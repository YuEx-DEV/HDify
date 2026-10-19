package com.example.hdify.ui.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.hdify.R
import com.example.hdify.data.database.JobEntity
import com.example.hdify.databinding.BottomSheetJobDetailBinding
import com.example.hdify.databinding.FragmentQueueBinding
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
class QueueFragment : Fragment() {

    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QueueViewModel by viewModels()
    private lateinit var queueAdapter: QueueAdapter
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        setupRecyclerView()
        observeQueue()
        animateCascadeEntrance(listOf(binding.tvQueueTitle))
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.queueRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.queueRoot.setPadding(systemBars.left, 0, systemBars.right, 0)
            val bottomNavReserve = (136 * resources.displayMetrics.density).toInt()
            binding.rvQueue.setPadding(
                binding.rvQueue.paddingLeft,
                binding.rvQueue.paddingTop,
                binding.rvQueue.paddingRight,
                systemBars.bottom + bottomNavReserve
            )
            insets
        }
    }

    private fun setupRecyclerView() {
        queueAdapter = QueueAdapter(
            onItemClicked = { job -> showJobDetailBottomSheet(job) },
            onCancelClicked = { job -> confirmCancelJob(job) }
        )
        binding.rvQueue.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = queueAdapter
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }
    }

    private fun confirmCancelJob(job: JobEntity) {
        val isProcessing = job.status == "PROCESSING"
        val title = if (isProcessing) getString(R.string.btn_cancel_job) + "?" else getString(R.string.action_cancel) + "?"
        val msg = if (isProcessing) {
            "Job #${job.id} is currently processing. Cancel and abort now?"
        } else {
            "Remove Job #${job.id} from queue?"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.cancelJob(job)
                Snackbar.make(binding.root, "Job #${job.id} cancelled", Snackbar.LENGTH_SHORT).show()
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

        var currentJob = job

        fun updateSheetUi(targetJob: JobEntity) {
            currentJob = targetJob
            val mediaLabel = if (targetJob.mediaType == "IMAGE") getString(R.string.media_image) else getString(R.string.media_video)
            sheetBinding.tvDetailTitle.text = getString(R.string.job_title_format, targetJob.id, mediaLabel)
            sheetBinding.tvDetailEngine.text = "${targetJob.engineName} • ${getString(R.string.job_scale_format, targetJob.scale)}"
            sheetBinding.ivDetailIcon.setImageResource(if (targetJob.mediaType == "IMAGE") R.drawable.ic_image else R.drawable.ic_video)

            val timeStr = dateFormat.format(Date(targetJob.createdAt))
            sheetBinding.tvDetailTime.text = timeStr
            sheetBinding.tvDetailSource.text = targetJob.sourceUri

            if (targetJob.status == "PROCESSING") {
                sheetBinding.chipDetailStatus.text = "${getString(R.string.status_processing)} (${targetJob.progress}%)"
                sheetBinding.chipDetailStatus.setChipBackgroundColorResource(R.color.status_processing_container)
                sheetBinding.chipDetailStatus.setTextColor(ContextCompat.getColor(context, R.color.status_processing_text))

                if (targetJob.mediaType == "VIDEO" && targetJob.totalFrames > 0) {
                    sheetBinding.layoutDetailFrames.visibility = View.VISIBLE
                    sheetBinding.tvDetailFrames.text = "${targetJob.checkpointFrame} / ${targetJob.totalFrames} (${targetJob.progress}%)"
                } else {
                    sheetBinding.layoutDetailFrames.visibility = View.GONE
                }
            } else {
                sheetBinding.chipDetailStatus.text = getString(R.string.status_waiting)
                sheetBinding.chipDetailStatus.setChipBackgroundColorResource(R.color.status_pending_container)
                sheetBinding.chipDetailStatus.setTextColor(ContextCompat.getColor(context, R.color.status_pending_text))
                sheetBinding.layoutDetailFrames.visibility = View.GONE
            }
        }

        updateSheetUi(job)

        val liveJobCollector = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeQueue.collect { list ->
                val updated = list.firstOrNull { it.id == job.id }
                if (updated != null) {
                    updateSheetUi(updated)
                }
            }
        }
        dialog.setOnDismissListener {
            liveJobCollector.cancel()
        }

        ViewCompat.setOnApplyWindowInsetsListener(sheetBinding.root) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBars.bottom + 24)
            insets
        }

        sheetBinding.layoutDetailOutput.visibility = View.GONE
        sheetBinding.layoutDetailError.visibility = View.GONE
        sheetBinding.layoutPositiveActions.visibility = View.GONE

        sheetBinding.btnDetailDanger.text = getString(R.string.btn_cancel_job)
        sheetBinding.btnDetailDanger.setIconResource(R.drawable.ic_close)
        sheetBinding.btnDetailDanger.setOnClickListener {
            dialog.dismiss()
            confirmCancelJob(currentJob)
        }

        sheetBinding.btnCloseDetail.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun observeQueue() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activeQueue.collect { list ->
                    if (list.isEmpty()) {
                        if (binding.rvQueue.visibility != View.GONE) binding.rvQueue.visibility = View.GONE
                        if (binding.layoutEmptyQueue.visibility != View.VISIBLE) binding.layoutEmptyQueue.visibility = View.VISIBLE
                    } else {
                        if (binding.rvQueue.visibility != View.VISIBLE) binding.rvQueue.visibility = View.VISIBLE
                        if (binding.layoutEmptyQueue.visibility != View.GONE) binding.layoutEmptyQueue.visibility = View.GONE
                        queueAdapter.submitList(list)
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
