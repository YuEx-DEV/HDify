package com.example.hdify.ui.history

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hdify.R
import com.example.hdify.data.database.JobEntity
import com.example.hdify.databinding.BottomSheetJobDetailBinding
import com.example.hdify.databinding.FragmentHistoryBinding
import com.example.hdify.util.animateCascadeEntrance
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var historyAdapter: HistoryAdapter
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        setupRecyclerView()
        setupHeaderActions()
        observeHistory()
        animateCascadeEntrance(listOf(binding.tvHistoryTitle, binding.btnClearHistory))
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.historyRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.historyRoot.setPadding(systemBars.left, 0, systemBars.right, 0)
            val bottomNavReserve = (136 * resources.displayMetrics.density).toInt()
            binding.rvHistory.setPadding(
                binding.rvHistory.paddingLeft,
                binding.rvHistory.paddingTop,
                binding.rvHistory.paddingRight,
                systemBars.bottom + bottomNavReserve
            )
            insets
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(
            onItemClicked = { job -> showJobDetailBottomSheet(job) },
            onDeleteClicked = { job -> confirmDeleteJob(job) }
        )
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    private fun setupHeaderActions() {
        binding.btnClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_clear_history_title)
                .setMessage(R.string.dialog_clear_history_msg)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    viewModel.clearHistory()
                    Snackbar.make(binding.root, R.string.history_cleared_snackbar, Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    private fun confirmDeleteJob(job: JobEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_delete_job_title))
            .setMessage(getString(R.string.dialog_delete_job_msg, job.id))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteJob(job.id)
                Snackbar.make(binding.root, R.string.history_deleted_snackbar, Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showJobDetailBottomSheet(job: JobEntity) {
        val context = requireContext()
        val sheetBinding = BottomSheetJobDetailBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(sheetBinding.root)

        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.window?.setDimAmount(0.75f)
        dialog.setCanceledOnTouchOutside(true)

        val mediaLabel = if (job.mediaType == "IMAGE") getString(R.string.media_image) else getString(R.string.media_video)
        sheetBinding.tvDetailTitle.text = getString(R.string.job_title_format, job.id, mediaLabel)
        sheetBinding.tvDetailEngine.text = "${job.engineName} • ${getString(R.string.job_scale_format, job.scale)}"
        sheetBinding.ivDetailIcon.setImageResource(if (job.mediaType == "IMAGE") R.drawable.ic_image else R.drawable.ic_video)

        val timeStr = dateFormat.format(Date(job.completedAt ?: job.createdAt))
        sheetBinding.tvDetailTime.text = timeStr
        sheetBinding.tvDetailSource.text = job.sourceUri

        ViewCompat.setOnApplyWindowInsetsListener(sheetBinding.root) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBars.bottom + 24)
            insets
        }

        when (job.status) {
            "COMPLETED" -> {
                sheetBinding.chipDetailStatus.text = getString(R.string.status_completed)
                sheetBinding.chipDetailStatus.setChipBackgroundColorResource(R.color.status_completed_container)
                sheetBinding.chipDetailStatus.setTextColor(ContextCompat.getColor(context, R.color.status_completed_text))

                if (!job.outputUri.isNullOrBlank()) {
                    sheetBinding.layoutDetailOutput.visibility = View.VISIBLE
                    sheetBinding.tvDetailOutput.text = job.outputUri
                    sheetBinding.layoutPositiveActions.visibility = View.VISIBLE
                    sheetBinding.btnDetailPrimary.setOnClickListener {
                        dialog.dismiss()
                        openMediaFile(job)
                    }
                    sheetBinding.btnDetailSecondary.setOnClickListener {
                        dialog.dismiss()
                        shareMediaFile(job)
                    }
                } else {
                    sheetBinding.layoutDetailOutput.visibility = View.GONE
                    sheetBinding.layoutPositiveActions.visibility = View.GONE
                }
                sheetBinding.layoutDetailError.visibility = View.GONE
            }
            "FAILED" -> {
                sheetBinding.chipDetailStatus.text = getString(R.string.status_failed)
                sheetBinding.chipDetailStatus.setChipBackgroundColorResource(R.color.status_failed_container)
                sheetBinding.chipDetailStatus.setTextColor(ContextCompat.getColor(context, R.color.status_failed_text))
                sheetBinding.layoutDetailOutput.visibility = View.GONE
                sheetBinding.layoutPositiveActions.visibility = View.GONE
                sheetBinding.layoutDetailError.visibility = View.VISIBLE
                sheetBinding.tvDetailError.text = job.errorMessage ?: getString(R.string.error_generic)
            }
            "CANCELLED" -> {
                sheetBinding.chipDetailStatus.text = getString(R.string.status_cancelled)
                sheetBinding.chipDetailStatus.setChipBackgroundColorResource(R.color.status_cancelled_container)
                sheetBinding.chipDetailStatus.setTextColor(ContextCompat.getColor(context, R.color.status_cancelled_text))
                sheetBinding.layoutDetailOutput.visibility = View.GONE
                sheetBinding.layoutPositiveActions.visibility = View.GONE
                sheetBinding.layoutDetailError.visibility = View.GONE
            }
            else -> {
                sheetBinding.chipDetailStatus.text = job.status
                sheetBinding.layoutDetailOutput.visibility = View.GONE
                sheetBinding.layoutPositiveActions.visibility = View.GONE
                sheetBinding.layoutDetailError.visibility = View.GONE
            }
        }

        sheetBinding.btnDetailDanger.text = getString(R.string.action_delete_record)
        sheetBinding.btnDetailDanger.setOnClickListener {
            dialog.dismiss()
            confirmDeleteJob(job)
        }

        sheetBinding.btnCloseDetail.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openMediaFile(job: JobEntity) {
        val uriStr = job.outputUri ?: return
        try {
            val uri = Uri.parse(uriStr)
            val mimeType = if (job.mediaType == "IMAGE") "image/*" else "video/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.action_open_file)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), e.message ?: "Cannot open file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareMediaFile(job: JobEntity) {
        val uriStr = job.outputUri ?: return
        try {
            val uri = Uri.parse(uriStr)
            val mimeType = if (job.mediaType == "IMAGE") "image/*" else "video/*"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share_file)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), e.message ?: "Cannot share file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.historyJobs.collect { list ->
                    if (list.isEmpty()) {
                        binding.rvHistory.visibility = View.GONE
                        binding.layoutEmptyHistory.visibility = View.VISIBLE
                        binding.btnClearHistory.visibility = View.GONE
                    } else {
                        binding.rvHistory.visibility = View.VISIBLE
                        binding.layoutEmptyHistory.visibility = View.GONE
                        binding.btnClearHistory.visibility = View.VISIBLE
                        historyAdapter.submitList(list)
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
