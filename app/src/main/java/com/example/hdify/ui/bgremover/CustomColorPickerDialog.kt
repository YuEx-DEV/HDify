package com.example.hdify.ui.bgremover

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import com.example.hdify.databinding.DialogCustomColorPickerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CustomColorPickerDialog : DialogFragment() {

    private var _binding: DialogCustomColorPickerBinding? = null
    private val binding get() = _binding!!

    private var initialColor: Int = Color.parseColor("#D32F2F")
    private var currentColor: Int = Color.parseColor("#D32F2F")
    private var onColorSelected: ((Int) -> Unit)? = null
    private var isUpdatingFromSlider = false
    private var isUpdatingFromText = false

    private val popularColors = listOf(
        "#D32F2F", // Merah Pas Foto
        "#1976D2", // Biru Pas Foto
        "#FFFFFF", // Putih
        "#000000", // Hitam
        "#2E7D32", // Hijau Green Screen
        "#FFB300", // Amber / Kuning
        "#FB8C00", // Oranye
        "#8E24AA", // Ungu
        "#E91E63", // Pink
        "#00ACC1", // Cyan
        "#90CAF9", // Pastel Biru
        "#F48FB1", // Pastel Pink
        "#A5D6A7", // Pastel Hijau
        "#FFF59D", // Pastel Kuning
        "#757575"  // Abu-abu
    )

    companion object {
        const val TAG = "CustomColorPickerDialog"

        fun newInstance(
            initialColor: Int,
            onColorSelected: (Int) -> Unit
        ): CustomColorPickerDialog {
            return CustomColorPickerDialog().apply {
                this.initialColor = if (initialColor == Color.TRANSPARENT) Color.parseColor("#D32F2F") else initialColor
                this.currentColor = this.initialColor
                this.onColorSelected = onColorSelected
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCustomColorPickerBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()

        setupSwatches()
        setupSliders()
        setupHexInput()
        setupActions()
        updateUiForColor(currentColor, updateText = true, updateSliders = true)

        return dialog
    }

    private fun setupSwatches() {
        val density = resources.displayMetrics.density
        val size = (36f * density).toInt()
        val margin = (4f * density).toInt()

        binding.layoutPaletteSwatches.removeAllViews()

        for (hex in popularColors) {
            val color = Color.parseColor(hex)
            val swatch = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, margin, margin, margin)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    val strokeColor = when (hex) {
                        "#FFFFFF" -> Color.parseColor("#8E8E93")
                        "#000000" -> Color.parseColor("#71717A")
                        else -> Color.argb(80, 128, 128, 128)
                    }
                    setStroke((1.5f * density).toInt(), strokeColor)
                }
                setOnClickListener {
                    currentColor = color
                    updateUiForColor(color, updateText = true, updateSliders = true)
                }
            }
            binding.layoutPaletteSwatches.addView(swatch)
        }
    }

    private fun setupSliders() {
        binding.sliderRed.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingFromText) {
                isUpdatingFromSlider = true
                val r = value.toInt()
                val g = binding.sliderGreen.value.toInt()
                val b = binding.sliderBlue.value.toInt()
                currentColor = Color.rgb(r, g, b)
                updateUiForColor(currentColor, updateText = true, updateSliders = false)
                isUpdatingFromSlider = false
            }
        }

        binding.sliderGreen.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingFromText) {
                isUpdatingFromSlider = true
                val r = binding.sliderRed.value.toInt()
                val g = value.toInt()
                val b = binding.sliderBlue.value.toInt()
                currentColor = Color.rgb(r, g, b)
                updateUiForColor(currentColor, updateText = true, updateSliders = false)
                isUpdatingFromSlider = false
            }
        }

        binding.sliderBlue.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingFromText) {
                isUpdatingFromSlider = true
                val r = binding.sliderRed.value.toInt()
                val g = binding.sliderGreen.value.toInt()
                val b = value.toInt()
                currentColor = Color.rgb(r, g, b)
                updateUiForColor(currentColor, updateText = true, updateSliders = false)
                isUpdatingFromSlider = false
            }
        }
    }

    private fun setupHexInput() {
        binding.etHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (isUpdatingFromSlider) return
                val text = s?.toString()?.trim() ?: return
                val formatted = if (text.startsWith("#")) text else "#$text"
                if (formatted.length == 7) {
                    try {
                        val parsed = Color.parseColor(formatted)
                        currentColor = parsed
                        isUpdatingFromText = true
                        updateUiForColor(parsed, updateText = false, updateSliders = true)
                        isUpdatingFromText = false
                    } catch (_: Exception) {}
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updateUiForColor(color: Int, updateText: Boolean, updateSliders: Boolean) {
        binding.viewColorPreview.setBackgroundColor(color)

        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        binding.tvValRed.text = r.toString()
        binding.tvValGreen.text = g.toString()
        binding.tvValBlue.text = b.toString()

        val hexStr = String.format("#%02X%02X%02X", r, g, b)
        binding.tvHexDisplay.text = hexStr

        if (updateText) {
            binding.etHex.setText(hexStr)
        }

        if (updateSliders) {
            binding.sliderRed.value = r.toFloat()
            binding.sliderGreen.value = g.toFloat()
            binding.sliderBlue.value = b.toFloat()
        }
    }

    private fun setupActions() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnApply.setOnClickListener {
            onColorSelected?.invoke(currentColor)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
