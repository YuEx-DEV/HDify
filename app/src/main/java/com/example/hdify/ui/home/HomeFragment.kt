package com.example.hdify.ui.home

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
import androidx.navigation.fragment.findNavController
import com.example.hdify.R
import com.example.hdify.databinding.FragmentHomeBinding
import com.example.hdify.util.safeNavigate
import com.example.hdify.util.setOnLiquidClickListener
import com.example.hdify.util.setOnSingleClickListener
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        animateEntrance()

        binding.cardHardwareStatus.setOnLiquidClickListener {
            findNavController().safeNavigate(R.id.nav_hardware_info)
        }

        binding.cardImage.setOnLiquidClickListener {
            findNavController().safeNavigate(R.id.action_home_to_image_upscale)
        }

        binding.cardBgRemover.setOnLiquidClickListener {
            findNavController().safeNavigate(R.id.action_home_to_bg_remover)
        }

        binding.cardVideo.setOnLiquidClickListener {
            findNavController().safeNavigate(R.id.action_home_to_video_upscale)
        }

        binding.cardDownloader.setOnLiquidClickListener {
            findNavController().safeNavigate(R.id.action_home_to_downloader)
        }

        binding.cardCompressor.setOnLiquidClickListener {
            findNavController().safeNavigate(R.id.action_home_to_compressor)
        }

        binding.cardHomeDeveloper.setOnLiquidClickListener {
            try {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.tiktok.com/@yuukaonlyone_?_r=1&_t=ZS-9ANT1Wtl7kF")
                )
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), getString(R.string.toast_tiktok_open_failed), android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is HomeUiState.Loading -> {
                            binding.tvHwStatus.setText(R.string.hw_status_checking)
                        }
                        is HomeUiState.Success -> {
                            val gpu = state.capabilities.gpuInfo
                            if (gpu.vulkanAvailable) {
                                binding.tvHwStatus.text = getString(R.string.hw_status_vulkan_active, gpu.deviceName)
                                binding.ivHwIcon.setColorFilter(requireContext().getColor(R.color.status_green))
                            } else {
                                binding.tvHwStatus.setText(R.string.hw_status_vulkan_disabled)
                                binding.ivHwIcon.setColorFilter(requireContext().getColor(R.color.status_red))
                            }
                        }
                        is HomeUiState.Error -> {
                            binding.tvHwStatus.text = state.message
                            binding.ivHwIcon.setColorFilter(requireContext().getColor(R.color.status_red))
                        }
                    }
                }
            }
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.homeRoot.setPadding(systemBars.left, 0, systemBars.right, 0)
            val bottomNavReserve = (136 * resources.displayMetrics.density).toInt()
            binding.homeScroll.setPadding(
                binding.homeScroll.paddingLeft,
                binding.homeScroll.paddingTop,
                binding.homeScroll.paddingRight,
                systemBars.bottom + bottomNavReserve
            )
            insets
        }
    }

    private fun animateEntrance() {
        val views = listOf(
            binding.tvEditionBadge,
            binding.cardHardwareStatus,
            binding.cardImage,
            binding.cardBgRemover,
            binding.cardVideo,
            binding.cardDownloader,
            binding.cardCompressor,
            binding.cardHomeDeveloper
        )
        com.example.hdify.util.animateCascadeEntrance(views, startDelay = 15L, staggerDelay = 35L, duration = 340L)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
