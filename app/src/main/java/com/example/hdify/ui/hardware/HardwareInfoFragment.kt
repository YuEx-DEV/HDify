package com.example.hdify.ui.hardware

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.hdify.R
import com.example.hdify.databinding.FragmentHardwareInfoBinding
import com.example.hdify.logging.HdLogger
import com.example.hdify.util.setOnSingleClickListener
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HardwareInfoFragment : Fragment() {

    private var _binding: FragmentHardwareInfoBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HardwareInfoViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHardwareInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()

        binding.btnRunSelfTest.setOnSingleClickListener {
            viewModel.runGpuSelfTest()
        }

        binding.btnExportDiagnostics.setOnSingleClickListener {
            exportDiagnostics()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is HardwareUiState.Loading -> {
                                binding.tvGpuName.text = getString(R.string.hw_status_checking)
                            }
                            is HardwareUiState.Success -> {
                                bindHardwareData(state)
                            }
                            is HardwareUiState.Error -> {
                                binding.tvGpuName.text = state.message
                            }
                        }
                    }
                }

                launch {
                    viewModel.selfTestEvents.collect { event ->
                        when (event) {
                            is SelfTestEvent.Success -> {
                                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                            }
                            is SelfTestEvent.Failure -> {
                                Snackbar.make(binding.root, "${event.errorCode}: ${event.message}", Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bindHardwareData(state: HardwareUiState.Success) {
        val gpu = state.capabilities.gpuInfo

        binding.tvGpuName.text = gpu.deviceName
        binding.tvGpuType.text = gpu.deviceType
        binding.tvGpuApi.text = gpu.apiVersion
        binding.tvGpuDriver.text = gpu.driverVersion

        val heapMb = gpu.memoryHeapBytes / (1024 * 1024)
        binding.tvGpuHeap.text = if (gpu.memoryHeapBytes > 0) "$heapMb MB" else "Shared / Unified"

        val supportedStr = getString(R.string.gpu_supported)
        val notSupportedStr = getString(R.string.gpu_not_supported)

        binding.tvGpuFp16Storage.text = if (gpu.supportFp16Storage) supportedStr else notSupportedStr
        binding.tvGpuFp16Arith.text = if (gpu.supportFp16Arithmetic) supportedStr else notSupportedStr
        binding.tvGpuCount.text = "${gpu.gpuCount}"

        // Codecs
        val availStr = getString(R.string.codec_available)
        val notAvailStr = getString(R.string.codec_not_available)

        binding.tvCodecAvcDec.text = "${getString(R.string.codec_avc_dec)} ${if (state.capabilities.avcDecoderAvailable) availStr else notAvailStr}"
        binding.tvCodecAvcEnc.text = "${getString(R.string.codec_avc_enc)} ${if (state.capabilities.avcEncoderAvailable) availStr else notAvailStr}"
        binding.tvCodecHevcDec.text = "${getString(R.string.codec_hevc_dec)} ${if (state.capabilities.hevcDecoderAvailable) availStr else notAvailStr}"
        binding.tvCodecHevcEnc.text = "${getString(R.string.codec_hevc_enc)} ${if (state.capabilities.hevcEncoderAvailable) availStr else notAvailStr}"
    }

    private fun exportDiagnostics() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val zipUri = viewModel.exportDiagnostics(requireContext())
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, zipUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.btn_export_diagnostics)))
            } catch (t: Throwable) {
                HdLogger.e("HardwareInfo", null, "HD-SYS-999", "Gagal mengekspor diagnosa", t)
                Snackbar.make(binding.root, getString(R.string.export_failed, t.message ?: ""), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.hardwareInfoRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val minBottomPadding = (12 * density).toInt()
            binding.hardwareInfoRoot.setPadding(systemBars.left, 0, systemBars.right, 0)
            binding.layoutHwActions.setPadding(
                (16 * density).toInt(),
                (12 * density).toInt(),
                (16 * density).toInt(),
                maxOf(systemBars.bottom, minBottomPadding)
            )
            insets
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
