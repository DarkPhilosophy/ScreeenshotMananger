package com.ko.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.ko.app.R
import com.ko.app.data.entity.Screenshot
import java.io.File
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

private const val BYTES_IN_KB = 1024
private const val BYTES_IN_MB = 1024 * 1024
private const val HOURS_IN_DAY = 24L
private const val MINUTES_IN_HOUR = 60L
private const val SECONDS_IN_MINUTE = 60L

class ScreenshotAdapter(
    private val onKeepClick: (Screenshot) -> Unit,
    private val onDeleteClick: (Screenshot) -> Unit
) : ListAdapter<Screenshot, ScreenshotAdapter.ScreenshotViewHolder>(ScreenshotDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScreenshotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_screenshot, parent, false)
        return ScreenshotViewHolder(view, onKeepClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ScreenshotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ScreenshotViewHolder(
        itemView: View,
        private val onKeepClick: (Screenshot) -> Unit,
        private val onDeleteClick: (Screenshot) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val thumbnail: ImageView = itemView.findViewById(R.id.screenshotThumbnail)
        private val fileName: TextView = itemView.findViewById(R.id.fileName)
        private val fileSize: TextView = itemView.findViewById(R.id.fileSize)
        private val timeRemaining: TextView = itemView.findViewById(R.id.timeRemaining)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val btnKeep: MaterialButton = itemView.findViewById(R.id.btnKeep)
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)

        fun bind(screenshot: Screenshot) {
            fileName.text = screenshot.fileName
            fileSize.text = formatFileSize(screenshot.fileSize)

            Glide.with(itemView.context)
                .load(File(screenshot.filePath))
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(thumbnail)

            when {
                screenshot.isKept -> {
                    timeRemaining.visibility = View.GONE
                    statusText.visibility = View.VISIBLE
                    statusText.text = "Kept"
                    btnKeep.visibility = View.GONE
                    btnDelete.visibility = View.VISIBLE
                }
                screenshot.isMarkedForDeletion && screenshot.deletionTimestamp != null -> {
                    val remaining = screenshot.deletionTimestamp - System.currentTimeMillis()
                    if (remaining > 0) {
                        timeRemaining.visibility = View.VISIBLE
                        timeRemaining.text = "Deletes in ${formatTimeRemaining(remaining)}"
                        statusText.visibility = View.GONE
                        btnKeep.visibility = View.VISIBLE
                        btnDelete.visibility = View.VISIBLE
                    } else {
                        timeRemaining.visibility = View.VISIBLE
                        timeRemaining.text = "Expired"
                        statusText.visibility = View.GONE
                        btnKeep.visibility = View.GONE
                        btnDelete.visibility = View.VISIBLE
                    }
                }
                else -> {
                    timeRemaining.visibility = View.GONE
                    statusText.visibility = View.VISIBLE
                    statusText.text = "Unmarked"
                    btnKeep.visibility = View.VISIBLE
                    btnDelete.visibility = View.VISIBLE
                }
            }

            btnKeep.setOnClickListener { onKeepClick(screenshot) }
            btnDelete.setOnClickListener { onDeleteClick(screenshot) }
        }

        private fun formatFileSize(bytes: Long): String {
            val df = DecimalFormat("#.##")
            return when {
                bytes < BYTES_IN_KB -> "$bytes B"
                bytes < BYTES_IN_MB -> "${df.format(bytes / BYTES_IN_KB.toDouble())} KB"
                else -> "${df.format(bytes / BYTES_IN_MB.toDouble())} MB"
            }
        }

        private fun formatTimeRemaining(millis: Long): String {
            val days = TimeUnit.MILLISECONDS.toDays(millis)
            val hours = TimeUnit.MILLISECONDS.toHours(millis) % HOURS_IN_DAY
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % MINUTES_IN_HOUR
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % SECONDS_IN_MINUTE

            return when {
                days > 0 -> "${days}d ${hours}h"
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
        }
    }

    class ScreenshotDiffCallback : DiffUtil.ItemCallback<Screenshot>() {
        override fun areItemsTheSame(oldItem: Screenshot, newItem: Screenshot): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Screenshot, newItem: Screenshot): Boolean {
            return oldItem == newItem
        }
    }
}
