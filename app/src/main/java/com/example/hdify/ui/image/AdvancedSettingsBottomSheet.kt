package com.example.hdify.ui.image

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.hdify.R
import com.example.hdify.databinding.DialogAdvancedSettingsBinding
import com.example.hdify.domain.model.EngineType
import com.example.hdify.util.applyLiquidBounce
import com.example.hdify.util.setOnSingleClickListener
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

data class AdvancedSettingsData(
    val engineType: EngineType,
    val modelDir: String,
    val tileSize: Int,
    val useFp16: Boolean,
    val useTta: Boolean
)

class AdvancedSettingsBottomSheet : BottomSheetDialogFragment() {
    private var _binding: DialogAdvancedSettingsBinding? = null
    private val binding get() = _binding!!

    private var currentData: AdvancedSettingsData = AdvancedSettingsData(
        engineType = EngineType.REAL_CUGAN,
        modelDir = "models-se",
        tileSize = 0,
        useFp16 = true,
        useTta = false
    )

    private var onApplyListener: ((AdvancedSettingsData) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAdvancedSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnCloseAdvanced.applyLiquidBounce()
        binding.btnCloseAdvanced.setOnSingleClickListener {
            dismiss()
        }
        setupViews()
    }

    fun setInitialData(data: AdvancedSettingsData, listener: (AdvancedSettingsData) -> Unit) {
        currentData = data
        onApplyListener = listener
    }

    private fun setupViews() {
        // 1. Initial engine toggle selection
        if (currentData.engineType == EngineType.REAL_CUGAN) {
            binding.toggleEngine.check(R.id.btn_engine_cugan)
        } else {
            binding.toggleEngine.check(R.id.btn_engine_esrgan)
        }
        populateModelChips(currentData.engineType, currentData.modelDir)

        binding.toggleEngine.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newEngine = if (checkedId == R.id.btn_engine_cugan) {
                    EngineType.REAL_CUGAN
                } else {
                    EngineType.REAL_ESRGAN
                }
                val defaultModel = if (newEngine == EngineType.REAL_CUGAN) "models-se" else "realesrgan-x4plus"
                populateModelChips(newEngine, defaultModel)
            }
        }

        // 2. Slider tile size
        binding.sliderTileSize.value = currentData.tileSize.toFloat()
        updateTileLabel(currentData.tileSize)
        binding.sliderTileSize.addOnChangeListener { _, value, _ ->
            updateTileLabel(value.toInt())
        }

        // 3. Switches
        binding.switchFp16.isChecked = currentData.useFp16
        binding.switchTta.isChecked = currentData.useTta

        // 4. Save button
        binding.btnApplyAdvanced.setOnSingleClickListener {
            val selectedEngine = if (binding.toggleEngine.checkedButtonId == R.id.btn_engine_cugan) {
                EngineType.REAL_CUGAN
            } else {
                EngineType.REAL_ESRGAN
            }

            val checkedChipId = binding.chipGroupModels.checkedChipId
            val selectedChip = binding.chipGroupModels.findViewById<Chip>(checkedChipId)
            val selectedModel = (selectedChip?.tag as? String) ?: if (selectedEngine == EngineType.REAL_CUGAN) "models-se" else "realesrgan-x4plus"

            val updated = AdvancedSettingsData(
                engineType = selectedEngine,
                modelDir = selectedModel,
                tileSize = binding.sliderTileSize.value.toInt(),
                useFp16 = binding.switchFp16.isChecked,
                useTta = binding.switchTta.isChecked
            )
            onApplyListener?.invoke(updated)
            dismiss()
        }
    }

    private fun updateTileLabel(tileSize: Int) {
        binding.tvTileSizeLabel.text = if (tileSize <= 0) {
            getString(R.string.tile_size_auto)
        } else {
            getString(R.string.tile_size_slider_label, tileSize)
        }
    }

    private fun populateModelChips(engine: EngineType, currentSelectedModel: String) {
        binding.chipGroupModels.removeAllViews()

        val models = if (engine == EngineType.REAL_CUGAN) {
            listOf(
                "models-se" to getString(R.string.model_se_label),
                "models-pro" to getString(R.string.model_pro_label)
            )
        } else {
            listOf(
                "realesrgan-x4plus" to "x4plus (Foto Kualitas Tinggi)",
                "realesrgan-x4plus-anime" to "x4plus-anime (Anime Kualitas Tinggi)",
                "realesr-animevideov3-x4" to "animevideov3 (Video / Ringan)"
            )
        }

        var foundMatch = false
        for ((modelId, label) in models) {
            val chip = Chip(requireContext()).apply {
                text = label
                tag = modelId
                isCheckable = true
                id = View.generateViewId()
            }
            binding.chipGroupModels.addView(chip)

            if (modelId == currentSelectedModel) {
                chip.isChecked = true
                foundMatch = true
            }
        }

        if (!foundMatch && binding.chipGroupModels.childCount > 0) {
            (binding.chipGroupModels.getChildAt(0) as? Chip)?.isChecked = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AdvancedSettingsBottomSheet"

        fun newInstance(data: AdvancedSettingsData, listener: (AdvancedSettingsData) -> Unit): AdvancedSettingsBottomSheet {
            return AdvancedSettingsBottomSheet().apply {
                setInitialData(data, listener)
            }
        }
    }
}
