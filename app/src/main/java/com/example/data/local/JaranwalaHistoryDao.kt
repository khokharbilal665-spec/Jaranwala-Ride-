package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JaranwalaHistoryDao {

    @Query("SELECT * FROM completed_rides ORDER BY completedAt DESC")
    fun getAllCompletedRides(): Flow<List<CompletedRideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletedRide(ride: CompletedRideEntity)

    @Query("DELETE FROM completed_rides WHERE id = :id")
    suspend fun deleteCompletedRideById(id: String)

    @Query("DELETE FROM completed_rides")
    suspend fun clearAllCompletedRides()

    @Query("SELECT * FROM wedding_convoy_history ORDER BY createdAt DESC")
    fun getAllWeddingConvoyBookings(): Flow<List<WeddingConvoyHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeddingBooking(booking: WeddingConvoyHistoryEntity)

    @Query("DELETE FROM wedding_convoy_history WHERE id = :id")
    suspend fun deleteWeddingBookingById(id: String)

    @Query("DELETE FROM wedding_convoy_history")
    suspend fun clearAllWeddingBookings()
}
