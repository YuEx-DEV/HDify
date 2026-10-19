package com.example.hdify.ui.video

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.example.hdify.databinding.FragmentVideoUpscaleBinding
import com.example.hdify.util.animateCascadeEntrance
import com.example.hdify.util.safeNavigate
import com.example.hdify.util.safePopBackStack
import com.example.hdify.util.setOnLiquidClickListener
import com.example.hdify.util.setOnSingleClickListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VideoUpscaleFragment : Fragment() {

    private var _binding: FragmentVideoUpscaleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VideoUpscaleViewModel by activityViewModels()

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onVideoSelected(uri)
        }
    }

    private val pickContentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onVideoSelected(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoUpscaleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        setupListeners()
        observeViewModel()
        animateEntrance()

        val inputUriString = arguments?.getString("inputUri")
        if (!inputUriString.isNullOrBlank()) {
            val uri = android.net.Uri.parse(inputUriString)
            viewModel.onVideoSelected(uri)
            arguments?.remove("inputUri")
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.videoUpscaleRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutHeader.setPadding(
                systemBars.left + 16,
                systemBars.top + 12,
                systemBars.right + 16,
                12
            )
            val minBottom = (16 * resources.displayMetrics.density).toInt()
            binding.videoBottomBar.setPadding(
                binding.videoBottomBar.paddingLeft,
                binding.videoBottomBar.paddingTop,
                binding.videoBottomBar.paddingRight,
                systemBars.bottom.coerceAtLeast(minBottom)
            )
            insets
        }
    }

    private fun animateEntrance() {
        val views = listOf(
            binding.layoutHeader,
            binding.cardSelectVideo,
            binding.cardVideoScale,
            binding.cardEncoderSettings,
            binding.cardInterpolation
        )
        animateCascadeEntrance(views)
    }

    private fun setupListeners() {
        binding.btnBack.setOnSingleClickListener {
            findNavController().safePopBackStack()
        }

        binding.cardSelectVideo.setOnLiquidClickListener {
            launchVideoPicker()
        }

        binding.toggleVideoScale.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_video_scale_1x -> viewModel.setScale(1)
                    R.id.btn_video_scale_2x -> viewModel.setScale(2)
                    R.id.btn_video_scale_3x -> viewModel.setScale(3)
                    R.id.btn_video_scale_4x -> viewModel.setScale(4)
                }
            }
        }

        binding.switchInterpolation.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setInterpolationEnabled(isChecked)
            binding.layoutInterpolationOptions.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.toggleInterpFactor.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_interp_2x -> viewModel.setInterpolationFactor(2)
                    R.id.btn_interp_3x -> viewModel.setInterpolationFactor(3)
                    R.id.btn_interp_4x -> viewModel.setInterpolationFactor(4)
                }
            }
        }

        binding.switchRifeUhd.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setUhdMode(isChecked)
        }

        binding.toggleCodec.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_codec_hevc -> viewModel.setIsHevc(true)
                    R.id.btn_codec_h264 -> viewModel.setIsHevc(false)
                }
            }
        }

        binding.tvQualitySliderLabel.text = getString(R.string.quality_slider_label, binding.sliderVideoQuality.value.toInt())
        binding.sliderVideoQuality.addOnChangeListener { _, value, _ ->
            val qualityInt = value.toInt()
            binding.tvQualitySliderLabel.text = getString(R.string.quality_slider_label, qualityInt)
            viewModel.setQualityPercent(qualityInt)
        }

        binding.btnStartVideoUpscale.setOnLiquidClickListener {
            viewModel.startUpscale(isPreview3s = false)
        }

        binding.btnPreview3s.setOnLiquidClickListener {
            viewModel.startUpscale(isPreview3s = true)
        }

        binding.btnPauseVideo.setOnLiquidClickListener {
            viewModel.togglePause()
        }

        binding.btnCancelVideo.setOnLiquidClickListener {
            binding.tvVideoProgressStatus.text = getString(R.string.status_cancelling)
            binding.btnCancelVideo.isEnabled = false
            viewModel.cancelUpscale()
        }

        // Set default selections
        binding.toggleVideoScale.check(R.id.btn_video_scale_2x)
        binding.toggleInterpFactor.check(R.id.btn_interp_2x)
        if (viewModel.isHevc.value) {
            binding.toggleCodec.check(R.id.btn_codec_hevc)
        } else {
            binding.toggleCodec.check(R.id.btn_codec_h264)
        }
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
                    viewModel.metadata.collect { meta ->
                        if (meta != null) {
                            binding.cardVideoMetadata.visibility = View.VISIBLE
                            binding.cardVideoSummary.visibility = View.VISIBLE
                            binding.btnStartVideoUpscale.isEnabled = true
                            binding.btnPreview3s.isEnabled = true

                            binding.tvMetaResolution.text = getString(R.string.video_meta_resolution, meta.displayWidth, meta.displayHeight)
                            binding.tvMetaFps.text = getString(R.string.video_meta_fps, meta.fps)
                            binding.tvMetaDuration.text = getString(R.string.video_meta_duration, meta.getFormattedDuration())
                            binding.tvMetaCodec.text = getString(R.string.video_meta_codec, meta.mimeType)
                            binding.tvMetaBitrate.text = getString(R.string.video_meta_bitrate, meta.getFormattedBitrate())

                            updateSummary(meta, viewModel.scale.value, viewModel.interpolationFactor.value, viewModel.interpolationEnabled.value)
                            updateFpsTargetText(meta, viewModel.interpolationFactor.value)
                        } else {
                            binding.cardVideoMetadata.visibility = View.GONE
                            binding.cardVideoSummary.visibility = View.GONE
                            binding.btnStartVideoUpscale.isEnabled = false
                            binding.btnPreview3s.isEnabled = false
                        }
                    }
                }

                launch {
                    viewModel.scale.collect { scale ->
                        viewModel.metadata.value?.let { meta ->
                            updateSummary(meta, scale, viewModel.interpolationFactor.value, viewModel.interpolationEnabled.value)
                        }
                    }
                }

                launch {
                    viewModel.interpolationEnabled.collect { isEnabled ->
                        binding.switchInterpolation.isChecked = isEnabled
                        binding.layoutInterpolationOptions.visibility = if (isEnabled) View.VISIBLE else View.GONE
                        viewModel.metadata.value?.let { meta ->
                            updateSummary(meta, viewModel.scale.value, viewModel.interpolationFactor.value, isEnabled)
                            updateFpsTargetText(meta, viewModel.interpolationFactor.value)
                        }
                    }
                }

                launch {
                    viewModel.interpolationFactor.collect { factor ->
                        viewModel.metadata.value?.let { meta ->
                            updateSummary(meta, viewModel.scale.value, factor, viewModel.interpolationEnabled.value)
                            updateFpsTargetText(meta, factor)
                        }
                    }
                }

                launch {
                    viewModel.uhdMode.collect { isUhd ->
                        binding.switchRifeUhd.isChecked = isUhd
                    }
                }

                launch {
                    viewModel.fpsWarning.collect { warning ->
                        if (warning != null) {
                            binding.cardEncoderWarning.visibility = View.VISIBLE
                            binding.tvEncoderWarningMessage.text = warning
                        } else if (viewModel.encoderWarning.value == null) {
                            binding.cardEncoderWarning.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.encoderWarning.collect { warning ->
                        if (warning != null) {
                            binding.cardEncoderWarning.visibility = View.VISIBLE
                            binding.tvEncoderWarningMessage.text = warning
                            binding.btnStartVideoUpscale.isEnabled = false
                        } else {
                            if (viewModel.fpsWarning.value == null) {
                                binding.cardEncoderWarning.visibility = View.GONE
                            }
                            if (viewModel.metadata.value != null) {
                                binding.btnStartVideoUpscale.isEnabled = true
                            }
                        }
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        handleUiState(state)
                    }
                }
            }
        }
    }

    private fun updateFpsTargetText(meta: com.example.hdify.video.VideoMetadata, factor: Int) {
        val targetFps = meta.fps * factor
        binding.tvInterpTargetFps.text = getString(R.string.interp_target_fps, targetFps, meta.fps)
    }

    private fun updateSummary(
        meta: com.example.hdify.video.VideoMetadata,
        scale: Int,
        interpFactor: Int,
        interpEnabled: Boolean
    ) {
        val outW = meta.displayWidth * scale
        val outH = meta.displayHeight * scale
        val scaleDesc = if (scale > 1) " (x$scale)" else " (1x)"
        binding.tvSummaryOutputRes.text = getString(R.string.summary_output_res, outW, outH) + scaleDesc

        val targetFps = if (interpEnabled) (meta.fps * interpFactor).coerceAtMost(120f) else meta.fps
        binding.tvSummaryOutputFps.text = getString(R.string.summary_output_fps, targetFps)

        val durationSec = meta.durationMs / 1000
        val factorWeight = if (interpEnabled) interpFactor else 1
        val estTimeSec = (durationSec * scale * factorWeight * 1.2).toInt().coerceAtLeast(1)
        val estMin = estTimeSec / 60
        val estSec = estTimeSec % 60
        binding.tvSummaryEstTime.text = getString(R.string.summary_est_time, "%02d:%02d".format(estMin, estSec))

        val interpSizeMultiplier = if (interpEnabled) 1.4 else 1.0
        val estMb = ((meta.bitrate * scale * interpSizeMultiplier * durationSec) / (8 * 1_000_000L)).toLong().coerceAtLeast(5L)
        binding.tvSummaryEstSize.text = getString(R.string.summary_est_size, "$estMb MB")
    }

    private fun handleUiState(state: VideoUpscaleUiState) {
        when (state) {
            is VideoUpscaleUiState.Idle -> {
                binding.layoutVideoActionButtons.visibility = View.VISIBLE
                binding.layoutVideoProgress.visibility = View.GONE
                binding.btnCancelVideo.isEnabled = true
            }
            is VideoUpscaleUiState.Processing -> {
                if (binding.layoutVideoActionButtons.visibility != View.GONE) {
                    binding.layoutVideoActionButtons.visibility = View.GONE
                }
                if (binding.layoutVideoProgress.visibility != View.VISIBLE) {
                    binding.layoutVideoProgress.visibility = View.VISIBLE
                }
                binding.btnCancelVideo.isEnabled = true

                if (state.totalFrames > 0) {
                    if (binding.videoProgressIndicator.isIndeterminate) {
                        binding.videoProgressIndicator.isIndeterminate = false
                    }
                    if (binding.videoProgressIndicator.progress != state.percent) {
                        binding.videoProgressIndicator.setProgressCompat(state.percent, false)
                    }
                    val etaText = if (state.etaSec > 0) " • ETA ${state.etaSec}s (%.1f FPS)".format(state.fps) else ""
                    val progressText = getString(R.string.status_processing_frame, state.currentFrame, state.totalFrames, state.percent, etaText)
                    if (binding.tvVideoProgressStatus.text != progressText) {
                        binding.tvVideoProgressStatus.text = progressText
                    }
                } else {
                    if (!binding.videoProgressIndicator.isIndeterminate) {
                        binding.videoProgressIndicator.isIndeterminate = true
                    }
                    val prepText = getString(R.string.status_preparing_pipeline)
                    if (binding.tvVideoProgressStatus.text != prepText) {
                        binding.tvVideoProgressStatus.text = prepText
                    }
                }

                val pauseText = if (state.isPaused) getString(R.string.btn_resume) else getString(R.string.btn_pause)
                if (binding.btnPauseVideo.text != pauseText) {
                    binding.btnPauseVideo.text = pauseText
                }
            }
            is VideoUpscaleUiState.Success -> {
                binding.layoutVideoActionButtons.visibility = View.VISIBLE
                binding.layoutVideoProgress.visibility = View.GONE

                val bundle = Bundle().apply {
                    putString("outputUri", state.outputUri.toString())
                    putString("originalUri", state.originalUri.toString())
                    putInt("width", state.width)
                    putInt("height", state.height)
                    putLong("durationMs", state.durationMs)
                    putString("mediaType", "VIDEO")
                    putBoolean("isPreview", state.isPreview)
                    putString("tempFilePath", state.tempFilePath)
                    putBoolean("isSaved", state.isSaved)
                }
                viewModel.resetState()
                findNavController().safeNavigate(R.id.action_video_to_result, bundle)
            }
            is VideoUpscaleUiState.Error -> {
                binding.layoutVideoActionButtons.visibility = View.VISIBLE
                binding.layoutVideoProgress.visibility = View.GONE

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.dialog_video_upscale_failed_title))
                    .setMessage("${state.code}\n\n${state.message}")
                    .setPositiveButton("OK") { _, _ -> viewModel.resetState() }
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
