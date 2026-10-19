package com.example.hdify.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.hdify.databinding.DialogAiModelsBinding
import com.example.hdify.util.applyLiquidBounce
import com.example.hdify.util.setOnLiquidClickListener
import com.example.hdify.util.setOnSingleClickListener
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AiModelsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogAiModelsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAiModelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseModelsSheet.applyLiquidBounce()
        binding.btnCloseModelsSheet.setOnSingleClickListener {
            dismiss()
        }

        binding.btnDismissModels.setOnLiquidClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AiModelsBottomSheet"

        fun newInstance(): AiModelsBottomSheet {
            return AiModelsBottomSheet()
        }
    }
}
