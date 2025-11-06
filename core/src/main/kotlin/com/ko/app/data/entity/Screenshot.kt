package com.ko.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "screenshots",
    indices = [Index(value = ["filePath"], unique = true)]
)
data class Screenshot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val filePath: String,

    val fileName: String,

    val fileSize: Long,

    val createdAt: Long,

    val deletionTimestamp: Long?,

    val isKept: Boolean = false,

    val deletionWorkId: String? = null,

    val thumbnailPath: String? = null,

    // New: store MediaStore/content URI as string for robust scoped-storage handling
    val contentUri: String? = null
)
