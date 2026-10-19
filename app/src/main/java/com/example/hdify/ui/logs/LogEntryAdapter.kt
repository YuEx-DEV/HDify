package com.example.hdify.ui.logs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hdify.R
import com.example.hdify.databinding.ItemLogEntryBinding

data class LogEntry(
    val id: String,
    val line: String,
    val levelChar: Char,
    val isCrash: Boolean = false
)

class LogEntryAdapter : ListAdapter<LogEntry, LogEntryAdapter.LogViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LogViewHolder(private val binding: ItemLogEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: LogEntry) {
            binding.tvLogLevel.text = entry.levelChar.toString()
            binding.tvLogContent.text = entry.line

            val context = binding.root.context
            val color = when (entry.levelChar) {
                'E', 'C' -> ContextCompat.getColor(context, R.color.status_red)
                'W' -> ContextCompat.getColor(context, R.color.status_orange)
                'I' -> ContextCompat.getColor(context, R.color.status_green)
                else -> ContextCompat.getColor(context, R.color.md_theme_secondary)
            }
            binding.tvLogLevel.setTextColor(color)
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean =
            oldItem == newItem
    }
}
