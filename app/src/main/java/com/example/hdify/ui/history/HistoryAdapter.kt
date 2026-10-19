package com.example.hdify.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hdify.R
import com.example.hdify.data.database.JobEntity
import com.example.hdify.databinding.ItemHistoryJobBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onItemClicked: (JobEntity) -> Unit,
    private val onDeleteClicked: (JobEntity) -> Unit
) : ListAdapter<JobEntity, HistoryAdapter.HistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryJobBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding, onItemClicked, onDeleteClicked)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HistoryViewHolder(
        private val binding: ItemHistoryJobBinding,
        private val onItemClicked: (JobEntity) -> Unit,
        private val onDeleteClicked: (JobEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault())

        fun bind(job: JobEntity) {
            val context = binding.root.context
            val mediaLabel = if (job.mediaType == "IMAGE") context.getString(R.string.media_image) else context.getString(R.string.media_video)
            binding.tvHistoryTitle.text = context.getString(R.string.job_title_format, job.id, mediaLabel)

            val timeStr = dateFormat.format(Date(job.completedAt ?: job.createdAt))
            binding.tvHistoryInfo.text = "${job.engineName} • ${context.getString(R.string.job_scale_format, job.scale)} • $timeStr"

            when (job.status) {
                "COMPLETED" -> {
                    binding.chipHistoryStatus.text = context.getString(R.string.status_completed)
                    binding.chipHistoryStatus.setChipBackgroundColorResource(R.color.status_completed_container)
                    binding.chipHistoryStatus.setTextColor(ContextCompat.getColor(context, R.color.status_completed_text))
                    binding.tvHistoryError.visibility = View.GONE
                    if (!job.outputUri.isNullOrEmpty()) {
                        binding.tvHistoryOutput.visibility = View.VISIBLE
                        binding.tvHistoryOutput.text = "Output: ${job.outputUri}"
                    } else {
                        binding.tvHistoryOutput.visibility = View.GONE
                    }
                }
                "FAILED" -> {
                    binding.chipHistoryStatus.text = context.getString(R.string.status_failed)
                    binding.chipHistoryStatus.setChipBackgroundColorResource(R.color.status_failed_container)
                    binding.chipHistoryStatus.setTextColor(ContextCompat.getColor(context, R.color.status_failed_text))
                    binding.tvHistoryError.visibility = View.VISIBLE
                    binding.tvHistoryError.text = job.errorMessage ?: context.getString(R.string.error_generic)
                    binding.tvHistoryOutput.visibility = View.GONE
                }
                "CANCELLED" -> {
                    binding.chipHistoryStatus.text = context.getString(R.string.status_cancelled)
                    binding.chipHistoryStatus.setChipBackgroundColorResource(R.color.status_cancelled_container)
                    binding.chipHistoryStatus.setTextColor(ContextCompat.getColor(context, R.color.status_cancelled_text))
                    binding.tvHistoryError.visibility = View.GONE
                    binding.tvHistoryOutput.visibility = View.GONE
                }
                else -> {
                    binding.chipHistoryStatus.text = job.status
                    binding.chipHistoryStatus.setChipBackgroundColorResource(R.color.status_cancelled_container)
                    binding.chipHistoryStatus.setTextColor(ContextCompat.getColor(context, R.color.status_cancelled_text))
                    binding.tvHistoryError.visibility = View.GONE
                    binding.tvHistoryOutput.visibility = View.GONE
                }
            }

            binding.btnDeleteHistory.setOnClickListener {
                onDeleteClicked(job)
            }

            binding.root.setOnClickListener {
                onItemClicked(job)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<JobEntity>() {
        override fun areItemsTheSame(oldItem: JobEntity, newItem: JobEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: JobEntity, newItem: JobEntity): Boolean =
            oldItem == newItem
    }
}
