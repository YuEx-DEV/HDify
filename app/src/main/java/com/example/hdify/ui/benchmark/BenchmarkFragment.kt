package com.example.hdify.ui.benchmark

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
import com.example.hdify.databinding.FragmentBenchmarkBinding
import com.example.hdify.databinding.ItemBenchmarkResultBinding
import com.example.hdify.util.setOnSingleClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BenchmarkFragment : Fragment() {

    private var _binding: FragmentBenchmarkBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BenchmarkViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBenchmarkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        setupListeners()
        observeViewModel()
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.benchmarkRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Top inset is handled by navHostFragment in MainActivity
            binding.benchmarkRoot.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupListeners() {
        binding.btnRunBenchmark.setOnSingleClickListener {
            viewModel.runBenchmark()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isRunning.collect { running ->
                        binding.btnRunBenchmark.isEnabled = !running
                        binding.layoutBenchmarkProgress.visibility = if (running) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.statusText.collect { status ->
                        binding.tvBenchmarkStatus.text = status
                    }
                }

                launch {
                    viewModel.benchmarkResults.collect { results ->
                        binding.layoutResultsContainer.removeAllViews()
                        for (res in results) {
                            val cardBinding = ItemBenchmarkResultBinding.inflate(
                                layoutInflater,
                                binding.layoutResultsContainer,
                                false
                            )

                            cardBinding.tvEngineName.text = res.engineName
                            cardBinding.chipScale.text = getString(R.string.scale_short_format, res.scale)
                            cardBinding.tvMsPerTile.text = getString(R.string.benchmark_ms_per_tile, res.msPerTile)
                            cardBinding.tvEst1080p.text = getString(R.string.benchmark_est_1080p, res.est1080pSec)
                            cardBinding.tvEstVideo720p.text = getString(R.string.benchmark_est_video_720p, res.est720pVideoSec) +
                                    " (%.1f FPS)".format(res.fps720p)

                            if (res.passed) {
                                cardBinding.tvStatusBadge.text = getString(R.string.benchmark_vulkan_passed)
                                cardBinding.tvStatusBadge.setTextColor(requireContext().getColor(R.color.status_completed_text))
                            } else {
                                cardBinding.tvStatusBadge.text = getString(R.string.benchmark_vulkan_failed, res.errorMessage ?: "")
                                cardBinding.tvStatusBadge.setTextColor(requireContext().getColor(R.color.status_failed_text))
                            }

                            binding.layoutResultsContainer.addView(cardBinding.root)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
