package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.TransferDirection
import com.example.data.model.TransferStatus

@Entity(
    tableName = "transfer_history",
    indices = [
        Index(value = ["code"]),
        Index(value = ["timestamp"])
    ]
)
data class TransferHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val fileName: String,
    val fileExtension: String,
    val mimeType: String,
    val fileSize: Long,
    val direction: TransferDirection,
    val checksum: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: TransferStatus,
    val localFilePath: String? = null,
    val shareUrl: String? = null,
    val expiresAt: Long? = null,
    val errorMessage: String? = null
)
