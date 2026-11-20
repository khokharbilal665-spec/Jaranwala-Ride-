package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wedding_convoy_history")
data class WeddingConvoyHistoryEntity(
    @PrimaryKey
    val id: String,
    val isBaratConvoy: Boolean,
    val customerName: String,
    val customerPhone: String,
    val primaryCar: String,
    val carCount: Int,
    val convoyMix: String,
    val decorationTier: String,
    val durationHours: Int,
    val durationLabel: String,
    val eventDate: String,
    val eventTime: String,
    val pickupLocation: String,
    val venueLocation: String,
    val totalCostPKR: Int,
    val notes: String = "",
    val status: String = "CONFIRMED",
    val createdAt: Long = System.currentTimeMillis()
)
