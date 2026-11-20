package com.example.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppLanguage(val label: String, val urduLabel: String) {
    BILINGUAL("Bilingual (Eng + اردو)", "دو لسانی"),
    ENGLISH("English Only", "صرف انگریزی"),
    URDU("اردو (Urdu Only)", "صرف اردو")
}

enum class UserRole(val title: String, val urduTitle: String) {
    PASSENGER("Passenger / Rider", "سواری"),
    DRIVER("Captain / Driver", "کپتان"),
    ADMIN("Administrator", "ایڈمن")
}

data class UserProfile(
    val uid: String,
    val phoneNumber: String,
    val name: String,
    val emergencyContactNumber: String = "",
    val role: UserRole = UserRole.PASSENGER,
    val email: String? = null,
    val photoUrl: String? = null,
    val isVerified: Boolean = true,
    val isGuest: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val cnic: String? = null,
    val licenseNo: String? = null,
    val vehicleCategory: VehicleCategory? = null,
    val vehicleModel: String? = null,
    val plateNo: String? = null
)

data class DriverRegDetails(
    val cnic: String,
    val licenseNo: String,
    val vehicleCategory: VehicleCategory,
    val vehicleModel: String,
    val plateNo: String
)

enum class VehicleCategory(
    val id: String,
    val title: String,
    val urduTitle: String,
    val description: String,
    val urduDescription: String,
    val baseFarePKR: Int,
    val perKmRatePKR: Int,
    val capacity: Int,
    val etaMinutes: Int
) {
    BIKE(
        id = "BIKE",
        title = "Bike Ride",
        urduTitle = "موٹر سائیکل سواری",
        description = "Fast, affordable solo ride for Jaranwala city",
        urduDescription = "تیز اور سستی انفرادی سواری",
        baseFarePKR = 70,
        perKmRatePKR = 25,
        capacity = 1,
        etaMinutes = 3
    ),
    RICKSHAW(
        id = "RICKSHAW",
        title = "Auto Rickshaw",
        urduTitle = "چنگ چی / آٹو رکشہ",
        description = "Traditional comfortable ride for families & shopping",
        urduDescription = "خاندان اور بازار کی خریداری کے لیے بہترین",
        baseFarePKR = 120,
        perKmRatePKR = 35,
        capacity = 3,
        etaMinutes = 5
    ),
    CAR(
        id = "CAR",
        title = "Car Ride (AC)",
        urduTitle = "کار / اے سی سواری",
        description = "Comfortable AC hatchback or sedan ride",
        urduDescription = "مکمل اے سی پرسکون کار سواری",
        baseFarePKR = 220,
        perKmRatePKR = 60,
        capacity = 4,
        etaMinutes = 7
    );

    val icon: ImageVector
        get() = when (this) {
            BIKE -> Icons.Default.DirectionsBike
            RICKSHAW -> Icons.Default.ElectricRickshaw
            CAR -> Icons.Default.DirectionsCar
        }
}

enum class RideStatus(val display: String, val urduDisplay: String) {
    IDLE("Idle", "غیر فعال"),
    SEARCHING("Searching for Captain...", "قریبی کپتان کی تلاش..."),
    ACCEPTED("Captain Accepted", "کپتان نے قبول کر لیا"),
    DRIVER_ARRIVED("Captain Arrived at Pickup", "کپتان پک اپ پوائنٹ پر پہنچ گئے"),
    IN_PROGRESS("Trip in Progress", "سفر جاری ہے"),
    COMPLETED("Ride Completed", "سواری مکمل ہو گئی"),
    CANCELLED("Cancelled", "منسوخ ہو گئی")
}

data class RideBooking(
    val id: String,
    val passengerId: String,
    val passengerName: String,
    val passengerPhone: String,
    val pickup: String,
    val destination: String,
    val vehicleCategory: VehicleCategory,
    val estimatedDistanceKm: Double,
    val estimatedFarePKR: Int,
    val status: RideStatus,
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val vehiclePlate: String? = null,
    val driverRating: Double = 4.8,
    val ratingGiven: Int = 0,
    val reviewGiven: String = "",
    val promoCode: String? = null,
    val discountPKR: Int = 0,
    val isDemoData: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val driverLat: Double? = null,
    val driverLng: Double? = null,
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
    val destLat: Double? = null,
    val destLng: Double? = null,
    val paymentMethod: String = "CASH",
    val paymentStatus: String = "PENDING",
    val serviceMode: String = "RIDE", // "RIDE" or "PARCEL"
    val isParcelDelivery: Boolean = false,
    val parcelDescription: String = "",
    val parcelWeightKg: String = "Up to 5 kg",
    val recipientName: String = "",
    val recipientPhone: String = "",
    val codAmountPKR: Int = 0
)

enum class ScheduledRideStatus(val display: String, val urduDisplay: String) {
    SCHEDULED("Scheduled", "طے شدہ سواری"),
    DISPATCHED("Captain Dispatched", "کپتان روانہ"),
    COMPLETED("Completed", "مکمل"),
    CANCELLED("Cancelled", "منسوخ")
}

data class ScheduledRide(
    val id: String,
    val passengerId: String,
    val passengerName: String,
    val passengerPhone: String,
    val pickup: String,
    val destination: String,
    val vehicleCategory: VehicleCategory,
    val distanceKm: Double,
    val estimatedFarePKR: Int,
    val scheduledForMillis: Long,
    val scheduledDateDisplay: String,
    val scheduledTimeDisplay: String,
    val notes: String = "",
    val promoCode: String? = null,
    val discountPKR: Int = 0,
    val status: ScheduledRideStatus = ScheduledRideStatus.SCHEDULED,
    val driverId: String? = null,
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
    val destLat: Double? = null,
    val destLng: Double? = null,
    val paymentMethod: String = "CASH",
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun toFirestoreMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "scheduleId" to id,
            "passengerId" to passengerId,
            "passengerName" to passengerName,
            "passengerPhone" to passengerPhone,
            "pickupLocation" to pickup,
            "destinationLocation" to destination,
            "rideType" to vehicleCategory.name,
            "distanceKm" to distanceKm,
            "estimatedFarePKR" to estimatedFarePKR.toLong(),
            "scheduledForMillis" to scheduledForMillis,
            "scheduledDateDisplay" to scheduledDateDisplay,
            "scheduledTimeDisplay" to scheduledTimeDisplay,
            "notes" to notes,
            "discountPKR" to discountPKR.toLong(),
            "status" to status.name,
            "paymentMethod" to paymentMethod.ifBlank { "CASH" },
            "city" to "Jaranwala",
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        promoCode?.let { if (it.isNotBlank()) map["promoCode"] = it }
        pickupLat?.let { map["pickupLat"] = it }
        pickupLng?.let { map["pickupLng"] = it }
        destLat?.let { map["destLat"] = it }
        destLng?.let { map["destLng"] = it }
        return map
    }
}

sealed class ScheduleRideResult {
    data class Success(val scheduledRide: ScheduledRide) : ScheduleRideResult()
    data class Error(val message: String, val urduMessage: String) : ScheduleRideResult()
}

data class SavedPlace(
    val id: String,
    val label: String,
    val urduLabel: String,
    val address: String,
    val iconType: String = "HOME", // "HOME", "WORK", "SHOP", "COLLEGE", "STATION", "FAVORITE"
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "label" to label,
            "urduLabel" to urduLabel,
            "address" to address,
            "iconType" to iconType,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    }
}

data class PromoCode(
    val code: String,
    val discountPercent: Int = 0,
    val flatDiscountPKR: Int = 0,
    val maxDiscountPKR: Int = 100,
    val description: String,
    val urduDescription: String
)

sealed class PromoValidationResult {
    data class Success(
        val promo: PromoCode,
        val discountAmountPKR: Int,
        val finalFarePKR: Int,
        val message: String,
        val urduMessage: String
    ) : PromoValidationResult()

    data class Error(
        val message: String,
        val urduMessage: String
    ) : PromoValidationResult()
}

data class DriverProfile(
    val id: String,
    val name: String,
    val phone: String,
    val cnic: String,
    val licenseNo: String,
    val vehicleCategory: VehicleCategory,
    val vehicleModel: String,
    val plateNo: String,
    val isApproved: Boolean = true,
    val isOnline: Boolean = true,
    val rating: Double = 4.9,
    val todayEarningsPKR: Int = 1850,
    val weeklyEarningsPKR: Int = 12400,
    val totalTrips: Int = 42,
    val isDemoData: Boolean = false
)

enum class WeddingCarType(val modelName: String, val basePricePKR: Int, val description: String) {
    PRADO_TX("Toyota Prado TX (White)", 28000, "Elite VIP SUV with full executive presence"),
    FORTUNER_SIGMA("Toyota Fortuner Sigma 4", 22000, "Bold luxury SUV with high road clearance"),
    CIVIC_RS("Honda Civic RS Turbo (White)", 16000, "Sporty luxury sedan with premium interior"),
    COROLLA_GRANDE("Toyota Corolla Altis Grande", 13000, "Elegant classic bridal car choice"),
    AUDI_A6("Audi A6 Luxury Limousine", 38000, "Ultra luxury German sedan with royal flair")
}

enum class ConvoyMixType(val title: String, val urduTitle: String, val multiplierPerCarPKR: Int) {
    STANDARD_SEDANS("Toyota Corolla / Yaris Fleet", "ٹویوٹا کرولا و یارس بیڑا", 13000),
    EXECUTIVE_MIX("Civic RS + Grande Fleet", "ہونڈا سیوک و گرینڈے بیڑا", 16000),
    ROYAL_VIP("Prado Lead + Fortuner & Hiace Van", "شاہی وی آئی پی پراڈو، فارچونر و ہائی ایس", 22000)
}

enum class DecorationTier(val title: String, val urduTitle: String, val additionalPricePKR: Int) {
    STANDARD("Standard Garland & Ribbons", "معیاری پھول اور ربن", 3500),
    ROYAL_ROSES("Royal Fresh Red Roses & Lilies", "شاہی تازہ گلاب اور موتیا", 7500),
    VIP_DELUXE("VIP Exotic Orchids & Satin Deluxe", "وی آئی پی درآمدی آرکڈ ڈیزائن", 12000)
}

enum class WeddingConvoyDuration(
    val hours: Int,
    val title: String,
    val urduTitle: String,
    val multiplier: Double
) {
    HALF_DAY_4H(4, "4 Hours (Reception)", "۴ گھنٹے (مختصر تقریب)", 0.7),
    FULL_BARAT_8H(8, "8 Hours (Standard Barat)", "۸ گھنٹے (مکمل بارات)", 1.0),
    EXTENDED_12H(12, "12 Hours (All-Day Event)", "۱۲ گھنٹے (پورے دن کی بارات)", 1.4),
    MULTI_DAY_24H(24, "24 Hours (Multi-Day / Mehndi+Barat)", "۲۴ گھنٹے (مہندی و بارات)", 2.2)
}

enum class ConvoyDecorationPreference(
    val id: String,
    val title: String,
    val urduTitle: String,
    val description: String,
    val additionalPricePKR: Int
) {
    FRESH_ROSES(
        id = "FRESH_ROSES",
        title = "Fresh Red Roses & Jasmine",
        urduTitle = "روایتی سرخ گلاب اور موتیا",
        description = "Fresh fragrant local rose garlands, bonnet spray & ribbons",
        additionalPricePKR = 7500
    ),
    ROYAL_ORCHIDS(
        id = "ROYAL_ORCHIDS",
        title = "Royal White Lilies & Imported Orchids",
        urduTitle = "شاہی سفید للی اور درآمدی آرکڈ",
        description = "Imported white lilies, orchids & luxury satin styling",
        additionalPricePKR = 14000
    ),
    SATIN_RIBBONS(
        id = "SATIN_RIBBONS",
        title = "Minimalist Satin Ribbon & Bows",
        urduTitle = "سادہ ساٹن ربن اور بونٹ بو ڈیزائن",
        description = "Clean elegant door-handle bows and bonnet cross-ribbon",
        additionalPricePKR = 3500
    ),
    NIGHT_LED_GLOW(
        id = "NIGHT_LED_GLOW",
        title = "Night Glow LED & Fairy Lights",
        urduTitle = "رات کے لیے ایل ای ڈی لائٹنگ و قمقمے",
        description = "Battery-powered ambient warm LED fairy lights along convoys",
        additionalPricePKR = 9500
    ),
    CHAUFFEUR_ONLY(
        id = "CHAUFFEUR_ONLY",
        title = "VIP Chauffeur-Only (No Floral)",
        urduTitle = "سادہ کاریں بغیر پھولوں کے (باصلاحیت ڈرائیور)",
        description = "Polished clean luxury fleet with uniformed drivers",
        additionalPricePKR = 0
    )
}

data class WeddingBooking(
    val id: String,
    val isBaratConvoy: Boolean,
    val customerName: String,
    val customerPhone: String,
    val primaryCar: String,
    val carCount: Int = 1,
    val convoyMix: ConvoyMixType = ConvoyMixType.STANDARD_SEDANS,
    val decorationTier: DecorationTier = DecorationTier.ROYAL_ROSES,
    val bookingDurationHours: Int = 8,
    val durationLabel: String = "8 Hours (Standard Barat)",
    val decorationPreference: String = "Fresh Red Roses & Jasmine",
    val escortOptions: List<String> = emptyList(),
    val eventDate: String,
    val eventTime: String,
    val pickupLocation: String,
    val venueLocation: String,
    val totalCostPKR: Int,
    val notes: String = "",
    val status: String = "CONFIRMED",
    val userId: String = "",
    val isDemoData: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toFirestoreMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "bookingId" to id,
            "id" to id,
            "serviceType" to if (isBaratConvoy) "BARAT_CONVOY" else "WEDDING_CAR",
            "isBaratConvoy" to isBaratConvoy,
            "customerName" to customerName,
            "customerPhone" to customerPhone,
            "primaryCar" to primaryCar,
            "carCount" to carCount,
            "convoyMix" to convoyMix.name,
            "durationHours" to bookingDurationHours,
            "durationLabel" to durationLabel,
            "decorationPreference" to decorationPreference,
            "eventDate" to eventDate,
            "eventTime" to eventTime,
            "pickupLocation" to pickupLocation,
            "venueLocation" to venueLocation,
            "totalCostPKR" to totalCostPKR,
            "notes" to notes,
            "status" to status,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "city" to "Jaranwala"
        )
        if (userId.isNotBlank()) map["userId"] = userId
        if (escortOptions.isNotEmpty()) map["escortOptions"] = escortOptions
        return map
    }
}

data class Complaint(
    val id: String,
    val rideId: String,
    val passengerName: String,
    val category: String,
    val urduCategory: String,
    val message: String,
    val status: String = "Under Review",
    val isDemoData: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class AdminConfig(
    val bikeBaseFare: Int = 70,
    val bikePerKm: Int = 25,
    val rickshawBaseFare: Int = 120,
    val rickshawPerKm: Int = 35,
    val carBaseFare: Int = 220,
    val carPerKm: Int = 60,
    val platformCommissionPercent: Int = 10
    // Notice: adminPin is NOT hardcoded here as plaintext. It is verified securely in JaranwalaRepository.
)

data class JaranwalaLocation(
    val name: String,
    val urduName: String,
    val category: String,
    val distanceMultiplier: Double // Relative distance factor from city center
)

/**
 * Firestore Data Model for storing ride request details in Jaranwala city service.
 * Supports Firestore serialization, zero-trust security rule compliance, and document ID mapping.
 */
@com.google.firebase.firestore.IgnoreExtraProperties
data class RideRequestFirestore(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val passengerId: String = "",
    val passengerName: String = "",
    val passengerPhone: String = "",
    val pickupLocation: String = "",
    val destinationLocation: String = "",
    val rideType: String = "RICKSHAW", // BIKE, RICKSHAW, CAR
    val vehicleCategoryTitle: String = "Auto Rickshaw",
    val estimatedFarePKR: Long = 0L,
    val distanceKm: Double = 0.0,
    val status: String = "SEARCHING", // SEARCHING, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val city: String = "Jaranwala",
    val promoCode: String? = null,
    val discountPKR: Long = 0L,
    val driverLat: Double? = null,
    val driverLng: Double? = null,
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
    val destLat: Double? = null,
    val destLng: Double? = null,
    val paymentMethod: String = "CASH",
    val paymentStatus: String = "PENDING",
    val serviceMode: String = "RIDE",
    val isParcelDelivery: Boolean = false,
    val parcelDescription: String = "",
    val parcelWeightKg: String = "Up to 5 kg",
    val recipientName: String = "",
    val recipientPhone: String = "",
    val codAmountPKR: Long = 0L,
    @com.google.firebase.firestore.ServerTimestamp val createdAt: com.google.firebase.Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: com.google.firebase.Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "passengerId" to passengerId,
            "passengerName" to passengerName,
            "passengerPhone" to passengerPhone,
            "pickupLocation" to pickupLocation,
            "destinationLocation" to destinationLocation,
            "rideType" to rideType,
            "vehicleCategoryTitle" to vehicleCategoryTitle,
            "estimatedFarePKR" to estimatedFarePKR,
            "distanceKm" to distanceKm,
            "status" to status,
            "city" to city,
            "paymentMethod" to paymentMethod.ifBlank { "CASH" },
            "paymentStatus" to paymentStatus.ifBlank { "PENDING" },
            "serviceMode" to serviceMode.ifBlank { "RIDE" },
            "isParcelDelivery" to isParcelDelivery
        )
        if (isParcelDelivery || serviceMode == "PARCEL") {
            if (parcelDescription.isNotBlank()) map["parcelDescription"] = parcelDescription
            if (parcelWeightKg.isNotBlank()) map["parcelWeightKg"] = parcelWeightKg
            if (recipientName.isNotBlank()) map["recipientName"] = recipientName
            if (recipientPhone.isNotBlank()) map["recipientPhone"] = recipientPhone
            if (codAmountPKR > 0L) map["codAmountPKR"] = codAmountPKR
        }
        promoCode?.let { if (it.isNotBlank()) map["promoCode"] = it }
        if (discountPKR > 0) map["discountPKR"] = discountPKR
        driverId?.let { if (it.isNotBlank()) map["driverId"] = it }
        driverName?.let { if (it.isNotBlank()) map["driverName"] = it }
        driverPhone?.let { if (it.isNotBlank()) map["driverPhone"] = it }
        driverLat?.let { map["driverLat"] = it }
        driverLng?.let { map["driverLng"] = it }
        pickupLat?.let { map["pickupLat"] = it }
        pickupLng?.let { map["pickupLng"] = it }
        destLat?.let { map["destLat"] = it }
        destLng?.let { map["destLng"] = it }
        createdAt?.let { map["createdAt"] = it }
        return map
    }

    fun toDomainBooking(): RideBooking {
        val category = try {
            VehicleCategory.valueOf(rideType)
        } catch (_: Exception) {
            VehicleCategory.RICKSHAW
        }
        val domainStatus = when (status) {
            "SEARCHING", "REQUESTED" -> RideStatus.SEARCHING
            "ACCEPTED" -> RideStatus.ACCEPTED
            "ARRIVED" -> RideStatus.DRIVER_ARRIVED
            "IN_PROGRESS" -> RideStatus.IN_PROGRESS
            "COMPLETED" -> RideStatus.COMPLETED
            "CANCELLED" -> RideStatus.CANCELLED
            else -> RideStatus.SEARCHING
        }
        return RideBooking(
            id = id.ifBlank { "JRW-" + (1000..9999).random() },
            passengerId = passengerId,
            passengerName = passengerName,
            passengerPhone = passengerPhone,
            pickup = if (pickupLocation.isNotBlank()) pickupLocation else "Committee Chowk",
            destination = if (destinationLocation.isNotBlank()) destinationLocation else "Jaranwala Railway Station",
            vehicleCategory = category,
            estimatedDistanceKm = distanceKm,
            estimatedFarePKR = estimatedFarePKR.toInt(),
            status = domainStatus,
            driverId = driverId,
            driverName = driverName,
            driverPhone = driverPhone,
            promoCode = promoCode,
            discountPKR = discountPKR.toInt(),
            driverLat = driverLat,
            driverLng = driverLng,
            pickupLat = pickupLat,
            pickupLng = pickupLng,
            destLat = destLat,
            destLng = destLng,
            paymentMethod = paymentMethod.ifBlank { "CASH" },
            paymentStatus = paymentStatus.ifBlank { "PENDING" },
            serviceMode = serviceMode.ifBlank { if (isParcelDelivery) "PARCEL" else "RIDE" },
            isParcelDelivery = isParcelDelivery || serviceMode.equals("PARCEL", ignoreCase = true),
            parcelDescription = parcelDescription,
            parcelWeightKg = parcelWeightKg.ifBlank { "Up to 5 kg" },
            recipientName = recipientName,
            recipientPhone = recipientPhone,
            codAmountPKR = codAmountPKR.toInt()
        )
    }
}

enum class SecurityEventType(val title: String, val urduTitle: String) {
    LOGIN_SUCCESS("Owner Admin Login Success", "کامیاب لاگ ان"),
    LOGIN_FAILED("Invalid Passcode Attempt", "ناکام لاگ ان"),
    BIOMETRIC_SUCCESS("Fingerprint Verified", "فنگر پرنٹ تصدیق شدہ"),
    BIOMETRIC_FAILED("Fingerprint Rejected", "فنگر پرنٹ مسترد"),
    MASTER_KEY_SUCCESS("Owner Master Key Verified", "ماسٹر کی تصدیق"),
    MASTER_KEY_FAILED("Invalid Master Key Attempt", "غلط ماسٹر کی"),
    PASSWORD_RESET_AUTHORIZED("Owner Password Reset Authorized", "پاس ورڈ ری سیٹ مجاز"),
    SUSPICIOUS_UNAUTHORIZED_RESET("Suspicious Password Reset Rejected", "مشکوک ری سیٹ مسترد"),
    UNAUTHORIZED_ELEVATION_ATTEMPT("Unauthorized Admin Elevation Blocked", "غیر مجاز ایڈمن کوشش بلاک"),
    SECURITY_LOCKOUT_TRIGGERED("Security Lockout Activated", "سیکیورٹی لاک آؤٹ فعال")
}

data class SecurityAuditLogEntry(
    val id: String = "SEC-" + (10000..99999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: SecurityEventType,
    val description: String,
    val severity: String = "NORMAL", // LOW, NORMAL, HIGH, CRITICAL
    val status: String = "LOGGED",
    val clientSignature: String = "Android Device / Hardware TEE"
)

/**
 * UI State for fetching and observing user ride history from Cloud Firestore.
 */
sealed class RideHistoryUiState {
    object Idle : RideHistoryUiState()
    object Loading : RideHistoryUiState()
    data class Success(
        val rides: List<RideBooking>,
        val lastSyncedAtMillis: Long = System.currentTimeMillis(),
        val isFromCacheOrDemo: Boolean = false
    ) : RideHistoryUiState()
    data class Unauthenticated(val message: String) : RideHistoryUiState()
    data class Error(val message: String, val urduMessage: String) : RideHistoryUiState()
}

/**
 * Supported payment methods for JARANWALA RIDE bookings & receipts.
 */
enum class PaymentMethod(
    val firestoreValue: String,
    val title: String,
    val urduTitle: String,
    val subtitle: String,
    val urduSubtitle: String
) {
    CASH(
        firestoreValue = "CASH",
        title = "Cash on Drop",
        urduTitle = "نقد ادائیگی (کیش)",
        subtitle = "Pay PKR directly to your Captain upon arrival",
        urduSubtitle = "منزل پر پہنچ کر کپتان کو براہِ راست نقد ادائیگی کریں"
    ),
    JAZZCASH(
        firestoreValue = "JAZZCASH",
        title = "JazzCash Mobile Wallet",
        urduTitle = "جاز کیش موبائل والٹ",
        subtitle = "Pay quickly via your JazzCash mobile account (03XX)",
        urduSubtitle = "اپنے جاز کیش موبائل اکاؤنٹ سے فوری ادائیگی کریں"
    ),
    EASYPAISA(
        firestoreValue = "EASYPAISA",
        title = "Easypaisa Mobile Wallet",
        urduTitle = "ایزی پیسہ موبائل والٹ",
        subtitle = "Fast digital payment via Easypaisa account (03XX)",
        urduSubtitle = "اپنے ایزی پیسہ موبائل اکاؤنٹ سے فوری ادائیگی کریں"
    );

    companion object {
        fun fromFirestore(value: String?): PaymentMethod {
            return when (value?.trim()?.uppercase()) {
                "JAZZCASH" -> JAZZCASH
                "EASYPAISA" -> EASYPAISA
                else -> CASH
            }
        }
    }
}



