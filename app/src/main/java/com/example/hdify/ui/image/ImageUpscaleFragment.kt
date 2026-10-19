package com.example.hdify.ui.image

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.example.hdify.databinding.FragmentImageUpscaleBinding
import com.example.hdify.domain.model.ContentType
import com.example.hdify.domain.model.EngineType
import com.example.hdify.domain.model.Preset
import com.example.hdify.util.animateCascadeEntrance
import com.example.hdify.util.safeNavigate
import com.example.hdify.util.safePopBackStack
import com.example.hdify.util.setOnLiquidClickListener
import com.example.hdify.util.setOnSingleClickListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ImageUpscaleFragment : Fragment() {

    private var _binding: FragmentImageUpscaleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ImageUpscaleViewModel by activityViewModels()

    private val pickMultipleMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onImagesSelected(uris)
        }
    }

    private val pickMultipleContentLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onImagesSelected(uris)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageUpscaleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        setupListeners()
        observeViewModel()
        animateEntrance()

        val inputUriString = arguments?.getString("inputUri") ?: arguments?.getStringArrayList("sourceUris")?.firstOrNull()
        if (!inputUriString.isNullOrBlank()) {
            val uri = android.net.Uri.parse(inputUriString)
            viewModel.onImagesSelected(listOf(uri))
            arguments?.remove("inputUri")
            arguments?.remove("sourceUris")
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.imageUpscaleRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutHeader.setPadding(
                systemBars.left + 16,
                systemBars.top + 12,
                systemBars.right + 16,
                12
            )
            val minBottom = (16 * resources.displayMetrics.density).toInt()
            binding.imageBottomBar.setPadding(
                binding.imageBottomBar.paddingLeft,
                binding.imageBottomBar.paddingTop,
                binding.imageBottomBar.paddingRight,
                systemBars.bottom.coerceAtLeast(minBottom)
            )
            insets
        }
    }

    private fun animateEntrance() {
        val views = listOf(
            binding.layoutHeader,
            binding.cardSelectImage,
            binding.cardScale,
            binding.cardModelOptions
        )
        animateCascadeEntrance(views)
    }

    private fun setupListeners() {
        binding.btnBack.setOnSingleClickListener {
            findNavController().safePopBackStack()
        }

        binding.cardSelectImage.setOnLiquidClickListener {
            launchImagePicker()
        }

        // Content Type Toggle (Anime vs Foto)
        binding.toggleContentType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_content_anime -> viewModel.setContentType(ContentType.ANIME)
                    R.id.btn_content_photo -> viewModel.setContentType(ContentType.PHOTO)
                }
            }
        }

        // Preset ChipGroup
        binding.chipGroupPreset.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chip_preset_fast -> viewModel.setPreset(Preset.FAST)
                R.id.chip_preset_balanced -> viewModel.setPreset(Preset.BALANCED)
                R.id.chip_preset_quality -> viewModel.setPreset(Preset.QUALITY)
            }
        }

        // Advanced Settings Button
        binding.btnOpenAdvanced.setOnLiquidClickListener {
            val currentData = AdvancedSettingsData(
                engineType = viewModel.engineType.value,
                modelDir = viewModel.modelDir.value,
                tileSize = viewModel.tileSize.value,
                useFp16 = viewModel.useFp16.value,
                useTta = viewModel.useTta.value
            )
            val sheet = AdvancedSettingsBottomSheet.newInstance(currentData) { updated ->
                viewModel.applyAdvancedSettings(updated)
            }
            sheet.show(parentFragmentManager, AdvancedSettingsBottomSheet.TAG)
        }

        // Scale Toggle
        binding.toggleScale.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_scale_2x -> viewModel.setScale(2)
                    R.id.btn_scale_3x -> viewModel.setScale(3)
                    R.id.btn_scale_4x -> viewModel.setScale(4)
                }
            }
        }

        // Custom Target Resolution Switch
        binding.switchCustomSize.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutCustomSize.visibility = if (isChecked) View.VISIBLE else View.GONE
            binding.tvCustomSizeHint.visibility = if (isChecked) View.VISIBLE else View.GONE
            viewModel.setCustomSizeEnabled(isChecked)
            updateCustomSizeInputs()
        }

        val customSizeWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                updateCustomSizeInputs()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etCustomWidth.addTextChangedListener(customSizeWatcher)
        binding.etCustomHeight.addTextChangedListener(customSizeWatcher)

        // Real-CUGAN Model Variant Toggle
        binding.toggleModelVariant.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_model_se -> viewModel.setModelDir("models-se")
                    R.id.btn_model_pro -> viewModel.setModelDir("models-pro")
                }
            }
        }

        // Real-CUGAN Denoise ChipGroup
        binding.chipGroupDenoise.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val denoiseVal = when (id) {
                R.id.chip_denoise_conservative -> -1
                R.id.chip_denoise_none -> 0
                R.id.chip_denoise_1x -> 1
                R.id.chip_denoise_2x -> 2
                R.id.chip_denoise_3x -> 3
                else -> 0
            }
            viewModel.setDenoise(denoiseVal)
        }

        binding.btnStartUpscale.setOnLiquidClickListener {
            viewModel.startUpscale(isCropPreview = false)
        }

        binding.btnPreviewCrop.setOnLiquidClickListener {
            viewModel.startUpscale(isCropPreview = true)
        }

        binding.btnCancelProcessing.setOnLiquidClickListener {
            binding.tvProgressStatus.text = getString(R.string.status_cancelling)
            binding.btnCancelProcessing.isEnabled = false
            viewModel.cancelUpscale()
        }

        // Initial default selections
        binding.toggleContentType.check(R.id.btn_content_anime)
        binding.toggleScale.check(R.id.btn_scale_2x)
        binding.toggleModelVariant.check(R.id.btn_model_se)
        binding.chipGroupDenoise.check(R.id.chip_denoise_none)
    }

    private fun updateCustomSizeInputs() {
        val w = binding.etCustomWidth.text?.toString()?.toIntOrNull() ?: 0
        val h = binding.etCustomHeight.text?.toString()?.toIntOrNull() ?: 0
        viewModel.setCustomDimensions(w, h)
    }

    private fun launchImagePicker() {
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(requireContext())) {
            pickMultipleMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            pickMultipleContentLauncher.launch("image/*")
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedUris.collect { uris ->
                        val count = uris.size
                        if (count > 0) {
                            binding.layoutEmptyPicker.visibility = View.GONE
                            binding.ivThumbnail.visibility = View.VISIBLE
                            binding.ivThumbnail.setImageURI(uris.first())
                            binding.btnStartUpscale.isEnabled = true

                            if (count > 1) {
                                binding.btnPreviewCrop.visibility = View.GONE
                                binding.btnStartUpscale.text = getString(R.string.btn_start_batch_upscale, count)
                            } else {
                                binding.btnPreviewCrop.visibility = View.VISIBLE
                                binding.btnPreviewCrop.isEnabled = true
                                binding.btnStartUpscale.text = getString(R.string.btn_start_upscale)
                            }
                        } else {
                            binding.layoutEmptyPicker.visibility = View.VISIBLE
                            binding.ivThumbnail.visibility = View.GONE
                            binding.tvDimensionsBanner.visibility = View.GONE
                            binding.btnStartUpscale.isEnabled = false
                            binding.btnPreviewCrop.isEnabled = false
                            binding.btnPreviewCrop.visibility = View.VISIBLE
                            binding.btnStartUpscale.text = getString(R.string.btn_start_upscale)
                        }
                    }
                }

                launch {
                    viewModel.sourceDimensions.collect { dims ->
                        updateDimensionsBanner(dims, viewModel.scale.value, viewModel.selectedUris.value.size)
                    }
                }

                launch {
                    viewModel.modelDir.collect { dir ->
                        updateActiveEngineInfo()
                        updateModelAndDenoiseChipAvailability(dir, viewModel.scale.value)
                    }
                }

                launch {
                    viewModel.scale.collect { scale ->
                        updateDimensionsBanner(viewModel.sourceDimensions.value, scale, viewModel.selectedUris.value.size)
                        updateModelAndDenoiseChipAvailability(viewModel.modelDir.value, scale)
                    }
                }

                launch {
                    viewModel.denoise.collect { d ->
                        val targetChipId = when (d) {
                            -1 -> R.id.chip_denoise_conservative
                            0 -> R.id.chip_denoise_none
                            1 -> R.id.chip_denoise_1x
                            2 -> R.id.chip_denoise_2x
                            3 -> R.id.chip_denoise_3x
                            else -> R.id.chip_denoise_none
                        }
                        if (binding.chipGroupDenoise.checkedChipId != targetChipId) {
                            binding.chipGroupDenoise.check(targetChipId)
                        }
                    }
                }

                launch {
                    viewModel.engineType.collect { engine ->
                        binding.layoutCuganOptions.visibility = if (engine == EngineType.REAL_CUGAN) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                        updateActiveEngineInfo()
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

    private fun updateActiveEngineInfo() {
        val eng = if (viewModel.engineType.value == EngineType.REAL_CUGAN) "Real-CUGAN" else "Real-ESRGAN"
        val mdl = viewModel.modelDir.value
        binding.tvActiveEngineInfo.text = "Engine: $eng • $mdl"
    }

    private fun updateModelAndDenoiseChipAvailability(modelDir: String, scale: Int) {
        val isPro = modelDir == "models-pro"
        val isScale4 = scale == 4
        val isScale3Or4 = scale >= 3

        // Pro is not supported on 4x
        binding.btnModelPro.isEnabled = !isScale4
        binding.btnModelPro.alpha = if (!isScale4) 1.0f else 0.4f

        if (isPro) {
            if (binding.toggleModelVariant.checkedButtonId != R.id.btn_model_pro) {
                binding.toggleModelVariant.check(R.id.btn_model_pro)
            }
        } else {
            if (binding.toggleModelVariant.checkedButtonId != R.id.btn_model_se) {
                binding.toggleModelVariant.check(R.id.btn_model_se)
            }
        }

        // 1x and 2x denoise are only available on SE at 2x
        val allow1x2x = !isPro && !isScale3Or4
        binding.chipDenoise1x.isEnabled = allow1x2x
        binding.chipDenoise1x.alpha = if (allow1x2x) 1.0f else 0.4f
        binding.chipDenoise2x.isEnabled = allow1x2x
        binding.chipDenoise2x.alpha = if (allow1x2x) 1.0f else 0.4f
    }

    private fun updateDimensionsBanner(dims: Pair<Int, Int>?, scale: Int, uriCount: Int) {
        if (uriCount > 1) {
            binding.tvDimensionsBanner.visibility = View.VISIBLE
            binding.tvDimensionsBanner.text = getString(R.string.batch_selected_count, uriCount)
            binding.cardWarning.visibility = View.GONE
            return
        }

        if (dims != null) {
            val srcW = dims.first
            val srcH = dims.second
            val dstW = srcW * scale
            val dstH = srcH * scale
            binding.tvDimensionsBanner.visibility = View.VISIBLE
            binding.tvDimensionsBanner.text = "$srcW × $srcH px  →  $dstW × $dstH px (x$scale)"

            // Warning for very large images (FR-IMG-08)
            val megaPixels = (dstW.toLong() * dstH.toLong()) / 1_000_000
            if (megaPixels > 60) {
                binding.cardWarning.visibility = View.VISIBLE
                binding.tvWarningMessage.text = getString(R.string.warning_large_resolution, dstW, dstH, megaPixels)
            } else {
                binding.cardWarning.visibility = View.GONE
            }
        } else {
            binding.tvDimensionsBanner.visibility = View.GONE
            binding.cardWarning.visibility = View.GONE
        }
    }

    private fun handleUiState(state: ImageUpscaleUiState) {
        when (state) {
            is ImageUpscaleUiState.Idle -> {
                binding.layoutActionButtons.visibility = View.VISIBLE
                binding.layoutProgress.visibility = View.GONE
                binding.btnCancelProcessing.isEnabled = true
            }
            is ImageUpscaleUiState.Processing -> {
                if (binding.layoutActionButtons.visibility != View.GONE) {
                    binding.layoutActionButtons.visibility = View.GONE
                }
                if (binding.layoutProgress.visibility != View.VISIBLE) {
                    binding.layoutProgress.visibility = View.VISIBLE
                }
                binding.btnCancelProcessing.isEnabled = true

                if (state.total > 0) {
                    if (binding.progressIndicator.isIndeterminate) {
                        binding.progressIndicator.isIndeterminate = false
                    }
                    if (binding.progressIndicator.progress != state.percent) {
                        binding.progressIndicator.setProgressCompat(state.percent, false)
                    }
                    val progressText = getString(R.string.status_processing_tile, state.current, state.total, state.percent)
                    if (binding.tvProgressStatus.text != progressText) {
                        binding.tvProgressStatus.text = progressText
                    }
                } else {
                    if (!binding.progressIndicator.isIndeterminate) {
                        binding.progressIndicator.isIndeterminate = true
                    }
                    val prepText = getString(R.string.status_preparing_engine)
                    if (binding.tvProgressStatus.text != prepText) {
                        binding.tvProgressStatus.text = prepText
                    }
                }
            }
            is ImageUpscaleUiState.Success -> {
                binding.layoutActionButtons.visibility = View.VISIBLE
                binding.layoutProgress.visibility = View.GONE

                val bundle = Bundle().apply {
                    putString("outputUri", state.outputUri.toString())
                    putString("originalUri", state.originalUri.toString())
                    putInt("width", state.width)
                    putInt("height", state.height)
                    putLong("durationMs", state.durationMs)
                    putString("backend", state.backend)
                    putString("deviceName", state.deviceName)
                    putString("mediaType", "IMAGE")
                    putInt("scale", viewModel.scale.value)
                }
                viewModel.resetState()
                findNavController().safeNavigate(R.id.action_image_to_result, bundle)
            }
            is ImageUpscaleUiState.CropPreviewReady -> {
                binding.layoutActionButtons.visibility = View.VISIBLE
                binding.layoutProgress.visibility = View.GONE

                val dialog = CropPreviewDialogFragment.newInstance(state.before, state.after) {
                    viewModel.startUpscale(isCropPreview = false)
                }
                dialog.show(parentFragmentManager, CropPreviewDialogFragment.TAG)
                viewModel.resetState()
            }
            is ImageUpscaleUiState.BatchEnqueued -> {
                binding.layoutActionButtons.visibility = View.VISIBLE
                binding.layoutProgress.visibility = View.GONE
                viewModel.resetState()
                findNavController().safeNavigate(R.id.action_image_to_queue)
            }
            is ImageUpscaleUiState.Error -> {
                binding.layoutActionButtons.visibility = View.VISIBLE
                binding.layoutProgress.visibility = View.GONE

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.dialog_upscale_failed_title))
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
