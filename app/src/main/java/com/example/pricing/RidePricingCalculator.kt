package com.example.pricing

import com.example.data.AdminConfig
import com.example.data.VehicleCategory
import kotlin.math.roundToInt

/**
 * Dedicated pricing calculation module for JARANWALA RIDE.
 *
 * Computes transparent, itemized PKR fare estimates using route distance fetched
 * from the Location API ([LocationDistanceResult]), active [AdminConfig] rates,
 * optional peak surge, minimum fare protection, nearest-10-PKR rounding, and promo discounts.
 */
object RidePricingCalculator {

    private const val MINIMUM_DISCOUNTED_FARE_PKR = 30
    private const val MINIMUM_TRIP_DURATION_MINUTES = 3

    /**
     * Calculates a full itemized [RidePriceBreakdown] for a given [category] and [distanceKm].
     */
    fun calculateFareBreakdown(
        category: VehicleCategory,
        distanceKm: Double,
        adminConfig: AdminConfig = AdminConfig(),
        apiDurationMinutes: Int? = null,
        promoCode: String? = null,
        promoDiscountPKR: Int = 0,
        distanceSource: DistanceSource = DistanceSource.COORDINATE_HAVERSINE,
        surgeMultiplier: Double = 1.0,
        platformFeePKR: Int = 0
    ): RidePriceBreakdown {
        val safeDistanceKm = distanceKm.coerceAtLeast(0.0)
        val (baseFare, perKmRate) = rateForCategory(category, adminConfig)

        val distanceChargePKR = (safeDistanceKm * perKmRate).toInt()
        val preSurgeTotal = baseFare + distanceChargePKR
        val safeSurge = surgeMultiplier.coerceAtLeast(1.0)
        val surgeFeePKR = if (safeSurge > 1.0) {
            ((preSurgeTotal * (safeSurge - 1.0))).roundToInt()
        } else {
            0
        }

        val rawSubtotal = preSurgeTotal + surgeFeePKR + platformFeePKR.coerceAtLeast(0)
        val minimumFarePKR = baseFare
        val flooredTotal = maxOf(rawSubtotal, minimumFarePKR)
        val isMinApplied = rawSubtotal <= minimumFarePKR

        // Round up to nearest 10 PKR for clean cash-on-drop transactions
        val roundedSubtotal = roundUpToNearestTen(flooredTotal)

        val validDiscount = promoDiscountPKR.coerceAtLeast(0)
        val finalFare = if (validDiscount > 0) {
            (roundedSubtotal - validDiscount).coerceAtLeast(MINIMUM_DISCOUNTED_FARE_PKR)
        } else {
            roundedSubtotal
        }

        val etaMinutes = estimateDurationMinutes(
            distanceKm = safeDistanceKm,
            category = category,
            apiDurationMinutes = apiDurationMinutes
        )

        return RidePriceBreakdown(
            category = category,
            distanceKm = safeDistanceKm,
            estimatedDurationMinutes = etaMinutes,
            baseFarePKR = baseFare,
            perKmRatePKR = perKmRate,
            distanceChargePKR = distanceChargePKR,
            minimumFarePKR = minimumFarePKR,
            surgeMultiplier = safeSurge,
            surgeFeePKR = surgeFeePKR,
            platformFeePKR = platformFeePKR.coerceAtLeast(0),
            rawSubtotalPKR = rawSubtotal,
            roundedSubtotalPKR = roundedSubtotal,
            promoCode = promoCode?.takeIf { validDiscount > 0 },
            promoDiscountPKR = validDiscount,
            finalEstimatedFarePKR = finalFare,
            distanceSource = distanceSource,
            isMinimumFareApplied = isMinApplied
        )
    }

    /**
     * Calculates a [RidePriceBreakdown] directly from a fetched [LocationDistanceResult].
     */
    fun calculateFromLocationDistance(
        distanceResult: LocationDistanceResult,
        category: VehicleCategory,
        adminConfig: AdminConfig = AdminConfig(),
        promoCode: String? = null,
        promoDiscountPKR: Int = 0,
        surgeMultiplier: Double = 1.0,
        platformFeePKR: Int = 0
    ): RidePriceBreakdown {
        return calculateFareBreakdown(
            category = category,
            distanceKm = distanceResult.distanceKm,
            adminConfig = adminConfig,
            apiDurationMinutes = distanceResult.estimatedDurationMinutes,
            promoCode = promoCode,
            promoDiscountPKR = promoDiscountPKR,
            distanceSource = distanceResult.source,
            surgeMultiplier = surgeMultiplier,
            platformFeePKR = platformFeePKR
        )
    }

    /**
     * Generates price quotes for every [VehicleCategory] so the user can compare
     * Bike, Auto Rickshaw, and AC Car upfront before confirming their booking.
     */
    fun calculateAllCategoryQuotes(
        distanceKm: Double,
        adminConfig: AdminConfig = AdminConfig(),
        apiDurationMinutes: Int? = null,
        distanceSource: DistanceSource = DistanceSource.COORDINATE_HAVERSINE
    ): Map<VehicleCategory, RidePriceBreakdown> {
        return VehicleCategory.values().associateWith { cat ->
            calculateFareBreakdown(
                category = cat,
                distanceKm = distanceKm,
                adminConfig = adminConfig,
                apiDurationMinutes = apiDurationMinutes,
                distanceSource = distanceSource
            )
        }
    }

    /**
     * Estimates travel duration in minutes for the chosen vehicle category.
     * Uses driving duration from the Location API when available, adjusted for
     * local Jaranwala urban/rural vehicle speeds.
     */
    fun estimateDurationMinutes(
        distanceKm: Double,
        category: VehicleCategory,
        apiDurationMinutes: Int? = null
    ): Int {
        if (apiDurationMinutes != null && apiDurationMinutes > 0) {
            val factor = when (category) {
                VehicleCategory.BIKE -> 0.85 // Bikes weave past city market traffic faster
                VehicleCategory.RICKSHAW -> 1.15 // Rickshaws average slightly slower speeds
                VehicleCategory.CAR -> 1.0
            }
            return (apiDurationMinutes * factor).roundToInt().coerceAtLeast(MINIMUM_TRIP_DURATION_MINUTES)
        }

        val averageSpeedKmh = when (category) {
            VehicleCategory.BIKE -> 30.0
            VehicleCategory.RICKSHAW -> 22.0
            VehicleCategory.CAR -> 35.0
        }
        val travelMinutes = (distanceKm / averageSpeedKmh) * 60.0
        return (category.etaMinutes + travelMinutes).roundToInt().coerceAtLeast(MINIMUM_TRIP_DURATION_MINUTES)
    }

    fun rateForCategory(category: VehicleCategory, adminConfig: AdminConfig): Pair<Int, Int> {
        return when (category) {
            VehicleCategory.BIKE -> adminConfig.bikeBaseFare to adminConfig.bikePerKm
            VehicleCategory.RICKSHAW -> adminConfig.rickshawBaseFare to adminConfig.rickshawPerKm
            VehicleCategory.CAR -> adminConfig.carBaseFare to adminConfig.carPerKm
        }
    }

    fun roundUpToNearestTen(amountPKR: Int): Int {
        if (amountPKR <= 0) return 0
        return ((amountPKR + 9) / 10) * 10
    }
}
