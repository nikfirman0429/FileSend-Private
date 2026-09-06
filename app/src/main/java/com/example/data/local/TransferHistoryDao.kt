package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TransferStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferHistoryDao {

    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<TransferHistoryEntity>>

    @Query("SELECT * FROM transfer_history WHERE id = :id")
    suspend fun getById(id: Long): TransferHistoryEntity?

    @Query("SELECT * FROM transfer_history WHERE code = :code ORDER BY timestamp DESC LIMIT 1")
    suspend fun getByCode(code: String): TransferHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: TransferHistoryEntity): Long

    @Update
    suspend fun update(history: TransferHistoryEntity)

    @Query("UPDATE transfer_history SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TransferStatus)

    @Delete
    suspend fun delete(history: TransferHistoryEntity)

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transfer_history")
    suspend fun clearAll()
}
