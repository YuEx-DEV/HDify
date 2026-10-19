package com.example.hdify.ui.compressor

import android.content.Intent
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.hdify.R
import com.example.hdify.databinding.FragmentVideoCompressBinding
import com.example.hdify.util.animateCascadeEntrance
import com.example.hdify.util.safePopBackStack
import com.example.hdify.util.setOnLiquidClickListener
import com.example.hdify.video.compressor.CompressPreset
import com.example.hdify.video.compressor.VideoCompressorConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VideoCompressFragment : Fragment() {

    private var _binding: FragmentVideoCompressBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VideoCompressViewModel by activityViewModels()

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.setVideoUri(uri)
        }
    }

    private val pickContentLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.setVideoUri(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoCompressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        setupListeners()
        observeViewModel()
        animateCascade()
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.compressRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutHeader.setPadding(
                systemBars.left + 16,
                systemBars.top + 12,
                systemBars.right + 16,
                12
            )
            binding.scrollCompress.setPadding(0, 0, 0, systemBars.bottom + 48)
            insets
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().safePopBackStack()
        }

        binding.cardPickVideo.setOnLiquidClickListener {
            launchVideoPicker()
        }

        binding.btnPickVideo.setOnLiquidClickListener {
            launchVideoPicker()
        }

        binding.btnChangeVideo.setOnLiquidClickListener {
            launchVideoPicker()
        }

        binding.rgPresets.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_preset_original_high -> viewModel.selectPreset(CompressPreset.ORIGINAL_HIGH)
                R.id.rb_preset_original_balanced -> viewModel.selectPreset(CompressPreset.ORIGINAL_BALANCED)
                R.id.rb_preset_original_saver -> viewModel.selectPreset(CompressPreset.ORIGINAL_SAVER)
                R.id.rb_preset_custom -> viewModel.selectPreset(CompressPreset.CUSTOM)
            }
        }

        binding.sliderCustomBitrate.addOnChangeListener { _, value, _ ->
            binding.tvCustomBitrateLabel.text = getString(R.string.compressor_custom_bitrate_label, value)
            viewModel.setCustomBitrate(value)
        }

        binding.toggleCustomRes.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val dim = when (checkedId) {
                    R.id.btn_res_original -> 9999
                    R.id.btn_res_1080p -> 1080
                    R.id.btn_res_720p -> 720
                    else -> 9999
                }
                viewModel.setCustomMaxDimension(dim)
            }
        }

        binding.toggleCustomCodec.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mime = when (checkedId) {
                    R.id.btn_codec_h264 -> MediaFormat.MIMETYPE_VIDEO_AVC
                    R.id.btn_codec_hevc -> MediaFormat.MIMETYPE_VIDEO_HEVC
                    else -> MediaFormat.MIMETYPE_VIDEO_AVC
                }
                viewModel.setCustomMime(mime)
            }
        }

        binding.switchKeepFps.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setKeepOriginalFps(isChecked)
        }

        binding.switchForceHdr.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setForceHdr10Plus(isChecked)
        }

        binding.btnStartCompress.setOnLiquidClickListener {
            viewModel.startCompress()
        }

        binding.btnPauseCompress.setOnLiquidClickListener {
            viewModel.togglePause()
        }

        binding.btnCancelCompress.setOnLiquidClickListener {
            viewModel.cancelCompress()
        }

        binding.btnPlayResult.setOnLiquidClickListener {
            val state = viewModel.uiState.value
            if (state is CompressUiState.Success) {
                playVideo(state.outputUri)
            }
        }

        binding.btnShareResult.setOnLiquidClickListener {
            val state = viewModel.uiState.value
            if (state is CompressUiState.Success) {
                shareVideo(state.outputUri)
            }
        }

        binding.btnCompressAgain.setOnLiquidClickListener {
            viewModel.reset()
            launchVideoPicker()
        }

        // Default custom toggle states
        binding.toggleCustomRes.check(R.id.btn_res_1080p)
        binding.toggleCustomCodec.check(R.id.btn_codec_h264)
    }

    private fun launchVideoPicker() {
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(requireContext())) {
            pickVideoLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        } else {
            pickContentLauncher.launch("video/*")
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedUri.collect { uri ->
                        val hasVideo = uri != null
                        binding.cardPickVideo.visibility = if (hasVideo) View.GONE else View.VISIBLE
                        binding.cardSourceVideo.visibility = if (hasVideo) View.VISIBLE else View.GONE
                        binding.cardPresets.visibility = if (hasVideo) View.VISIBLE else View.GONE
                        binding.cardEstimation.visibility = if (hasVideo) View.VISIBLE else View.GONE
                        binding.btnStartCompress.visibility = if (hasVideo) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.metadata.collect { meta ->
                        if (meta != null) {
                            binding.tvMetaRes.text = getString(R.string.video_meta_resolution, meta.displayWidth, meta.displayHeight)
                            binding.tvMetaFps.text = getString(R.string.video_meta_fps, meta.fps)
                            binding.tvMetaDur.text = getString(R.string.video_meta_duration, meta.getFormattedDuration())
                            binding.tvMetaBitrate.text = getString(R.string.video_meta_bitrate, meta.getFormattedBitrate())
                        }
                    }
                }

                launch {
                    viewModel.originalFileSize.collect { size ->
                        binding.tvSourceSize.text = getString(R.string.compressor_source_size_format, VideoCompressorConfig.formatFileSize(size))
                        binding.tvEstOriginal.text = VideoCompressorConfig.formatFileSize(size)
                    }
                }

                launch {
                    viewModel.selectedPreset.collect { preset ->
                        when (preset) {
                            CompressPreset.ORIGINAL_HIGH -> binding.rbPresetOriginalHigh.isChecked = true
                            CompressPreset.ORIGINAL_BALANCED -> binding.rbPresetOriginalBalanced.isChecked = true
                            CompressPreset.ORIGINAL_SAVER -> binding.rbPresetOriginalSaver.isChecked = true
                            CompressPreset.CUSTOM -> binding.rbPresetCustom.isChecked = true
                        }
                        binding.layoutCustomOptions.visibility = if (preset == CompressPreset.CUSTOM) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.estimatedSizeBytes.collect { estBytes ->
                        binding.tvEstTarget.text = "~${VideoCompressorConfig.formatFileSize(estBytes)}"
                    }
                }

                launch {
                    viewModel.forceHdr10Plus.collect { isHdr ->
                        if (binding.switchForceHdr.isChecked != isHdr) {
                            binding.switchForceHdr.isChecked = isHdr
                        }
                    }
                }

                launch {
                    viewModel.currentConfig.collect { config ->
                        val savings = viewModel.estimatedSavingsPercent.value
                        val meta = viewModel.metadata.value
                        if (config != null && meta != null) {
                            val isResSame = (config.targetWidth == meta.displayWidth && config.targetHeight == meta.displayHeight)
                            val isFpsSame = (Math.abs(config.targetFps - meta.fps) < 0.5f)
                            val origTag = getString(R.string.compressor_tag_original)
                            val resTag = if (isResSame) "${config.targetWidth}×${config.targetHeight} $origTag" else "${config.targetWidth}×${config.targetHeight}"
                            val fpsTag = if (isFpsSame) "${meta.fps.toInt()} FPS $origTag" else "${config.targetFps.toInt()} FPS"
                            val bitrateMbps = String.format(java.util.Locale.US, "%.1f Mbps", config.bitrateBps / 1_000_000.0)
                            val hdrTag = if (config.forceHdr10Plus) getString(R.string.compressor_badge_hdr_tag) else ""

                            binding.tvEstBadge.text = if (savings > 0) {
                                getString(R.string.compressor_badge_savings_format, savings, resTag, fpsTag, hdrTag)
                            } else {
                                getString(R.string.compressor_badge_normal_format, resTag, fpsTag, bitrateMbps, hdrTag)
                            }
                        }
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        renderUiState(state)
                    }
                }
            }
        }
    }

    private fun renderUiState(state: CompressUiState) {
        when (state) {
            is CompressUiState.Idle -> {
                val hasVideo = viewModel.selectedUri.value != null
                binding.cardPickVideo.visibility = if (hasVideo) View.GONE else View.VISIBLE
                binding.cardSourceVideo.visibility = if (hasVideo) View.VISIBLE else View.GONE
                binding.cardPresets.visibility = if (hasVideo) View.VISIBLE else View.GONE
                binding.cardEstimation.visibility = if (hasVideo) View.VISIBLE else View.GONE
                binding.btnStartCompress.visibility = if (hasVideo) View.VISIBLE else View.GONE
                binding.cardProgress.visibility = View.GONE
                binding.cardSuccess.visibility = View.GONE
            }
            is CompressUiState.Compressing -> {
                if (binding.cardPickVideo.visibility != View.GONE) binding.cardPickVideo.visibility = View.GONE
                if (binding.cardSourceVideo.visibility != View.GONE) binding.cardSourceVideo.visibility = View.GONE
                if (binding.cardPresets.visibility != View.GONE) binding.cardPresets.visibility = View.GONE
                if (binding.cardEstimation.visibility != View.GONE) binding.cardEstimation.visibility = View.GONE
                if (binding.btnStartCompress.visibility != View.GONE) binding.btnStartCompress.visibility = View.GONE
                if (binding.cardProgress.visibility != View.VISIBLE) binding.cardProgress.visibility = View.VISIBLE
                if (binding.cardSuccess.visibility != View.GONE) binding.cardSuccess.visibility = View.GONE

                if (binding.progressBar.progress != state.percent) {
                    binding.progressBar.setProgressCompat(state.percent, false)
                }
                val pctStr = "${state.percent}%"
                if (binding.tvProgressPercent.text != pctStr) {
                    binding.tvProgressPercent.text = pctStr
                }
                val detailsStr = getString(
                    R.string.compressor_progress_format,
                    state.currentFrame,
                    state.totalFrames,
                    state.fps,
                    state.etaSec
                )
                if (binding.tvProgressDetails.text != detailsStr) {
                    binding.tvProgressDetails.text = detailsStr
                }
                val pauseText = if (state.isPaused) getString(R.string.btn_resume) else getString(R.string.btn_pause)
                if (binding.btnPauseCompress.text != pauseText) {
                    binding.btnPauseCompress.text = pauseText
                }
                val pauseIcon = if (state.isPaused) R.drawable.ic_play else R.drawable.ic_pause
                binding.btnPauseCompress.setIconResource(pauseIcon)
            }
            is CompressUiState.Success -> {
                binding.cardProgress.visibility = View.GONE
                binding.cardSuccess.visibility = View.VISIBLE

                binding.tvResultOrigSize.text = VideoCompressorConfig.formatFileSize(state.originalSize)
                binding.tvResultNewSize.text = VideoCompressorConfig.formatFileSize(state.compressedSize)
                binding.tvResultSavings.text = getString(R.string.compressor_stat_savings_format, state.reductionPercent)
            }
            is CompressUiState.Error -> {
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                renderUiState(CompressUiState.Idle)
            }
        }
    }

    private fun playVideo(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.no_gallery_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareVideo(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_video_title)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.compressor_share_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun animateCascade() {
        animateCascadeEntrance(
            listOf(
                binding.layoutHeader,
                binding.cardPickVideo
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
