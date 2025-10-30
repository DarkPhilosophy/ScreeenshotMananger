package com.ko.app.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ko.app.R
import com.ko.app.util.DebugLogger

private const val COLOR_DEBUG = "#AAAAAA"
private const val COLOR_INFO = "#00FF00"
private const val COLOR_WARNING = "#FFA500"
private const val COLOR_ERROR = "#FF0000"

class LogAdapter : ListAdapter<DebugLogger.LogEntry, LogAdapter.LogViewHolder>(LogDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val logText: TextView = itemView.findViewById(R.id.logText)

        fun bind(entry: DebugLogger.LogEntry) {
            logText.text = entry.getFormattedMessage()

            // Color code by log level
            val color = when (entry.level) {
                DebugLogger.LogLevel.DEBUG -> Color.parseColor(COLOR_DEBUG)
                DebugLogger.LogLevel.INFO -> Color.parseColor(COLOR_INFO)
                DebugLogger.LogLevel.WARNING -> Color.parseColor(COLOR_WARNING)
                DebugLogger.LogLevel.ERROR -> Color.parseColor(COLOR_ERROR)
            }
            logText.setTextColor(color)
        }
    }

    class LogDiffCallback : DiffUtil.ItemCallback<DebugLogger.LogEntry>() {
        override fun areItemsTheSame(oldItem: DebugLogger.LogEntry, newItem: DebugLogger.LogEntry): Boolean {
            return oldItem.timestamp == newItem.timestamp && oldItem.message == newItem.message
        }

        override fun areContentsTheSame(oldItem: DebugLogger.LogEntry, newItem: DebugLogger.LogEntry): Boolean {
            return oldItem == newItem
        }
    }
}
