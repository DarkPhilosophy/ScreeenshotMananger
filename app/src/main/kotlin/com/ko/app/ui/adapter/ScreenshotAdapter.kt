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
import com.ko.app.util.DebugLogger
import com.ko.app.util.TimeUtils
import java.io.File
import java.text.DecimalFormat

private const val BYTES_IN_KB = 1024
private const val BYTES_IN_MB = 1024 * 1024

class ScreenshotAdapter(
    private val onKeepClick: (Screenshot) -> Unit,
    private val onDeleteClick: (Screenshot) -> Unit,
    private val onImageClick: (Screenshot) -> Unit
) : ListAdapter<Screenshot, ScreenshotAdapter.ScreenshotViewHolder>(ScreenshotDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScreenshotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_screenshot, parent, false)
        return ScreenshotViewHolder(view, onKeepClick, onDeleteClick, onImageClick)
    }

    override fun onBindViewHolder(holder: ScreenshotViewHolder, position: Int) {
        val item = getItem(position)
        DebugLogger.info("ScreenshotAdapter", "Binding item at position $position: ${item.fileName}")
        holder.bind(item)
    }

    class ScreenshotViewHolder(
        itemView: View,
        private val onKeepClick: (Screenshot) -> Unit,
        private val onDeleteClick: (Screenshot) -> Unit,
        private val onImageClick: (Screenshot) -> Unit
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

            val file = File(screenshot.filePath)
            val actualFileSize = if (file.exists()) file.length() else screenshot.fileSize
            fileSize.text = formatFileSize(actualFileSize)

            Glide.with(itemView.context)
                .load(file)
                .override(200, 200)
                .centerCrop()
                .dontAnimate()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(thumbnail)

            thumbnail.setOnClickListener {
                onImageClick(screenshot)
            }

            when {
                screenshot.isKept -> {
                    timeRemaining.visibility = View.GONE
                    statusText.visibility = View.VISIBLE
                    statusText.text = itemView.context.getString(R.string.kept)
                    btnKeep.visibility = View.GONE
                    btnDelete.visibility = View.VISIBLE
                }
                screenshot.isMarkedForDeletion && screenshot.deletionTimestamp != null -> {
                    val remaining = screenshot.deletionTimestamp - System.currentTimeMillis()
                    if (remaining > 0) {
                        timeRemaining.visibility = View.VISIBLE
                        timeRemaining.text = itemView.context.getString(
                            R.string.deletes_in,
                            TimeUtils.formatTimeRemaining(remaining)
                        )
                        statusText.visibility = View.GONE
                        btnKeep.visibility = View.VISIBLE
                        btnDelete.visibility = View.VISIBLE
                    } else {
                        timeRemaining.visibility = View.VISIBLE
                        timeRemaining.text = itemView.context.getString(R.string.expired)
                        statusText.visibility = View.GONE
                        btnKeep.visibility = View.GONE
                        btnDelete.visibility = View.VISIBLE
                    }
                }
                else -> {
                    timeRemaining.visibility = View.GONE
                    statusText.visibility = View.VISIBLE
                    statusText.text = itemView.context.getString(R.string.unmarked)
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
