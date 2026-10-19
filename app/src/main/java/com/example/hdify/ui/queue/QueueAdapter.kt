package com.example.hdify.ui.queue

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hdify.R
import com.example.hdify.data.database.JobEntity
import com.example.hdify.databinding.ItemQueueJobBinding

class QueueAdapter(
    private val onItemClicked: (JobEntity) -> Unit,
    private val onCancelClicked: (JobEntity) -> Unit
) : ListAdapter<JobEntity, QueueAdapter.QueueViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = ItemQueueJobBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QueueViewHolder(binding, onItemClicked, onCancelClicked)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val job = getItem(position)
            holder.bindProgress(job)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    class QueueViewHolder(
        private val binding: ItemQueueJobBinding,
        private val onItemClicked: (JobEntity) -> Unit,
        private val onCancelClicked: (JobEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var boundJob: JobEntity? = null

        init {
            binding.btnCancelJob.setOnClickListener {
                boundJob?.let { onCancelClicked(it) }
            }
            binding.root.setOnClickListener {
                boundJob?.let { onItemClicked(it) }
            }
        }

        fun bind(job: JobEntity) {
            boundJob = job
            val context = binding.root.context
            val mediaLabel = if (job.mediaType == "IMAGE") context.getString(R.string.media_image) else context.getString(R.string.media_video)
            binding.tvJobTitle.text = context.getString(R.string.job_title_format, job.id, mediaLabel)
            binding.tvJobInfo.text = "${job.engineName} • ${context.getString(R.string.job_scale_format, job.scale)}"

            val isProcessing = job.status == "PROCESSING"
            if (isProcessing) {
                binding.chipJobStatus.text = context.getString(R.string.status_processing)
                binding.chipJobStatus.setChipBackgroundColorResource(R.color.status_processing_container)
                binding.chipJobStatus.setTextColor(ContextCompat.getColor(context, R.color.status_processing_text))
            } else {
                binding.chipJobStatus.text = context.getString(R.string.status_waiting)
                binding.chipJobStatus.setChipBackgroundColorResource(R.color.status_pending_container)
                binding.chipJobStatus.setTextColor(ContextCompat.getColor(context, R.color.status_pending_text))
            }

            bindProgress(job)
        }

        fun bindProgress(job: JobEntity) {
            boundJob = job
            val context = binding.root.context
            val isProcessing = job.status == "PROCESSING"
            if (isProcessing) {
                if (binding.jobProgressIndicator.visibility != View.VISIBLE) {
                    binding.jobProgressIndicator.visibility = View.VISIBLE
                }
                if (job.progress <= 0) {
                    if (!binding.jobProgressIndicator.isIndeterminate) {
                        binding.jobProgressIndicator.isIndeterminate = true
                    }
                } else {
                    if (binding.jobProgressIndicator.isIndeterminate) {
                        binding.jobProgressIndicator.isIndeterminate = false
                    }
                    if (binding.jobProgressIndicator.progress != job.progress) {
                        binding.jobProgressIndicator.setProgressCompat(job.progress, false)
                    }
                }
                if (binding.tvProgressPercent.visibility != View.VISIBLE) {
                    binding.tvProgressPercent.visibility = View.VISIBLE
                }
                val percentStr = context.getString(R.string.status_progress_percent, job.progress)
                if (binding.tvProgressPercent.text != percentStr) {
                    binding.tvProgressPercent.text = percentStr
                }
            } else {
                if (binding.jobProgressIndicator.visibility != View.GONE) {
                    binding.jobProgressIndicator.visibility = View.GONE
                }
                if (binding.tvProgressPercent.visibility != View.VISIBLE) {
                    binding.tvProgressPercent.visibility = View.VISIBLE
                }
                val queuedStr = context.getString(R.string.status_queued)
                if (binding.tvProgressPercent.text != queuedStr) {
                    binding.tvProgressPercent.text = queuedStr
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<JobEntity>() {
        private const val PAYLOAD_PROGRESS = "PAYLOAD_PROGRESS"

        override fun areItemsTheSame(oldItem: JobEntity, newItem: JobEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: JobEntity, newItem: JobEntity): Boolean =
            oldItem == newItem

        override fun getChangePayload(oldItem: JobEntity, newItem: JobEntity): Any? {
            return if (oldItem.id == newItem.id && oldItem.status == newItem.status) {
                PAYLOAD_PROGRESS
            } else {
                null
            }
        }
    }
}
