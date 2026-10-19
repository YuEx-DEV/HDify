package com.example.hdify.ui.bgremover

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.hdify.R
import com.example.hdify.databinding.FragmentBgRemoverBinding
import com.example.hdify.util.animateCascadeEntrance
import com.example.hdify.util.applyLiquidBounce
import com.example.hdify.util.safeNavigate
import com.example.hdify.util.safePopBackStack
import com.example.hdify.util.setOnLiquidClickListener
import com.example.hdify.util.setOnSingleClickListener
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint
class BgRemoverFragment : Fragment() {

    private var _binding: FragmentBgRemoverBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BgRemoverViewModel by viewModels()

    private var loadedSourceBitmap: Bitmap? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onImageSelected(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBgRemoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        setupListeners()
        setupDrawingView()
        observeState()
        animateEntrance()
    }

    private fun setupDrawingView() {
        binding.maskDrawingView.onHistoryChangedListener = { canUndo, canRedo ->
            binding.btnUndo.isEnabled = canUndo
            binding.btnRedo.isEnabled = canRedo
            viewModel.updateHistoryState(canUndo, canRedo)
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnSingleClickListener {
            findNavController().safePopBackStack()
        }

        // Image picking
        binding.btnPickEmpty.setOnLiquidClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.btnChangeImage.setOnLiquidClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Manual Tool Mode Toggle (Auto Color vs Erase vs Restore vs Pan/Zoom)
        binding.toggleToolMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_tool_magic -> {
                        binding.maskDrawingView.brushMode = BrushMode.MAGIC_COLOR
                        binding.layoutMagicControls.visibility = View.VISIBLE
                        binding.layoutBrushSize.visibility = View.GONE
                        binding.layoutPanHint.visibility = View.GONE
                        viewModel.setBrushMode(BrushMode.MAGIC_COLOR)
                    }
                    R.id.btn_tool_erase -> {
                        binding.maskDrawingView.brushMode = BrushMode.ERASE
                        binding.layoutMagicControls.visibility = View.GONE
                        binding.layoutBrushSize.visibility = View.VISIBLE
                        binding.layoutPanHint.visibility = View.GONE
                        viewModel.setBrushMode(BrushMode.ERASE)
                    }
                    R.id.btn_tool_restore -> {
                        binding.maskDrawingView.brushMode = BrushMode.RESTORE
                        binding.layoutMagicControls.visibility = View.GONE
                        binding.layoutBrushSize.visibility = View.VISIBLE
                        binding.layoutPanHint.visibility = View.GONE
                        viewModel.setBrushMode(BrushMode.RESTORE)
                    }
                    R.id.btn_tool_pan -> {
                        binding.maskDrawingView.brushMode = BrushMode.PAN_ZOOM
                        binding.layoutMagicControls.visibility = View.GONE
                        binding.layoutBrushSize.visibility = View.GONE
                        binding.layoutPanHint.visibility = View.VISIBLE
                        viewModel.setBrushMode(BrushMode.PAN_ZOOM)
                    }
                }
            }
        }

        // Magic Tolerance Slider
        binding.sliderMagicTolerance.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.maskDrawingView.magicTolerance = value.toInt()
                binding.tvMagicToleranceVal.text = "${value.toInt()}"
            }
        }

        // Magic Contiguous Chip
        binding.chipMagicContiguous.setOnCheckedChangeListener { _, isChecked ->
            binding.maskDrawingView.magicContiguous = isChecked
        }

        // Undo, Redo, Reset with tactile bounce
        binding.btnUndo.applyLiquidBounce()
        binding.btnUndo.setOnSingleClickListener {
            binding.maskDrawingView.undo()
        }

        binding.btnRedo.applyLiquidBounce()
        binding.btnRedo.setOnSingleClickListener {
            binding.maskDrawingView.redo()
        }

        binding.btnResetAuto.applyLiquidBounce()
        binding.btnResetAuto.setOnSingleClickListener {
            binding.maskDrawingView.resetToAuto()
            Toast.makeText(requireContext(), getString(R.string.bg_remover_toast_mask_reset), Toast.LENGTH_SHORT).show()
        }

        // Brush Size Slider
        binding.sliderBrushSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val density = resources.displayMetrics.density
                binding.maskDrawingView.brushRadius = value * density
                binding.tvBrushSizeVal.text = "${value.toInt()}px"
            }
        }

        // Dedicated AI Auto Remove Button
        binding.btnAutoRemoveAi.setOnLiquidClickListener {
            binding.btnAutoRemoveAi.isEnabled = false
            binding.btnAutoRemoveAi.text = getString(R.string.bg_btn_auto_remove_ai_running)
            viewModel.runAutoRemove { newMask ->
                binding.btnAutoRemoveAi.isEnabled = true
                binding.btnAutoRemoveAi.text = getString(R.string.bg_btn_auto_remove_ai)
                if (newMask != null) {
                    binding.maskDrawingView.applyNewMask(newMask)
                    Toast.makeText(requireContext(), getString(R.string.bg_remover_toast_auto_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.bg_remover_toast_auto_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Background Color Buttons (with tactile spring bounce)
        binding.btnBgTransparent.applyLiquidBounce()
        binding.btnBgTransparent.setOnSingleClickListener {
            binding.maskDrawingView.bgColor = Color.TRANSPARENT
            viewModel.setBackgroundColor(Color.TRANSPARENT)
            updateSelectedBgColorButton(Color.TRANSPARENT)
        }

        binding.btnBgWhite.applyLiquidBounce()
        binding.btnBgWhite.setOnSingleClickListener {
            binding.maskDrawingView.bgColor = Color.WHITE
            viewModel.setBackgroundColor(Color.WHITE)
            updateSelectedBgColorButton(Color.WHITE)
        }

        binding.btnBgRed.applyLiquidBounce()
        binding.btnBgRed.setOnSingleClickListener {
            binding.maskDrawingView.bgColor = BgRemoverViewModel.COLOR_RED_PAS_FOTO
            viewModel.setBackgroundColor(BgRemoverViewModel.COLOR_RED_PAS_FOTO)
            updateSelectedBgColorButton(BgRemoverViewModel.COLOR_RED_PAS_FOTO)
        }

        binding.btnBgBlue.applyLiquidBounce()
        binding.btnBgBlue.setOnSingleClickListener {
            binding.maskDrawingView.bgColor = BgRemoverViewModel.COLOR_BLUE_PAS_FOTO
            viewModel.setBackgroundColor(BgRemoverViewModel.COLOR_BLUE_PAS_FOTO)
            updateSelectedBgColorButton(BgRemoverViewModel.COLOR_BLUE_PAS_FOTO)
        }

        binding.btnBgBlack.applyLiquidBounce()
        binding.btnBgBlack.setOnSingleClickListener {
            binding.maskDrawingView.bgColor = Color.BLACK
            viewModel.setBackgroundColor(Color.BLACK)
            updateSelectedBgColorButton(Color.BLACK)
        }

        binding.btnBgCustom.applyLiquidBounce()
        binding.btnBgCustom.setOnSingleClickListener {
            openCustomColorPicker()
        }

        // Save
        binding.btnSave.setOnLiquidClickListener {
            val mask = binding.maskDrawingView.getMask() ?: return@setOnLiquidClickListener
            binding.btnSave.isEnabled = false
            viewModel.saveToGallery(mask) { success, _ ->
                binding.btnSave.isEnabled = true
                if (success) {
                    Toast.makeText(requireContext(), getString(R.string.bg_remover_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.bg_remover_toast_save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Open in AI Upscaler
        binding.cardUpscaleShortcut.setOnLiquidClickListener {
            val mask = binding.maskDrawingView.getMask() ?: return@setOnLiquidClickListener
            binding.cardUpscaleShortcut.isEnabled = false
            viewModel.prepareForUpscaler(mask) { tempUri ->
                binding.cardUpscaleShortcut.isEnabled = true
                if (tempUri != null) {
                    val bundle = Bundle().apply {
                        putStringArrayList("sourceUris", arrayListOf(tempUri.toString()))
                    }
                    findNavController().safeNavigate(R.id.action_bg_remover_to_image_upscale, bundle)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.bg_remover_toast_prepare_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Set default tool to Magic Auto Color
        binding.toggleToolMode.check(R.id.btn_tool_magic)
        binding.maskDrawingView.brushMode = BrushMode.MAGIC_COLOR
        binding.layoutMagicControls.visibility = View.VISIBLE
        binding.layoutBrushSize.visibility = View.GONE
        binding.layoutPanHint.visibility = View.GONE
        updateSelectedBgColorButton(Color.TRANSPARENT)
    }

    private fun updateSelectedBgColorButton(selectedColor: Int, isCustom: Boolean = false) {
        val colorButtons = listOf(
            binding.btnBgTransparent,
            binding.btnBgWhite,
            binding.btnBgRed,
            binding.btnBgBlue,
            binding.btnBgBlack,
            binding.btnBgCustom
        )

        val activeBtn = when {
            isCustom -> binding.btnBgCustom
            selectedColor == Color.TRANSPARENT -> binding.btnBgTransparent
            selectedColor == Color.WHITE -> binding.btnBgWhite
            selectedColor == BgRemoverViewModel.COLOR_RED_PAS_FOTO -> binding.btnBgRed
            selectedColor == BgRemoverViewModel.COLOR_BLUE_PAS_FOTO -> binding.btnBgBlue
            selectedColor == Color.BLACK -> binding.btnBgBlack
            else -> binding.btnBgCustom
        }

        val primary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        val onPrimaryContainer = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnPrimaryContainer)
        val primaryContainer = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimaryContainer)
        val onSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)

        colorButtons.forEach { btn ->
            if (btn == activeBtn) {
                btn.strokeColor = ColorStateList.valueOf(primary)
                btn.strokeWidth = (1.8f * resources.displayMetrics.density).toInt()
                btn.backgroundTintList = ColorStateList.valueOf(primaryContainer)
                btn.setTextColor(onPrimaryContainer)
            } else {
                btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#444450"))
                btn.strokeWidth = (1f * resources.displayMetrics.density).toInt()
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                btn.setTextColor(onSurface)
            }
        }
    }

    private fun openCustomColorPicker() {
        val initial = if (binding.maskDrawingView.bgColor == Color.TRANSPARENT) {
            Color.parseColor("#D32F2F")
        } else {
            binding.maskDrawingView.bgColor
        }

        val dialog = CustomColorPickerDialog.newInstance(initial) { selectedColor ->
            binding.maskDrawingView.bgColor = selectedColor
            viewModel.setCustomColor(selectedColor)
            val hex = String.format("#%06X", (0xFFFFFF and selectedColor))
            binding.btnBgCustom.text = hex
            updateSelectedBgColorButton(selectedColor, isCustom = true)
        }
        dialog.show(parentFragmentManager, CustomColorPickerDialog.TAG)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is BgRemoverUiState.Idle -> {
                            binding.layoutEmpty.visibility = View.VISIBLE
                            binding.layoutLoading.visibility = View.GONE
                            binding.layoutCanvasFloatingTools.visibility = View.GONE
                            binding.btnAutoRemoveAi.visibility = View.GONE
                            binding.tvToolsLabel.visibility = View.GONE
                            binding.cardManualTools.visibility = View.GONE
                            binding.tvBgOptionsLabel.visibility = View.GONE
                            binding.cardBgColors.visibility = View.GONE
                            binding.layoutBgColors.visibility = View.GONE
                            binding.btnChangeImage.visibility = View.GONE
                            binding.btnSave.visibility = View.GONE
                            binding.cardUpscaleShortcut.visibility = View.GONE
                        }
                        is BgRemoverUiState.Processing -> {
                            binding.layoutEmpty.visibility = View.GONE
                            binding.layoutLoading.visibility = View.VISIBLE
                            binding.layoutCanvasFloatingTools.visibility = View.GONE
                            binding.btnAutoRemoveAi.visibility = View.GONE
                            binding.tvToolsLabel.visibility = View.GONE
                            binding.cardManualTools.visibility = View.GONE
                            binding.tvBgOptionsLabel.visibility = View.GONE
                            binding.cardBgColors.visibility = View.GONE
                            binding.layoutBgColors.visibility = View.GONE
                            binding.btnChangeImage.isEnabled = false
                            binding.btnSave.isEnabled = false
                            binding.cardUpscaleShortcut.visibility = View.GONE
                        }
                        is BgRemoverUiState.Ready -> {
                            binding.layoutEmpty.visibility = View.GONE
                            binding.layoutLoading.visibility = View.GONE
                            binding.layoutCanvasFloatingTools.visibility = View.VISIBLE
                            binding.btnAutoRemoveAi.visibility = View.VISIBLE
                            binding.btnAutoRemoveAi.isEnabled = true
                            binding.tvToolsLabel.visibility = View.VISIBLE
                            binding.cardManualTools.visibility = View.VISIBLE
                            binding.tvBgOptionsLabel.visibility = View.VISIBLE
                            binding.cardBgColors.visibility = View.VISIBLE
                            binding.layoutBgColors.visibility = View.VISIBLE
                            binding.btnChangeImage.visibility = View.VISIBLE
                            binding.btnChangeImage.isEnabled = true
                            binding.btnSave.visibility = View.VISIBLE
                            binding.btnSave.isEnabled = true
                            binding.cardUpscaleShortcut.visibility = View.VISIBLE

                            // Setup mask view with bitmaps only when a new image is loaded
                            if (loadedSourceBitmap !== state.source) {
                                loadedSourceBitmap = state.source
                                binding.maskDrawingView.setup(state.source, state.mask)
                            }

                            if (state.isSaved) {
                                binding.btnSave.text = getString(R.string.bg_remover_saved_badge)
                                binding.btnSave.setIconResource(R.drawable.ic_check_circle)
                            } else {
                                binding.btnSave.text = getString(R.string.bg_remover_save)
                                binding.btnSave.setIconResource(R.drawable.ic_download)
                            }

                            updateSelectedBgColorButton(binding.maskDrawingView.bgColor)
                        }
                        is BgRemoverUiState.Error -> {
                            binding.layoutEmpty.visibility = View.VISIBLE
                            binding.layoutLoading.visibility = View.GONE
                            binding.layoutCanvasFloatingTools.visibility = View.GONE
                            binding.btnAutoRemoveAi.visibility = View.GONE
                            binding.tvToolsLabel.visibility = View.GONE
                            binding.cardManualTools.visibility = View.GONE
                            binding.tvBgOptionsLabel.visibility = View.GONE
                            binding.cardBgColors.visibility = View.GONE
                            binding.layoutBgColors.visibility = View.GONE
                            binding.btnChangeImage.visibility = View.GONE
                            binding.btnSave.visibility = View.GONE
                            binding.cardUpscaleShortcut.visibility = View.GONE
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bgRemoverRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutHeader.setPadding(
                systemBars.left + 16,
                systemBars.top + 12,
                systemBars.right + 16,
                12
            )
            binding.bgRemoverRoot.setPadding(systemBars.left, 0, systemBars.right, 0)
            val minBottom = (16 * resources.displayMetrics.density).toInt()
            binding.layoutBottomBar.setPadding(
                binding.layoutBottomBar.paddingLeft,
                binding.layoutBottomBar.paddingTop,
                binding.layoutBottomBar.paddingRight,
                systemBars.bottom.coerceAtLeast(minBottom)
            )
            insets
        }
    }

    private fun animateEntrance() {
        val views = listOf(
            binding.layoutHeader,
            binding.cardPreview
        )
        animateCascadeEntrance(views)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadedSourceBitmap = null
        _binding = null
    }
}
