package com.example.hdify.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hdify.BuildConfig
import com.example.hdify.R
import com.example.hdify.databinding.FragmentLogCrashReportBinding
import com.example.hdify.util.setOnSingleClickListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LogCrashReportFragment : Fragment() {

    private var _binding: FragmentLogCrashReportBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LogCrashReportViewModel by viewModels()
    private val adapter = LogEntryAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogCrashReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()

        // Setup RecyclerView
        binding.rvLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLogs.adapter = adapter

        // Debug testing controls: ONLY visible in DEBUG build (FR-LOG-08 / AC-LOG-8)
        if (BuildConfig.DEBUG) {
            binding.layoutDebugTest.visibility = View.VISIBLE
            binding.btnTestKotlinCrash.setOnClickListener {
                showCrashConfirmationDialog("Kotlin Exception") {
                    viewModel.triggerKotlinCrash()
                }
            }
            binding.btnTestNativeCrash.setOnClickListener {
                showCrashConfirmationDialog("Native Signal (SIGSEGV)") {
                    viewModel.triggerNativeCrash()
                }
            }
        } else {
            binding.layoutDebugTest.visibility = View.GONE
        }

        // Actions
        binding.btnCopyLogs.setOnSingleClickListener {
            val text = viewModel.getFullLogText()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("HDify Logs", text))
            Snackbar.make(binding.root, R.string.logs_copied, Snackbar.LENGTH_SHORT).show()
        }

        binding.btnShareLogs.setOnSingleClickListener {
            shareDiagnosticZip()
        }

        binding.btnClearLogs.setOnSingleClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.action_clear)
                .setMessage(R.string.dialog_clear_logs_message)
                .setPositiveButton(R.string.action_delete) { dialog, _ ->
                    viewModel.clearLogs()
                    dialog.dismiss()
                    Snackbar.make(binding.root, R.string.logs_cleared, Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.dismiss() }
                .show()
        }

        binding.btnRefreshLogs.setOnSingleClickListener {
            viewModel.loadLogs()
        }

        // Filter chips
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chip_filter_info -> viewModel.setFilter(LogFilter.INFO)
                R.id.chip_filter_warn -> viewModel.setFilter(LogFilter.WARN)
                R.id.chip_filter_error -> viewModel.setFilter(LogFilter.ERROR)
                R.id.chip_filter_crashes -> viewModel.setFilter(LogFilter.CRASHES_ONLY)
                else -> viewModel.setFilter(LogFilter.ALL)
            }
        }

        // Observe State
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.logEntries.collect { entries ->
                    adapter.submitList(entries)
                    if (entries.isEmpty()) {
                        binding.tvEmptyLogs.visibility = View.VISIBLE
                        binding.rvLogs.visibility = View.GONE
                    } else {
                        binding.tvEmptyLogs.visibility = View.GONE
                        binding.rvLogs.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun showCrashConfirmationDialog(crashType: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_test_crash_title, crashType))
            .setMessage(R.string.dialog_test_crash_message)
            .setIcon(R.drawable.ic_warning)
            .setPositiveButton(R.string.btn_trigger_crash) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun shareDiagnosticZip() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val zipUri = viewModel.exportDiagnostics(requireContext())
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, zipUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
            } catch (t: Throwable) {
                Snackbar.make(binding.root, getString(R.string.export_failed, t.message ?: ""), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.logScreenRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val minBottomPadding = (8 * density).toInt()
            binding.logScreenRoot.setPadding(systemBars.left, 0, systemBars.right, 0)
            if (binding.layoutDebugTest.visibility == View.VISIBLE) {
                binding.layoutDebugTest.setPadding(
                    (16 * density).toInt(),
                    (8 * density).toInt(),
                    (16 * density).toInt(),
                    maxOf(systemBars.bottom, minBottomPadding)
                )
            } else {
                binding.rvLogs.setPadding(
                    (8 * density).toInt(),
                    0,
                    (8 * density).toInt(),
                    maxOf(systemBars.bottom, minBottomPadding)
                )
            }
            insets
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
