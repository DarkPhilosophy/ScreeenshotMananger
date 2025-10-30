package com.ko.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "screenshots")
data class Screenshot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val filePath: String,

    val fileName: String,

    val fileSize: Long,

    val createdAt: Long,

    val deletionTimestamp: Long?,

    val isMarkedForDeletion: Boolean = false,

    val isKept: Boolean = false,

    val thumbnailPath: String? = null
)

