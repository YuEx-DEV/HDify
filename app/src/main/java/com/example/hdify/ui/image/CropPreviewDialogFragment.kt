package com.example.hdify.ui.image

import android.app.Dialog
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import com.example.hdify.R
import com.example.hdify.databinding.DialogCropPreviewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CropPreviewDialogFragment : DialogFragment() {

    private var _binding: DialogCropPreviewBinding? = null
    private val binding get() = _binding!!

    private var beforeBitmap: Bitmap? = null
    private var afterBitmap: Bitmap? = null
    private var onProceedListener: (() -> Unit)? = null

    fun setBitmaps(before: Bitmap, after: Bitmap, onProceed: () -> Unit) {
        this.beforeBitmap = before
        this.afterBitmap = after
        this.onProceedListener = onProceed
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCropPreviewBinding.inflate(layoutInflater)

        binding.previewSlider.setBitmaps(beforeBitmap, afterBitmap)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_crop_preview_title)
            .setMessage(R.string.crop_preview_desc)
            .setView(binding.root)
            .setPositiveButton(R.string.btn_start_upscale) { _, _ ->
                onProceedListener?.invoke()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
    }

    override fun onDestroyView() {
        _binding?.previewSlider?.setBitmaps(null, null)
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CropPreviewDialog"

        fun newInstance(before: Bitmap, after: Bitmap, onProceed: () -> Unit): CropPreviewDialogFragment {
            return CropPreviewDialogFragment().apply {
                setBitmaps(before, after, onProceed)
            }
        }
    }
}
