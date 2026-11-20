package com.example.pricing

import com.example.data.VehicleCategory

/**
 * Indicates how the route distance between pickup and destination was obtained.
 */
enum class DistanceSource(
    val label: String,
    val urduLabel: String,
    val isLiveApi: Boolean
) {
    LOCATION_API_OSRM(
        label = "Live Road API (OSRM)",
        urduLabel = "لائیو روڈ API (OSRM)",
        isLiveApi = true
    ),
    COORDINATE_HAVERSINE(
        label = "GPS Coordinate Route",
        urduLabel = "جی پی ایس کوآرڈینیٹ فاصلہ",
        isLiveApi = false
    ),
    LOCAL_FALLBACK(
        label = "Estimated City Distance",
        urduLabel = "تخمینی شہری فاصلہ",
        isLiveApi = false
    )
}

/**
 * Distance and duration fetched from the Location/Routing API (or coordinate fallback).
 */
data class LocationDistanceResult(
    val pickupName: String,
    val destinationName: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val destinationLat: Double,
    val destinationLng: Double,
    val distanceKm: Double,
    val estimatedDurationMinutes: Int,
    val source: DistanceSource,
    val resolvedViaGeocoder: Boolean = false,
    val fetchedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Complete itemized fare breakdown returned by [RidePricingCalculator]
 * and displayed to the passenger before confirming a ride booking.
 */
data class RidePriceBreakdown(
    val category: VehicleCategory,
    val distanceKm: Double,
    val estimatedDurationMinutes: Int,
    val baseFarePKR: Int,
    val perKmRatePKR: Int,
    val distanceChargePKR: Int,
    val minimumFarePKR: Int,
    val surgeMultiplier: Double = 1.0,
    val surgeFeePKR: Int = 0,
    val platformFeePKR: Int = 0,
    val rawSubtotalPKR: Int,
    val roundedSubtotalPKR: Int,
    val promoCode: String? = null,
    val promoDiscountPKR: Int = 0,
    val finalEstimatedFarePKR: Int,
    val distanceSource: DistanceSource = DistanceSource.COORDINATE_HAVERSINE,
    val isMinimumFareApplied: Boolean = false,
    val currency: String = "PKR"
)
