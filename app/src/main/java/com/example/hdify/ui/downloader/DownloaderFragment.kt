package com.example.hdify.ui.downloader

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.hdify.R
import com.example.hdify.databinding.DialogMediaPreviewBinding
import com.example.hdify.databinding.FragmentDownloaderBinding
import com.example.hdify.databinding.ItemDownloadOptionBinding
import com.example.hdify.domain.downloader.DownloadFormat
import com.example.hdify.domain.downloader.ExtractedMediaInfo
import com.example.hdify.domain.downloader.MediaDownloadOption
import com.example.hdify.domain.downloader.MediaPlatform
import com.example.hdify.util.animateCascadeEntrance
import com.example.hdify.util.safeNavigate
import com.example.hdify.util.safePopBackStack
import com.example.hdify.util.setOnLiquidClickListener
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

@AndroidEntryPoint
class DownloaderFragment : Fragment() {

    private var _binding: FragmentDownloaderBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DownloaderViewModel by activityViewModels()

    private val optionViews = mutableListOf<ItemDownloadOptionBinding>()
    private var currentMediaInfo: ExtractedMediaInfo? = null
    private var currentSelectedOption: MediaDownloadOption? = null
    private var currentPreviewDialog: Dialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        setupListeners()
        observeViewModel()
        animateEntrance()
    }

    override fun onResume() {
        super.onResume()
        checkClipboardForMediaUrl()
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.downloaderRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.downloaderRoot.setPadding(systemBars.left, 0, systemBars.right, 0)
            val basePadding = (110 * resources.displayMetrics.density).toInt()
            binding.scrollContainer.setPadding(
                binding.scrollContainer.paddingLeft,
                binding.scrollContainer.paddingTop,
                binding.scrollContainer.paddingRight,
                systemBars.bottom + basePadding
            )
            insets
        }
    }

    private fun animateEntrance() {
        val views = listOf(
            binding.layoutHeader,
            binding.chipGroupPlatforms,
            binding.cardInput,
            binding.cardFeaturesGuide
        )
        animateCascadeEntrance(views)
    }

    private fun setupListeners() {
        binding.btnBack.setOnLiquidClickListener {
            findNavController().safePopBackStack()
        }

        binding.etUrl.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            viewModel.onUrlInputChanged(query)
            if (query.isNotBlank()) {
                binding.cardClipboardSuggestion.visibility = View.GONE
            }
        }

        binding.btnPaste.setOnLiquidClickListener {
            pasteFromClipboard()
        }

        binding.btnClipboardUse.setOnLiquidClickListener {
            pasteFromClipboard()
            binding.cardClipboardSuggestion.visibility = View.GONE
        }

        binding.cardClipboardSuggestion.setOnLiquidClickListener {
            pasteFromClipboard()
            binding.cardClipboardSuggestion.visibility = View.GONE
        }

        binding.btnAnalyze.setOnLiquidClickListener {
            binding.cardClipboardSuggestion.visibility = View.GONE
            viewModel.analyze()
        }

        binding.btnDownload.setOnLiquidClickListener {
            viewModel.startDownload()
        }

        binding.btnPreviewMedia.setOnLiquidClickListener {
            openMediaPreview()
        }

        binding.containerThumbPreview.setOnLiquidClickListener {
            openMediaPreview()
        }

        binding.btnDownloadAgain.setOnLiquidClickListener {
            binding.etUrl.setText("")
            binding.cardMediaResult.visibility = View.GONE
            viewModel.resetState()
            checkClipboardForMediaUrl()
        }
    }

    private fun checkClipboardForMediaUrl() {
        val context = context ?: return
        val currentInput = binding.etUrl.text?.toString().orEmpty()
        if (currentInput.isNotBlank()) {
            binding.cardClipboardSuggestion.visibility = View.GONE
            return
        }

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val clipText = clipData.getItemAt(0).coerceToText(context).toString().trim()
                val platform = MediaPlatform.detectFromUrl(clipText)
                if (platform != MediaPlatform.UNKNOWN && (clipText.startsWith("http://") || clipText.startsWith("https://"))) {
                    binding.tvClipboardUrl.text = clipText
                    binding.cardClipboardSuggestion.visibility = View.VISIBLE
                    return
                }
            }
        } catch (_: Throwable) {}
        binding.cardClipboardSuggestion.visibility = View.GONE
    }

    private fun pasteFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val pasted = clipData.getItemAt(0).coerceToText(requireContext()).toString().trim()
            binding.etUrl.setText(pasted)
            binding.etUrl.setSelection(pasted.length)
            viewModel.onUrlInputChanged(pasted)
            if (pasted.startsWith("http://") || pasted.startsWith("https://")) {
                viewModel.analyze()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.detectedPlatform.collect { platform ->
                        updatePlatformBadges(platform)
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        handleUiState(state)
                    }
                }

                launch {
                    viewModel.selectedOption.collect { option ->
                        currentSelectedOption = option
                    }
                }
            }
        }
    }

    private fun updatePlatformBadges(platform: MediaPlatform) {
        val checkedId = when (platform) {
            MediaPlatform.TIKTOK -> R.id.chip_tiktok
            MediaPlatform.YOUTUBE -> R.id.chip_youtube
            MediaPlatform.YOUTUBE_MUSIC -> R.id.chip_yt_music
            MediaPlatform.SOUNDCLOUD -> R.id.chip_soundcloud
            MediaPlatform.INSTAGRAM -> R.id.chip_instagram
            MediaPlatform.TWITTER_X -> R.id.chip_twitter
            MediaPlatform.DOUYIN -> R.id.chip_douyin
            MediaPlatform.UNKNOWN -> View.NO_ID
        }
        if (checkedId != View.NO_ID) {
            binding.chipGroupPlatforms.check(checkedId)
        } else {
            binding.chipGroupPlatforms.clearCheck()
        }
    }

    private fun handleUiState(state: DownloaderUiState) {
        when (state) {
            is DownloaderUiState.Idle -> {
                binding.layoutAnalyzing.visibility = View.GONE
                binding.btnAnalyze.isEnabled = true
                binding.layoutActionsRow.visibility = View.VISIBLE
                binding.btnDownload.isEnabled = true
                binding.layoutDownloadProgress.visibility = View.GONE
                binding.layoutDownloadSuccess.visibility = View.GONE
            }
            is DownloaderUiState.Analyzing -> {
                binding.layoutAnalyzing.visibility = View.VISIBLE
                binding.cardMediaResult.visibility = View.GONE
                binding.btnAnalyze.isEnabled = false
            }
            is DownloaderUiState.MediaReady -> {
                binding.layoutAnalyzing.visibility = View.GONE
                binding.btnAnalyze.isEnabled = true
                binding.cardMediaResult.visibility = View.VISIBLE
                binding.layoutActionsRow.visibility = View.VISIBLE
                binding.btnDownload.isEnabled = true
                binding.layoutDownloadProgress.visibility = View.GONE
                binding.layoutDownloadSuccess.visibility = View.GONE
                currentMediaInfo = state.info
                populateMediaCard(state.info)
            }
            is DownloaderUiState.Downloading -> {
                if (binding.cardMediaResult.visibility != View.VISIBLE) binding.cardMediaResult.visibility = View.VISIBLE
                if (binding.layoutActionsRow.visibility != View.GONE) binding.layoutActionsRow.visibility = View.GONE
                if (binding.layoutDownloadProgress.visibility != View.VISIBLE) binding.layoutDownloadProgress.visibility = View.VISIBLE
                if (binding.layoutDownloadSuccess.visibility != View.GONE) binding.layoutDownloadSuccess.visibility = View.GONE

                if (state.percent >= 0) {
                    if (binding.progressDownload.isIndeterminate) {
                        binding.progressDownload.isIndeterminate = false
                    }
                    if (binding.progressDownload.progress != state.percent) {
                        binding.progressDownload.setProgressCompat(state.percent, false)
                    }
                    val pctStr = "${state.percent}%"
                    if (binding.tvDownloadPercent.text != pctStr) {
                        binding.tvDownloadPercent.text = pctStr
                    }
                } else {
                    if (!binding.progressDownload.isIndeterminate) {
                        binding.progressDownload.isIndeterminate = true
                    }
                    val dlText = getString(R.string.downloader_status_downloading)
                    if (binding.tvDownloadPercent.text != dlText) {
                        binding.tvDownloadPercent.text = dlText
                    }
                }

                val bytesText = if (state.totalBytes > 0) {
                    "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}"
                } else {
                    formatBytes(state.downloadedBytes)
                }
                if (binding.tvDownloadBytes.text != bytesText) {
                    binding.tvDownloadBytes.text = bytesText
                }
            }
            is DownloaderUiState.DownloadSuccess -> {
                binding.cardMediaResult.visibility = View.VISIBLE
                binding.layoutActionsRow.visibility = View.GONE
                binding.layoutDownloadProgress.visibility = View.GONE
                binding.layoutDownloadSuccess.visibility = View.VISIBLE

                val isVideo = state.format == DownloadFormat.VIDEO
                val isImage = state.format == DownloadFormat.IMAGE

                if (isVideo) {
                    binding.btnOpenUpscaler.visibility = View.VISIBLE
                    binding.btnOpenUpscaler.text = getString(R.string.downloader_btn_open_video_upscaler)
                    binding.btnOpenUpscaler.setIconResource(R.drawable.ic_auto_awesome)
                    binding.btnOpenUpscaler.setOnLiquidClickListener {
                        findNavController().safeNavigate(
                            R.id.action_downloader_to_video,
                            bundleOf("inputUri" to state.fileUri.toString())
                        )
                    }
                } else if (isImage) {
                    binding.btnOpenUpscaler.visibility = View.VISIBLE
                    binding.btnOpenUpscaler.text = getString(R.string.downloader_btn_open_image_upscaler)
                    binding.btnOpenUpscaler.setIconResource(R.drawable.ic_auto_awesome)
                    binding.btnOpenUpscaler.setOnLiquidClickListener {
                        findNavController().safeNavigate(
                            R.id.action_downloader_to_image,
                            bundleOf("inputUri" to state.fileUri.toString())
                        )
                    }
                } else {
                    binding.btnOpenUpscaler.visibility = View.GONE
                }
            }
            is DownloaderUiState.Error -> {
                binding.layoutAnalyzing.visibility = View.GONE
                binding.btnAnalyze.isEnabled = true
                binding.layoutActionsRow.visibility = View.VISIBLE
                binding.btnDownload.isEnabled = true
                binding.layoutDownloadProgress.visibility = View.GONE

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.dialog_notice_title))
                    .setMessage(state.message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun populateMediaCard(info: ExtractedMediaInfo) {
        binding.tvMediaTitle.text = info.title
        binding.tvMediaAuthor.text = if (info.author.startsWith("@")) info.author else "@${info.author}"
        binding.chipPlatformBadge.text = info.platform.displayName

        val colorPrimary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        binding.chipPlatformBadge.setTextColor(colorPrimary)

        // Load main thumbnail
        if (!info.thumbnailUrl.isNullOrBlank()) {
            binding.ivMediaThumb.visibility = View.VISIBLE
            val hasVideo = info.options.any { it.format == DownloadFormat.VIDEO }
            binding.ivPlayBadge.visibility = if (hasVideo) View.VISIBLE else View.GONE
            loadThumbnail(info.thumbnailUrl)
        } else {
            binding.ivMediaThumb.setImageResource(info.platform.iconRes)
            binding.ivPlayBadge.visibility = View.GONE
        }

        // Setup Slide Carousel Chips if multi-slide or multi-image
        setupSlideChips(info)

        // Populate options container
        binding.layoutOptionsContainer.removeAllViews()
        optionViews.clear()

        for ((index, option) in info.options.withIndex()) {
            val optionBinding = ItemDownloadOptionBinding.inflate(layoutInflater, binding.layoutOptionsContainer, false)

            val label = option.label
            val (title, subtitle) = when {
                label.contains("(") && label.contains(")") -> {
                    val t = label.substringBefore("(").trim()
                    val sub = label.substringAfter("(").substringBefore(")").trim()
                    Pair(t, sub)
                }
                option.format == DownloadFormat.AUDIO -> Pair(getString(R.string.downloader_option_audio_title), getString(R.string.downloader_option_audio_desc))
                option.format == DownloadFormat.IMAGE -> Pair(label, getString(R.string.downloader_option_image_desc))
                else -> Pair(label, getString(R.string.downloader_option_standard_desc))
            }

            optionBinding.tvOptionTitle.text = title
            optionBinding.tvOptionSubtitle.text = subtitle

            val badge = when {
                label.contains("HD", ignoreCase = true) -> "HD"
                label.contains("Standard", ignoreCase = true) -> "SD"
                option.format == DownloadFormat.AUDIO -> "MP3"
                option.format == DownloadFormat.IMAGE -> "FOTO"
                else -> option.quality ?: "ASLI"
            }
            optionBinding.tvOptionBadge.text = badge

            val iconRes = when (option.format) {
                DownloadFormat.VIDEO -> R.drawable.ic_video
                DownloadFormat.AUDIO -> R.drawable.ic_music
                DownloadFormat.IMAGE -> R.drawable.ic_image
            }
            optionBinding.ivOptionIcon.setImageResource(iconRes)

            optionBinding.cardOption.setOnLiquidClickListener {
                selectOptionAt(index, info.options)
            }

            binding.layoutOptionsContainer.addView(optionBinding.root)
            optionViews.add(optionBinding)
        }

        if (info.options.isNotEmpty()) {
            selectOptionAt(0, info.options)
        }
    }

    private fun setupSlideChips(info: ExtractedMediaInfo) {
        val hasSlideLabels = info.options.any { it.label.contains("Slide", ignoreCase = true) }
        val slideOptions = if (hasSlideLabels) {
            info.options.filter { it.label.contains("Slide", ignoreCase = true) }
        } else {
            info.options.filter { it.format == DownloadFormat.IMAGE || it.label.contains("Live Photo", ignoreCase = true) }
        }

        if (slideOptions.size > 1) {
            binding.layoutSlidesSection.visibility = View.VISIBLE
            binding.chipGroupSlides.removeAllViews()

            for (option in slideOptions) {
                val targetIndex = info.options.indexOf(option)
                val chip = Chip(requireContext()).apply {
                    val chipText = when {
                        option.label.contains("Slide", ignoreCase = true) -> {
                            val num = option.label.substringAfter("Slide").trim().takeWhile { it.isDigit() }
                            if (num.isNotEmpty()) "Slide $num" else "Slide"
                        }
                        option.label.contains("Live Photo", ignoreCase = true) -> "Live Photo"
                        else -> option.label.take(16)
                    }
                    text = chipText
                    isCheckable = true
                    tag = targetIndex
                    val colorOutline = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutlineVariant)
                    chipStrokeColor = ColorStateList.valueOf(colorOutline)
                    shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(14f * resources.displayMetrics.density).build()
                    chipStrokeWidth = 1f * resources.displayMetrics.density
                    setOnClickListener {
                        if (targetIndex >= 0) {
                            selectOptionAt(targetIndex, info.options)
                        }
                    }
                }
                binding.chipGroupSlides.addView(chip)
            }

            if (binding.chipGroupSlides.childCount > 0) {
                (binding.chipGroupSlides.getChildAt(0) as? Chip)?.isChecked = true
            }
        } else {
            binding.layoutSlidesSection.visibility = View.GONE
        }
    }

    private fun selectOptionAt(selectedIndex: Int, options: List<MediaDownloadOption>) {
        val colorPrimary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        val colorPrimaryContainer = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimaryContainer)
        val colorOutline = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutlineVariant)
        val colorSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)

        for ((index, optionBinding) in optionViews.withIndex()) {
            val isSelected = index == selectedIndex
            if (isSelected) {
                optionBinding.cardOption.strokeColor = colorPrimary
                optionBinding.cardOption.strokeWidth = dpToPx(2)
                optionBinding.cardOption.setCardBackgroundColor(colorPrimaryContainer)
                optionBinding.ivOptionCheck.visibility = View.VISIBLE
                optionBinding.ivOptionCheck.setImageResource(R.drawable.ic_check_circle)
                optionBinding.ivOptionCheck.imageTintList = ColorStateList.valueOf(colorPrimary)
            } else {
                optionBinding.cardOption.strokeColor = colorOutline
                optionBinding.cardOption.strokeWidth = dpToPx(1)
                optionBinding.cardOption.setCardBackgroundColor(colorSurface)
                optionBinding.ivOptionCheck.visibility = View.INVISIBLE
            }
        }

        // Sync chip in chip_group_slides if present
        val chipCount = binding.chipGroupSlides.childCount
        for (i in 0 until chipCount) {
            val chip = binding.chipGroupSlides.getChildAt(i) as? Chip ?: continue
            val chipTag = chip.tag as? Int
            chip.isChecked = (chipTag == selectedIndex)
        }

        if (selectedIndex in options.indices) {
            val option = options[selectedIndex]
            currentSelectedOption = option
            viewModel.selectOption(option)

            // Dynamically update thumbnail preview if option has its own thumbnail or is an image
            val thumbTarget = option.thumbnailUrl ?: if (option.format == DownloadFormat.IMAGE) option.url else null
            if (!thumbTarget.isNullOrBlank()) {
                loadThumbnail(thumbTarget)
            }
            binding.ivPlayBadge.visibility = if (option.format == DownloadFormat.VIDEO) View.VISIBLE else View.GONE
        }
    }

    private fun openMediaPreview() {
        val option = currentSelectedOption ?: return
        val info = currentMediaInfo ?: return
        showMediaPreviewDialog(option, info.title)
    }

    private fun showMediaPreviewDialog(option: MediaDownloadOption, title: String) {
        val context = requireContext()
        val dialogBinding = DialogMediaPreviewBinding.inflate(layoutInflater)

        dialogBinding.tvPreviewTitle.text = title
        dialogBinding.tvPreviewSubtitle.text = "${option.label} • ${option.format.name}"

        val dialog = BottomSheetDialog(context)
        dialog.setContentView(dialogBinding.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.window?.apply {
            setDimAmount(0.80f)
        }
        dialog.setCanceledOnTouchOutside(true)

        currentPreviewDialog = dialog

        dialogBinding.btnClosePreview.setOnLiquidClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnPreviewDownload.setOnLiquidClickListener {
            val options = currentMediaInfo?.options ?: emptyList()
            val targetIndex = options.indexOf(option)
            if (targetIndex >= 0) {
                selectOptionAt(targetIndex, options)
            }
            dialog.dismiss()
            viewModel.startDownload()
        }

        // Always load thumbnail first as background poster so there is never a black screen
        val posterUrl = option.thumbnailUrl ?: currentMediaInfo?.thumbnailUrl
        if (!posterUrl.isNullOrBlank()) {
            loadPreviewImage(posterUrl, dialogBinding.ivPreviewImage)
        }

        if (option.format == DownloadFormat.VIDEO) {
            dialogBinding.vvPreviewVideo.visibility = View.VISIBLE
            dialogBinding.progressPreviewLoading.visibility = View.VISIBLE
            dialogBinding.tvPreviewHint.visibility = View.VISIBLE
            dialogBinding.tvPreviewHint.text = getString(R.string.downloader_hint_video_tap)
            dialogBinding.layoutVideoControls.visibility = View.GONE
            dialogBinding.ivPreviewPlay.visibility = View.GONE

            var isTickerRunning = true

            fun toggleVideoPlayback() {
                try {
                    if (dialogBinding.vvPreviewVideo.isPlaying) {
                        dialogBinding.vvPreviewVideo.pause()
                        dialogBinding.ivPreviewPlay.visibility = View.VISIBLE
                        dialogBinding.ivControlPlayPause.setImageResource(R.drawable.ic_play)
                    } else {
                        dialogBinding.vvPreviewVideo.start()
                        dialogBinding.ivPreviewPlay.visibility = View.GONE
                        dialogBinding.ivControlPlayPause.setImageResource(R.drawable.ic_pause)
                    }
                } catch (_: Throwable) {}
            }

            dialogBinding.layoutPreviewContainer.setOnClickListener {
                toggleVideoPlayback()
            }

            dialogBinding.ivControlPlayPause.setOnClickListener {
                toggleVideoPlayback()
            }

            val videoUri = Uri.parse(option.url)
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Referer" to when (currentMediaInfo?.platform) {
                    MediaPlatform.TIKTOK -> "https://www.tiktok.com/"
                    MediaPlatform.TWITTER_X -> "https://twitter.com/"
                    MediaPlatform.INSTAGRAM -> "https://www.instagram.com/"
                    else -> "https://google.com/"
                },
                "Accept" to "*/*"
            )
            dialogBinding.vvPreviewVideo.setVideoURI(videoUri, headers)

            dialogBinding.vvPreviewVideo.setOnInfoListener { _, what, _ ->
                when (what) {
                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                        dialogBinding.progressPreviewLoading.visibility = View.VISIBLE
                    }
                    MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                        dialogBinding.progressPreviewLoading.visibility = View.GONE
                    }
                }
                false
            }

            dialogBinding.vvPreviewVideo.setOnPreparedListener { mp: MediaPlayer ->
                dialogBinding.ivPreviewImage.visibility = View.GONE
                dialogBinding.progressPreviewLoading.visibility = View.GONE
                dialogBinding.layoutVideoControls.visibility = View.VISIBLE
                dialogBinding.ivControlPlayPause.setImageResource(R.drawable.ic_pause)
                dialogBinding.ivPreviewPlay.visibility = View.GONE
                mp.isLooping = true
                dialogBinding.vvPreviewVideo.start()

                val duration = mp.duration
                if (duration > 0) {
                    dialogBinding.progressVideoTimeline.max = duration
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    while (isTickerRunning && dialog.isShowing) {
                        try {
                            if (dialogBinding.vvPreviewVideo.isPlaying) {
                                val current = dialogBinding.vvPreviewVideo.currentPosition
                                val total = dialogBinding.vvPreviewVideo.duration
                                dialogBinding.progressVideoTimeline.progress = current
                                dialogBinding.tvVideoTime.text = "${formatDuration(current)} / ${formatDuration(total)}"
                            }
                        } catch (_: Throwable) {}
                        kotlinx.coroutines.delay(400)
                    }
                }
            }

            dialogBinding.vvPreviewVideo.setOnErrorListener { _, _, _ ->
                dialogBinding.progressPreviewLoading.visibility = View.GONE
                dialogBinding.layoutVideoControls.visibility = View.GONE
                dialogBinding.ivPreviewPlay.visibility = View.GONE
                dialogBinding.tvPreviewHint.text = getString(R.string.downloader_video_unsupported_preview)
                true
            }

            dialog.setOnDismissListener {
                isTickerRunning = false
                try {
                    if (dialogBinding.vvPreviewVideo.isPlaying) {
                        dialogBinding.vvPreviewVideo.stopPlayback()
                    }
                } catch (_: Throwable) {}
                currentPreviewDialog = null
            }
        } else if (option.format == DownloadFormat.AUDIO) {
            dialogBinding.vvPreviewVideo.visibility = View.GONE
            dialogBinding.progressPreviewLoading.visibility = View.VISIBLE
            dialogBinding.tvPreviewHint.visibility = View.VISIBLE
            dialogBinding.tvPreviewHint.text = getString(R.string.downloader_hint_audio_tap)
            dialogBinding.layoutVideoControls.visibility = View.GONE
            dialogBinding.ivPreviewPlay.visibility = View.GONE

            // Fallback icon if no artwork available
            if (posterUrl.isNullOrBlank()) {
                dialogBinding.ivPreviewImage.setImageResource(R.drawable.ic_music)
                dialogBinding.ivPreviewImage.imageTintList = ColorStateList.valueOf(0x88FFFFFF.toInt())
            }

            var audioPlayer: MediaPlayer? = MediaPlayer()
            var isAudioTickerRunning = true

            fun toggleAudioPlayback() {
                try {
                    audioPlayer?.let { player ->
                        if (player.isPlaying) {
                            player.pause()
                            dialogBinding.ivControlPlayPause.setImageResource(R.drawable.ic_play)
                        } else {
                            player.start()
                            dialogBinding.ivControlPlayPause.setImageResource(R.drawable.ic_pause)
                        }
                    }
                } catch (_: Throwable) {}
            }

            dialogBinding.layoutPreviewContainer.setOnClickListener {
                toggleAudioPlayback()
            }

            dialogBinding.ivControlPlayPause.setOnClickListener {
                toggleAudioPlayback()
            }

            try {
                val audioHeaders = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Accept" to "*/*"
                )
                audioPlayer?.setDataSource(context, Uri.parse(option.url), audioHeaders)
                audioPlayer?.setOnPreparedListener { mp ->
                    dialogBinding.progressPreviewLoading.visibility = View.GONE
                    dialogBinding.layoutVideoControls.visibility = View.VISIBLE
                    dialogBinding.ivControlPlayPause.setImageResource(R.drawable.ic_pause)
                    dialogBinding.ivPreviewPlay.visibility = View.GONE
                    mp.isLooping = true
                    mp.start()

                    val duration = mp.duration
                    if (duration > 0) {
                        dialogBinding.progressVideoTimeline.max = duration
                    }

                    viewLifecycleOwner.lifecycleScope.launch {
                        while (isAudioTickerRunning && dialog.isShowing) {
                            try {
                                if (audioPlayer?.isPlaying == true) {
                                    val current = audioPlayer?.currentPosition ?: 0
                                    val total = audioPlayer?.duration ?: 0
                                    dialogBinding.progressVideoTimeline.progress = current
                                    dialogBinding.tvVideoTime.text = "${formatDuration(current)} / ${formatDuration(total)}"
                                }
                            } catch (_: Throwable) {}
                            kotlinx.coroutines.delay(400)
                        }
                    }
                }

                audioPlayer?.setOnErrorListener { _, _, _ ->
                    dialogBinding.progressPreviewLoading.visibility = View.GONE
                    dialogBinding.layoutVideoControls.visibility = View.GONE
                    dialogBinding.ivPreviewPlay.visibility = View.GONE
                    dialogBinding.tvPreviewHint.text = getString(R.string.downloader_audio_unsupported_preview)
                    true
                }

                audioPlayer?.prepareAsync()
            } catch (t: Throwable) {
                dialogBinding.progressPreviewLoading.visibility = View.GONE
                dialogBinding.tvPreviewHint.text = getString(R.string.downloader_audio_play_failed, t.message ?: "")
            }

            dialog.setOnDismissListener {
                isAudioTickerRunning = false
                try {
                    audioPlayer?.stop()
                    audioPlayer?.release()
                    audioPlayer = null
                } catch (_: Throwable) {}
                currentPreviewDialog = null
            }
        } else {
            // Image / Photo slide preview
            dialogBinding.vvPreviewVideo.visibility = View.GONE
            dialogBinding.layoutVideoControls.visibility = View.GONE
            dialogBinding.ivPreviewPlay.visibility = View.GONE
            dialogBinding.tvPreviewHint.visibility = View.GONE
            dialogBinding.progressPreviewLoading.visibility = View.VISIBLE

            val imageUrl = option.url
            loadPreviewImage(imageUrl, dialogBinding.ivPreviewImage) {
                dialogBinding.progressPreviewLoading.visibility = View.GONE
            }

            dialog.setOnDismissListener {
                currentPreviewDialog = null
            }
        }

        dialog.show()
    }

    private fun loadPreviewImage(url: String, imageView: android.widget.ImageView, onComplete: (() -> Unit)? = null) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stream = URL(url).openStream()
                val bitmap = BitmapFactory.decodeStream(stream)
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }
                    onComplete?.invoke()
                }
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }
        }
    }

    private fun formatDuration(millis: Int): String {
        if (millis <= 0) return "00:00"
        val totalSec = millis / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun loadThumbnail(url: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = URL(url).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                withContext(Dispatchers.Main) {
                    if (_binding != null && bitmap != null) {
                        binding.ivMediaThumb.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentPreviewDialog?.dismiss()
        currentPreviewDialog = null
        _binding = null
    }
}
