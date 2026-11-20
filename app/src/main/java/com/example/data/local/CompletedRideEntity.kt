package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "completed_rides")
data class CompletedRideEntity(
    @PrimaryKey
    val id: String,
    val passengerName: String,
    val passengerPhone: String,
    val pickup: String,
    val destination: String,
    val vehicleCategory: String,
    val distanceKm: Double,
    val farePKR: Int,
    val captainName: String,
    val captainPhone: String,
    val vehiclePlate: String,
    val ratingGiven: Int = 5,
    val reviewGiven: String = "",
    val promoCode: String? = null,
    val discountPKR: Int = 0,
    val completedAt: Long = System.currentTimeMillis()
)
