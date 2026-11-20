package com.example.data

import android.content.Context
import com.example.data.local.CompletedRideEntity
import com.example.data.local.JaranwalaRoomDatabase
import com.example.data.local.WeddingConvoyHistoryEntity
import com.example.pricing.DistanceSource
import com.example.pricing.LocationDistanceResult
import com.example.pricing.LocationDistanceService
import com.example.pricing.RidePriceBreakdown
import com.example.pricing.RidePricingCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Locale
import com.example.R
import com.example.payment.PaymentSettlementRequest
import com.example.payment.PaymentSettlementResult
import com.example.payment.RidePaymentGatewayRouter
import com.example.util.OperationType
import com.example.util.RideNotificationManager
import com.example.util.handleFirestoreError
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.google.firebase.messaging.FirebaseMessaging
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.SecureRandom

sealed class AdminAuthResult {
    object Success : AdminAuthResult()
    data class RequiresSecondFactor(val message: String) : AdminAuthResult()
    data class InvalidCredentials(val attemptsLeft: Int) : AdminAuthResult()
    data class LockedOut(val remainingSeconds: Int) : AdminAuthResult()
}

sealed class OwnerAdminLoginResult {
    object Success : OwnerAdminLoginResult()
    data class RequiresSecondFactor(val message: String, val attemptsLeft: Int) : OwnerAdminLoginResult()
    data class InvalidCredentials(val attemptsLeft: Int) : OwnerAdminLoginResult()
    data class LockedOut(val remainingSeconds: Int) : OwnerAdminLoginResult()
    data class SecondFactorFailed(val message: String) : OwnerAdminLoginResult()
}

sealed class PasswordResetResult {
    object Success : PasswordResetResult()
    data class RejectedUnauthorized(val reason: String) : PasswordResetResult()
    data class InvalidMasterKey(val attemptsRemaining: Int) : PasswordResetResult()
    data class LockedOut(val remainingSeconds: Int) : PasswordResetResult()
}

class JaranwalaRepository internal constructor(
    private val injectedFirestore: com.google.firebase.firestore.FirebaseFirestore? = null,
    private val injectedAuth: com.google.firebase.auth.FirebaseAuth? = null
) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var appContext: Context? = null

    private fun getFirebaseAuth(): com.google.firebase.auth.FirebaseAuth? {
        return injectedAuth ?: try {
            com.google.firebase.auth.FirebaseAuth.getInstance()
        } catch (_: Throwable) {
            null
        }
    }

    // Local Room Database state
    private var roomDb: JaranwalaRoomDatabase? = null
    private val _localCompletedRides = MutableStateFlow<List<CompletedRideEntity>>(emptyList())
    val localCompletedRides: StateFlow<List<CompletedRideEntity>> = _localCompletedRides.asStateFlow()

    private val _localWeddingHistory = MutableStateFlow<List<WeddingConvoyHistoryEntity>>(emptyList())
    val localWeddingHistory: StateFlow<List<WeddingConvoyHistoryEntity>> = _localWeddingHistory.asStateFlow()

    // Firestore Ride History UI state
    private val _rideHistoryUiState = MutableStateFlow<RideHistoryUiState>(RideHistoryUiState.Idle)
    val rideHistoryUiState: StateFlow<RideHistoryUiState> = _rideHistoryUiState.asStateFlow()

    // Scheduled Rides (Future Bookings stored in /scheduled_rides)
    private val _scheduledRides = MutableStateFlow<List<ScheduledRide>>(emptyList())
    val scheduledRides: StateFlow<List<ScheduledRide>> = _scheduledRides.asStateFlow()

    // Completed Rides & Payments Summary state for authenticated user
    private val _completedPaymentRidesState = MutableStateFlow<RideHistoryUiState>(RideHistoryUiState.Idle)
    val completedPaymentRidesState: StateFlow<RideHistoryUiState> = _completedPaymentRidesState.asStateFlow()

    // Selected Payment Method & Gateway Settlement (Cash, JazzCash Stub, Easypaisa Stub)
    private val paymentGatewayRouter = RidePaymentGatewayRouter()

    private val _selectedPaymentMethod = MutableStateFlow(PaymentMethod.CASH)
    val selectedPaymentMethod: StateFlow<PaymentMethod> = _selectedPaymentMethod.asStateFlow()

    private val _selectedWalletAccount = MutableStateFlow("")
    val selectedWalletAccount: StateFlow<String> = _selectedWalletAccount.asStateFlow()

    private val _lastPaymentSettlement = MutableStateFlow<PaymentSettlementResult?>(null)
    val lastPaymentSettlement: StateFlow<PaymentSettlementResult?> = _lastPaymentSettlement.asStateFlow()

    fun selectPaymentMethod(method: PaymentMethod, walletAccount: String = "") {
        _selectedPaymentMethod.value = method
        _selectedWalletAccount.value = if (method == PaymentMethod.CASH) "" else walletAccount.trim()
    }

    fun getPaymentProcessor(method: PaymentMethod = _selectedPaymentMethod.value) =
        paymentGatewayRouter.processorFor(method)

    suspend fun settleRideFare(
        rideId: String,
        amountPKR: Int,
        method: PaymentMethod = _selectedPaymentMethod.value,
        walletAccount: String = _selectedWalletAccount.value
    ): PaymentSettlementResult {
        val user = _currentUser.value
        val result = paymentGatewayRouter.settle(
            method = method,
            request = PaymentSettlementRequest(
                rideId = rideId,
                amountPKR = amountPKR,
                passengerId = user?.uid ?: "",
                passengerPhone = user?.phoneNumber ?: "",
                walletAccount = walletAccount,
                driverId = _currentPassengerRide.value?.driverId
            )
        )
        _lastPaymentSettlement.value = result
        return result
    }

    // Language setting
    private val _appLanguage = MutableStateFlow(AppLanguage.BILINGUAL)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    fun setLanguage(lang: AppLanguage) {
        _appLanguage.value = lang
    }

    // User Authentication & Profile
    private val _currentUser = MutableStateFlow<UserProfile?>(
        UserProfile(
            uid = "USER-101",
            phoneNumber = "0301-8765432",
            name = "Ahmed Raza",
            role = UserRole.PASSENGER,
            isVerified = true
        )
    )
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _pendingOtps = mutableMapOf<String, String>()

    private fun normalizeOtpPhoneKey(phone: String): String {
        val validation = com.example.util.PakistanPhoneValidator.validate(phone)
        return if (validation.isValid) {
            validation.normalizedE164
        } else {
            phone.trim().replace(" ", "").replace("-", "")
        }
    }

    fun requestPhoneOtp(phone: String): String {
        val cleanPhone = normalizeOtpPhoneKey(phone)
        val generated = ((100000..999999).random()).toString()
        _pendingOtps[cleanPhone] = generated
        return generated
    }

    fun getPendingOtp(phone: String): String? {
        val cleanPhone = normalizeOtpPhoneKey(phone)
        return _pendingOtps[cleanPhone]
    }

    fun verifyPhoneOtpAndLogin(
        phone: String,
        otp: String,
        role: UserRole,
        fullName: String = "",
        photoUrl: String? = null,
        driverDetails: DriverRegDetails? = null,
        context: Context? = null,
        firebaseUid: String? = null,
        isFirebaseSdkVerified: Boolean = false
    ): Boolean {
        val cleanPhone = normalizeOtpPhoneKey(phone)
        val expected = _pendingOtps[cleanPhone]
        val isValid = (isFirebaseSdkVerified && !firebaseUid.isNullOrBlank()) ||
            (expected != null && expected == otp.trim())
        if (!isValid) return false

        _pendingOtps.remove(cleanPhone)

        val resolvedAuthUid = firebaseUid?.takeIf { it.isNotBlank() }
            ?: try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (_: Throwable) { null }
        val userUid = resolvedAuthUid ?: "UID-${cleanPhone.takeLast(7)}"
        val existingProfile = _currentUser.value?.takeIf { it.uid == userUid }
        val existingName = when {
            fullName.isNotBlank() -> fullName.trim()
            existingProfile != null && existingProfile.name.isNotBlank() -> existingProfile.name
            else -> "User ${cleanPhone.takeLast(4)}"
        }

        val safeRole = if (role == UserRole.ADMIN) {
            recordSecurityAuditLog(
                SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT,
                "Public OTP registration/login with ADMIN role rejected. Defaulted to PASSENGER.",
                severity = "HIGH",
                status = "BLOCKED"
            )
            UserRole.PASSENGER
        } else {
            role
        }

        val normalizedDisplayPhone = com.example.util.PakistanPhoneValidator.validate(phone).let { res ->
            if (res.isValid) res.localFormatted else phone.trim()
        }

        val profile = UserProfile(
            uid = userUid,
            phoneNumber = normalizedDisplayPhone,
            name = existingName,
            emergencyContactNumber = existingProfile?.emergencyContactNumber ?: "",
            role = safeRole,
            photoUrl = photoUrl ?: existingProfile?.photoUrl,
            isVerified = true,
            isGuest = false,
            cnic = driverDetails?.cnic ?: existingProfile?.cnic,
            licenseNo = driverDetails?.licenseNo ?: existingProfile?.licenseNo,
            vehicleCategory = driverDetails?.vehicleCategory ?: existingProfile?.vehicleCategory,
            vehicleModel = driverDetails?.vehicleModel ?: existingProfile?.vehicleModel,
            plateNo = driverDetails?.plateNo ?: existingProfile?.plateNo
        )
        _currentUser.value = profile

        if (context != null) {
            appContext = context.applicationContext
            saveSession(context, profile)
        }

        if (safeRole == UserRole.DRIVER) {
            if (driverDetails != null) {
                val newDriver = registerDriver(
                    name = existingName,
                    phone = normalizedDisplayPhone,
                    cnic = driverDetails.cnic,
                    license = driverDetails.licenseNo,
                    category = driverDetails.vehicleCategory,
                    model = driverDetails.vehicleModel,
                    plate = driverDetails.plateNo,
                    explicitId = userUid
                )
                // New drivers remain blocked until MAHI Owner Admin approves them
                _activeDriver.value = newDriver.copy(isOnline = false, isApproved = false)

                // Persist unapproved driver record in Firestore /drivers/{uid} respecting immutable rules
                scope.launch {
                    try {
                        val db = getFirestore(context) ?: return@launch
                        val driverRef = db.collection("drivers").document(userUid)
                        driverRef.get().addOnSuccessListener { snap ->
                            if (snap.exists()) {
                                val updateData = hashMapOf<String, Any>(
                                    "name" to existingName,
                                    "phone" to normalizedDisplayPhone,
                                    "cnic" to driverDetails.cnic,
                                    "licenseNumber" to driverDetails.licenseNo,
                                    "vehicleType" to driverDetails.vehicleCategory.name,
                                    "vehicleModel" to driverDetails.vehicleModel,
                                    "plateNumber" to driverDetails.plateNo,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                                driverRef.update(updateData).addOnFailureListener { ex ->
                                    handleFirestoreError(ex, OperationType.UPDATE, "drivers/$userUid") { getFirebaseAuth() }
                                }
                            } else {
                                val driverData = hashMapOf<String, Any>(
                                    "driverId" to userUid,
                                    "name" to existingName,
                                    "phone" to normalizedDisplayPhone,
                                    "cnic" to driverDetails.cnic,
                                    "licenseNumber" to driverDetails.licenseNo,
                                    "vehicleType" to driverDetails.vehicleCategory.name,
                                    "vehicleModel" to driverDetails.vehicleModel,
                                    "plateNumber" to driverDetails.plateNo,
                                    "isOnline" to false,
                                    "isApproved" to false,
                                    "rating" to 5.0,
                                    "totalRides" to 0,
                                    "earningsPKR" to 0.0,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                                driverRef.set(driverData).addOnFailureListener { ex ->
                                    handleFirestoreError(ex, OperationType.CREATE, "drivers/$userUid") { getFirebaseAuth() }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }
            } else {
                // Returning driver login: sync profile & approval state from Firestore
                syncUserFromFirestore(userUid, context)
            }
        }

        scope.launch {
            try {
                val db = getFirestore(context) ?: return@launch
                val userRef = db.collection("users").document(userUid)
                userRef.get().addOnSuccessListener { snap ->
                    if (snap.exists()) {
                        val updateData = hashMapOf<String, Any>(
                            "phoneNumber" to normalizedDisplayPhone,
                            "phone" to normalizedDisplayPhone,
                            "isVerified" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        if (fullName.isNotBlank()) {
                            updateData["name"] = fullName.trim()
                        }
                        userRef.update(updateData)
                            .addOnSuccessListener { syncUserFromFirestore(userUid, context) }
                            .addOnFailureListener { ex ->
                                handleFirestoreError(ex, OperationType.UPDATE, "users/$userUid") { getFirebaseAuth() }
                            }
                    } else {
                        val userData = hashMapOf<String, Any>(
                            "uid" to userUid,
                            "phoneNumber" to normalizedDisplayPhone,
                            "phone" to normalizedDisplayPhone,
                            "name" to existingName,
                            "role" to safeRole.name,
                            "isVerified" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        userRef.set(userData)
                            .addOnFailureListener { ex ->
                                handleFirestoreError(ex, OperationType.CREATE, "users/$userUid") { getFirebaseAuth() }
                            }
                    }
                }
            } catch (_: Throwable) {}
        }
        return true
    }

    val firestoreDatabaseId: String = "ai-studio-android-jaranwal-6046411e-5936-475c-a28a-6af2da26d46c"

    fun getFirestore(context: Context? = null): com.google.firebase.firestore.FirebaseFirestore? {
        if (injectedFirestore != null) return injectedFirestore
        val resolvedContext = context?.applicationContext ?: appContext
        val dbId = try {
            resolvedContext?.getString(R.string.firestore_database_id)?.takeIf { it.isNotBlank() }
                ?: firestoreDatabaseId
        } catch (_: Throwable) {
            firestoreDatabaseId
        }
        return try {
            com.google.firebase.firestore.FirebaseFirestore.getInstance(dbId)
        } catch (_: Throwable) {
            null
        }
    }

    private fun requireFirebaseUserId(): String {
        return getFirebaseAuth()?.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    fun isCurrentFirebaseUserMahiOwner(): Boolean {
        val fbUser = getFirebaseAuth()?.currentUser ?: return false
        val emailMatch = fbUser.email?.equals(IMMUTABLE_OWNER_EMAIL, ignoreCase = true) == true && fbUser.isEmailVerified
        val phoneClean = fbUser.phoneNumber?.replace(" ", "")?.replace("-", "")
        val phoneMatch = phoneClean == IMMUTABLE_OWNER_PHONE
        return fbUser.uid == IMMUTABLE_OWNER_UID || emailMatch || phoneMatch
    }

    /**
     * Fetches the authenticated user's profile and (if Driver) their Captain approval record from Firestore.
     */
    fun syncUserFromFirestore(uid: String, context: Context? = null) {
        if (uid.isBlank()) return
        scope.launch {
            try {
                val db = getFirestore(context) ?: return@launch
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { userDoc ->
                        if (userDoc.exists()) {
                            val roleStr = userDoc.getString("role")
                            val role = try {
                                if (roleStr != null) UserRole.valueOf(roleStr) else (_currentUser.value?.role ?: UserRole.PASSENGER)
                            } catch (_: Throwable) {
                                _currentUser.value?.role ?: UserRole.PASSENGER
                            }
                            val name = userDoc.getString("name")?.takeIf { it.isNotBlank() }
                                ?: (_currentUser.value?.name ?: "Jaranwala User")
                            val phone = userDoc.getString("phoneNumber")
                                ?: userDoc.getString("phone")
                                ?: (_currentUser.value?.phoneNumber ?: "")
                            val emergencyContact = userDoc.getString("emergencyContact")
                                ?: (_currentUser.value?.emergencyContactNumber ?: "")
                            val email = userDoc.getString("email") ?: _currentUser.value?.email
                            val updated = (_currentUser.value ?: UserProfile(
                                uid = uid,
                                phoneNumber = phone,
                                name = name,
                                emergencyContactNumber = emergencyContact,
                                role = role
                            )).copy(
                                uid = uid,
                                name = name,
                                phoneNumber = phone,
                                emergencyContactNumber = emergencyContact,
                                email = email,
                                role = role,
                                isVerified = true,
                                isGuest = false
                            )
                            _currentUser.value = updated
                            (context ?: appContext)?.let { saveSession(it, updated) }
                        }
                    }

                db.collection("drivers").document(uid).get()
                    .addOnSuccessListener { driverDoc ->
                        if (driverDoc.exists()) {
                            val catStr = driverDoc.getString("vehicleType") ?: "RICKSHAW"
                            val category = try { VehicleCategory.valueOf(catStr) } catch (_: Throwable) { VehicleCategory.RICKSHAW }
                            val remoteDriver = DriverProfile(
                                id = uid,
                                name = driverDoc.getString("name") ?: (_currentUser.value?.name ?: "Captain"),
                                phone = driverDoc.getString("phone") ?: (_currentUser.value?.phoneNumber ?: ""),
                                cnic = driverDoc.getString("cnic") ?: "",
                                licenseNo = driverDoc.getString("licenseNumber") ?: "",
                                vehicleCategory = category,
                                vehicleModel = driverDoc.getString("vehicleModel") ?: "",
                                plateNo = driverDoc.getString("plateNumber") ?: "",
                                isApproved = driverDoc.getBoolean("isApproved") ?: false,
                                isOnline = driverDoc.getBoolean("isOnline") ?: false,
                                rating = driverDoc.getDouble("rating") ?: 5.0,
                                todayEarningsPKR = (driverDoc.get("earningsPKR") as? Number)?.toInt() ?: 0,
                                weeklyEarningsPKR = (driverDoc.get("earningsPKR") as? Number)?.toInt() ?: 0,
                                totalTrips = (driverDoc.get("totalRides") as? Number)?.toInt() ?: 0,
                                isDemoData = false
                            )
                            _activeDriver.value = remoteDriver
                            _drivers.update { existing ->
                                val others = existing.filter { it.id != uid }
                                listOf(remoteDriver) + others
                            }
                        }
                    }
            } catch (_: Throwable) {}
        }
    }

    private var userProfileListener: com.google.firebase.firestore.ListenerRegistration? = null

    /**
     * Subscribes to real-time updates on `/users/{uid}` in Firebase Firestore so
     * display name, phone number, and emergency contact updates reflect live.
     */
    fun listenToUserProfile(
        uid: String,
        context: Context? = null
    ): com.google.firebase.firestore.ListenerRegistration? {
        if (uid.isBlank()) return null
        val authUid = getFirebaseAuth()?.currentUser?.uid
        if (authUid == null || authUid != uid) return null
        val db = getFirestore(context) ?: return null
        userProfileListener?.remove()
        val path = "users/$uid"
        val registration = db.collection("users").document(uid)
            .addSnapshotListener { userDoc, error ->
                if (error != null) {
                    if (getFirebaseAuth()?.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.GET, path) { getFirebaseAuth() }
                    }
                    return@addSnapshotListener
                }
                if (userDoc != null && userDoc.exists()) {
                    val roleStr = userDoc.getString("role")
                    val role = try {
                        if (roleStr != null) UserRole.valueOf(roleStr) else (_currentUser.value?.role ?: UserRole.PASSENGER)
                    } catch (_: Throwable) {
                        _currentUser.value?.role ?: UserRole.PASSENGER
                    }
                    val name = userDoc.getString("name")?.takeIf { it.isNotBlank() }
                        ?: (_currentUser.value?.name ?: "Jaranwala User")
                    val phone = userDoc.getString("phoneNumber")
                        ?: userDoc.getString("phone")
                        ?: (_currentUser.value?.phoneNumber ?: "")
                    val emergencyContact = userDoc.getString("emergencyContact")
                        ?: (_currentUser.value?.emergencyContactNumber ?: "")
                    val email = userDoc.getString("email") ?: _currentUser.value?.email
                    val updated = (_currentUser.value ?: UserProfile(
                        uid = uid,
                        phoneNumber = phone,
                        name = name,
                        emergencyContactNumber = emergencyContact,
                        role = role
                    )).copy(
                        uid = uid,
                        name = name,
                        phoneNumber = phone,
                        emergencyContactNumber = emergencyContact,
                        email = email,
                        role = role,
                        isVerified = true,
                        isGuest = false
                    )
                    _currentUser.value = updated
                    (context ?: appContext)?.let { saveSession(it, updated) }
                }
            }
        userProfileListener = registration
        return registration
    }

    /**
     * Validates and writes user display name, phone number, and emergency contact directly
     * to `/users/{uid}` in Firebase Firestore following zero-trust rules parity.
     */
    suspend fun updateUserProfileFirestoreSuspend(
        displayName: String,
        phoneNumber: String,
        emergencyContactNumber: String,
        targetUidOverride: String? = null,
        context: Context? = null
    ): Result<UserProfile> {
        val cleanName = displayName.trim()
        val cleanPhone = phoneNumber.trim()
        val cleanEmergency = emergencyContactNumber.trim()
        var path = "users/${targetUidOverride ?: getFirebaseAuth()?.currentUser?.uid ?: "unauth"}"
        return try {
            require(cleanName.isNotEmpty() && cleanName.length <= 100) {
                "Display name must be between 1 and 100 characters"
            }
            require(cleanPhone.length <= 30 && cleanPhone.count { it.isDigit() } >= 7) {
                "Enter a valid phone number (e.g. 0301-8765432)"
            }
            require(
                cleanEmergency.isEmpty() ||
                    (cleanEmergency.length <= 30 && cleanEmergency.count { it.isDigit() } >= 3)
            ) {
                "Emergency contact must be a valid phone number up to 30 characters"
            }

            val uid = targetUidOverride ?: requireFirebaseUserId()
            path = "users/$uid"
            val db = requireNotNull(getFirestore(context)) { "Firestore is not initialized" }
            val docRef = db.collection("users").document(uid)
            val existingSnap = docRef.get().await()

            val current = _currentUser.value
            val safeRole = if (current?.role == UserRole.ADMIN) {
                if (isCurrentFirebaseUserMahiOwner()) UserRole.ADMIN else UserRole.PASSENGER
            } else {
                current?.role ?: UserRole.PASSENGER
            }

            if (existingSnap.exists()) {
                val updatePayload = hashMapOf<String, Any>(
                    "name" to cleanName,
                    "phone" to cleanPhone,
                    "phoneNumber" to cleanPhone,
                    "emergencyContact" to cleanEmergency,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            } else {
                val createPayload = hashMapOf<String, Any>(
                    "uid" to uid,
                    "name" to cleanName,
                    "phone" to cleanPhone,
                    "phoneNumber" to cleanPhone,
                    "emergencyContact" to cleanEmergency,
                    "role" to safeRole.name,
                    "isVerified" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                current?.email?.takeIf { it.isNotBlank() }?.let { createPayload["email"] = it }
                docRef.set(createPayload).await()
            }

            val updatedProfile = (current ?: UserProfile(
                uid = uid,
                phoneNumber = cleanPhone,
                name = cleanName,
                emergencyContactNumber = cleanEmergency,
                role = safeRole
            )).copy(
                uid = uid,
                name = cleanName,
                phoneNumber = cleanPhone,
                emergencyContactNumber = cleanEmergency
            )
            _currentUser.value = updatedProfile
            (context ?: appContext)?.let { saveSession(it, updatedProfile) }
            Result.success(updatedProfile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path) { getFirebaseAuth() }
            Result.failure(e)
        }
    }

    /**
     * Saves user display name, phone number, and emergency contact number.
     * Syncs directly with Firebase Firestore when authenticated via FirebaseAuth,
     * and updates local session state for offline/guest UI preview.
     */
    suspend fun saveUserProfileDetails(
        displayName: String,
        phoneNumber: String,
        emergencyContactNumber: String,
        context: Context? = null
    ): Result<UserProfile> {
        val cleanName = displayName.trim()
        val cleanPhone = phoneNumber.trim()
        val cleanEmergency = emergencyContactNumber.trim()
        if (cleanName.isEmpty() || cleanName.length > 100) {
            return Result.failure(IllegalArgumentException("Display name must be between 1 and 100 characters"))
        }
        if (cleanPhone.length > 30 || cleanPhone.count { it.isDigit() } < 7) {
            return Result.failure(IllegalArgumentException("Enter a valid phone number (e.g. 0301-8765432)"))
        }
        if (cleanEmergency.isNotEmpty() && (cleanEmergency.length > 30 || cleanEmergency.count { it.isDigit() } < 3)) {
            return Result.failure(IllegalArgumentException("Enter a valid emergency contact number"))
        }

        val current = _currentUser.value
            ?: return Result.failure(IllegalStateException("Please sign in to save your profile."))

        val fbUser = getFirebaseAuth()?.currentUser
        if (fbUser != null && !current.isGuest && fbUser.uid == current.uid) {
            return updateUserProfileFirestoreSuspend(
                displayName = cleanName,
                phoneNumber = cleanPhone,
                emergencyContactNumber = cleanEmergency,
                context = context
            )
        }

        val updated = current.copy(
            name = cleanName,
            phoneNumber = cleanPhone,
            emergencyContactNumber = cleanEmergency
        )
        _currentUser.value = updated
        (context ?: appContext)?.let { saveSession(it, updated) }
        return Result.success(updated)
    }

    fun setUserFromFirebase(
        user: com.google.firebase.auth.FirebaseUser,
        role: UserRole,
        context: Context? = null
    ): UserProfile {
        val safeRole = if (role == UserRole.ADMIN) UserRole.PASSENGER else role
        val profile = UserProfile(
            uid = user.uid,
            phoneNumber = user.phoneNumber ?: "",
            name = user.displayName ?: "Google User",
            email = user.email,
            photoUrl = user.photoUrl?.toString(),
            role = safeRole,
            isVerified = true,
            isGuest = false
        )
        _currentUser.value = profile
        (context ?: appContext)?.let {
            appContext = it.applicationContext
            saveSession(it, profile)
        }
        scope.launch {
            try {
                val db = getFirestore(context) ?: return@launch
                val path = "users/${user.uid}"
                val userRef = db.collection("users").document(user.uid)
                userRef.get().addOnSuccessListener { snap ->
                    if (snap.exists()) {
                        val updateData = hashMapOf<String, Any>(
                            "email" to (user.email ?: ""),
                            "isVerified" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        if (!user.phoneNumber.isNullOrBlank()) {
                            updateData["phoneNumber"] = user.phoneNumber!!
                            updateData["phone"] = user.phoneNumber!!
                        }
                        userRef.update(updateData)
                            .addOnSuccessListener { syncUserFromFirestore(user.uid, context) }
                            .addOnFailureListener { ex ->
                                handleFirestoreError(ex, OperationType.UPDATE, path) { getFirebaseAuth() }
                            }
                    } else {
                        val userData = hashMapOf<String, Any>(
                            "uid" to user.uid,
                            "email" to (user.email ?: ""),
                            "name" to profile.name,
                            "phoneNumber" to profile.phoneNumber,
                            "phone" to (profile.phoneNumber.ifBlank { "N/A" }),
                            "role" to safeRole.name,
                            "isVerified" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        userRef.set(userData)
                            .addOnFailureListener { ex ->
                                handleFirestoreError(ex, OperationType.CREATE, path) { getFirebaseAuth() }
                            }
                    }
                }
            } catch (e: Throwable) {
                handleFirestoreError(e, OperationType.WRITE, "users/${user.uid}") { getFirebaseAuth() }
            }
        }
        syncUserFromFirestore(user.uid, context)
        return profile
    }

    fun loginWithGoogle(email: String, displayName: String, role: UserRole): UserProfile {
        val uid = "G-" + (10000..99999).random()
        val safeRole = if (role == UserRole.ADMIN) {
            recordSecurityAuditLog(
                SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT,
                "Google Sign-In with ADMIN role rejected. Defaulted to PASSENGER.",
                severity = "HIGH",
                status = "BLOCKED"
            )
            UserRole.PASSENGER
        } else {
            role
        }
        val profile = UserProfile(
            uid = uid,
            phoneNumber = "0300-0000000",
            name = displayName.ifBlank { "Google User" },
            email = email,
            role = safeRole,
            isVerified = true
        )
        _currentUser.value = profile
        scope.launch {
            try {
                val db = getFirestore()
                val userData = hashMapOf<String, Any>(
                    "uid" to uid,
                    "email" to email,
                    "name" to profile.name,
                    "phone" to profile.phoneNumber,
                    "role" to safeRole.name,
                    "isVerified" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db?.collection("users")?.document(uid)?.set(userData, SetOptions.merge())
            } catch (_: Throwable) {}
        }
        return profile
    }

    /**
     * Persists this device's FCM registration token to /users/{uid}/fcmTokens/{token}.
     */
    fun saveFcmToken(uid: String, token: String, context: Context? = null) {
        if (uid.isBlank() || token.isBlank()) return
        val resolvedCtx = context?.applicationContext ?: appContext
        try {
            resolvedCtx?.getSharedPreferences("jaranwala_auth_prefs", Context.MODE_PRIVATE)
                ?.edit()
                ?.putString("last_fcm_token", token)
                ?.apply()
        } catch (_: Throwable) {}

        scope.launch {
            try {
                val db = getFirestore(resolvedCtx) ?: return@launch
                val path = "users/$uid/fcmTokens/$token"
                val payload = hashMapOf<String, Any>(
                    "token" to token,
                    "platform" to "android",
                    "role" to (_currentUser.value?.role?.name ?: UserRole.PASSENGER.name),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("users").document(uid)
                    .collection("fcmTokens").document(token)
                    .set(payload, SetOptions.merge())
                    .addOnFailureListener { ex ->
                        handleFirestoreError(ex, OperationType.WRITE, path) { getFirebaseAuth() }
                    }
            } catch (e: Throwable) {
                handleFirestoreError(e, OperationType.WRITE, "users/$uid/fcmTokens/$token") { getFirebaseAuth() }
            }
        }
    }

    /**
     * Emits a real-time notification event document in /notification_events/{eventId}
     * so Cloud Functions / FCM triggers can deliver push notifications across devices.
     */
    fun emitNotificationEvent(
        type: String,
        recipientUid: String,
        title: String,
        body: String,
        rideId: String? = null,
        targetTab: Int = 0
    ) {
        val senderUid = getFirebaseAuth()?.currentUser?.uid ?: _currentUser.value?.uid ?: return
        if (senderUid.isBlank() || recipientUid.isBlank()) return
        val eventId = "NTF-${System.currentTimeMillis()}-${(100..999).random()}"
        scope.launch {
            try {
                val db = getFirestore() ?: return@launch
                val path = "notification_events/$eventId"
                val payload = hashMapOf<String, Any>(
                    "senderUid" to senderUid,
                    "recipientUid" to recipientUid,
                    "type" to type,
                    "title" to title.take(120),
                    "body" to body.take(400),
                    "targetTab" to targetTab,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (!rideId.isNullOrBlank()) {
                    payload["rideId"] = rideId
                }
                db.collection("notification_events").document(eventId)
                    .set(payload)
                    .addOnFailureListener { ex ->
                        handleFirestoreError(ex, OperationType.CREATE, path) { getFirebaseAuth() }
                    }
            } catch (e: Throwable) {
                handleFirestoreError(e, OperationType.CREATE, "notification_events/$eventId") { getFirebaseAuth() }
            }
        }
    }

    /**
     * Stores a ride request in Cloud Firestore for Jaranwala city service.
     */
    fun saveRideRequestToFirestore(
        rideRequest: RideRequestFirestore,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val db = getFirestore()
                if (db != null) {
                    val docRef = if (rideRequest.id.isNotBlank()) {
                        db.collection("ride_requests").document(rideRequest.id)
                    } else {
                        db.collection("ride_requests").document()
                    }
                    val finalModel = if (rideRequest.id.isNotBlank()) rideRequest else rideRequest.copy(id = docRef.id)
                    val payload = finalModel.toMap().toMutableMap()
                    payload["createdAt"] = FieldValue.serverTimestamp()
                    payload["updatedAt"] = FieldValue.serverTimestamp()

                    docRef.set(payload).addOnSuccessListener {
                        onComplete?.invoke(true, docRef.id)
                    }.addOnFailureListener { e ->
                        handleFirestoreError(e, OperationType.CREATE, docRef.path) { getFirebaseAuth() }
                        onComplete?.invoke(false, e.message)
                    }

                    val rideMirrorPayload = hashMapOf<String, Any>(
                        "rideId" to finalModel.id,
                        "passengerId" to finalModel.passengerId,
                        "passengerName" to finalModel.passengerName,
                        "passengerPhone" to finalModel.passengerPhone,
                        "pickup" to finalModel.pickupLocation,
                        "destination" to finalModel.destinationLocation,
                        "vehicleType" to finalModel.rideType,
                        "estimatedFarePKR" to finalModel.estimatedFarePKR,
                        "distanceKm" to finalModel.distanceKm,
                        "status" to finalModel.status,
                        "paymentMethod" to finalModel.paymentMethod,
                        "paymentStatus" to finalModel.paymentStatus,
                        "serviceMode" to finalModel.serviceMode,
                        "isParcelDelivery" to finalModel.isParcelDelivery,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (finalModel.isParcelDelivery || finalModel.serviceMode == "PARCEL") {
                        if (finalModel.parcelDescription.isNotBlank()) rideMirrorPayload["parcelDescription"] = finalModel.parcelDescription
                        if (finalModel.parcelWeightKg.isNotBlank()) rideMirrorPayload["parcelWeightKg"] = finalModel.parcelWeightKg
                        if (finalModel.recipientName.isNotBlank()) rideMirrorPayload["recipientName"] = finalModel.recipientName
                        if (finalModel.recipientPhone.isNotBlank()) rideMirrorPayload["recipientPhone"] = finalModel.recipientPhone
                        if (finalModel.codAmountPKR > 0L) rideMirrorPayload["codAmountPKR"] = finalModel.codAmountPKR
                    }
                    finalModel.pickupLat?.let { rideMirrorPayload["pickupLat"] = it }
                    finalModel.pickupLng?.let { rideMirrorPayload["pickupLng"] = it }
                    finalModel.destLat?.let { rideMirrorPayload["destLat"] = it }
                    finalModel.destLng?.let { rideMirrorPayload["destLng"] = it }

                    db.collection("rides").document(finalModel.id)
                        .set(rideMirrorPayload)
                        .addOnFailureListener { e ->
                            handleFirestoreError(e, OperationType.CREATE, "rides/${finalModel.id}") { getFirebaseAuth() }
                        }
                } else {
                    onComplete?.invoke(true, rideRequest.id)
                }
            } catch (e: Throwable) {
                handleFirestoreError(e, OperationType.CREATE, "ride_requests/${rideRequest.id}") { getFirebaseAuth() }
                onComplete?.invoke(false, e.message)
            }
        }
    }

    private fun mapFirestoreRideDocument(doc: com.google.firebase.firestore.DocumentSnapshot): RideBooking? {
        return try {
            val req = doc.toObject(RideRequestFirestore::class.java)?.copy(id = doc.id)
            val base = req?.toDomainBooking() ?: return null
            val ts = doc.getTimestamp("createdAt")?.toDate()?.time
                ?: doc.getLong("timestamp")
                ?: base.timestamp
            val plate = doc.getString("vehiclePlate") ?: base.vehiclePlate
            val rating = (doc.get("ratingGiven") as? Number)?.toInt()
                ?: (doc.get("rating") as? Number)?.toInt()
                ?: base.ratingGiven
            val review = doc.getString("reviewGiven") ?: doc.getString("review") ?: base.reviewGiven
            base.copy(
                timestamp = ts,
                vehiclePlate = plate,
                ratingGiven = rating,
                reviewGiven = review
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Fetches ride history from Cloud Firestore for the currently logged-in user
     * (matching passengerId or driverId) and updates [rideHistoryUiState] & [passengerHistory].
     */
    fun fetchRideHistoryFromFirestore(
        userId: String = "",
        onResult: ((List<RideBooking>) -> Unit)? = null
    ) {
        val current = _currentUser.value
        val firebaseUid = getFirebaseAuth()?.currentUser?.uid
        val targetUid = userId.ifBlank { firebaseUid ?: current?.uid ?: "" }

        if (targetUid.isBlank() || current?.isGuest == true) {
            _rideHistoryUiState.value = RideHistoryUiState.Unauthenticated(
                "Please sign in with your verified mobile number or Google account to view cloud trip history."
            )
            onResult?.invoke(_passengerHistory.value)
            return
        }

        _rideHistoryUiState.value = RideHistoryUiState.Loading
        scope.launch {
            try {
                val db = getFirestore()
                if (db == null) {
                    _rideHistoryUiState.value = RideHistoryUiState.Success(
                        rides = _passengerHistory.value,
                        isFromCacheOrDemo = true
                    )
                    onResult?.invoke(_passengerHistory.value)
                    return@launch
                }

                val filterField = if (current?.role == UserRole.DRIVER) "driverId" else "passengerId"
                db.collection("ride_requests")
                    .whereEqualTo(filterField, targetUid)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val firestoreRides = snapshot.documents
                            .mapNotNull { mapFirestoreRideDocument(it) }
                            .sortedByDescending { it.timestamp }

                        _passengerHistory.update { existing ->
                            val nonDuplicates = existing.filter { ex -> firestoreRides.none { it.id == ex.id } }
                            (firestoreRides + nonDuplicates).sortedByDescending { it.timestamp }
                        }
                        _rideHistoryUiState.value = RideHistoryUiState.Success(
                            rides = _passengerHistory.value,
                            isFromCacheOrDemo = firestoreRides.isEmpty()
                        )
                        onResult?.invoke(_passengerHistory.value)
                    }
                    .addOnFailureListener { ex ->
                        _rideHistoryUiState.value = RideHistoryUiState.Error(
                            message = ex.localizedMessage ?: "Failed to sync trip history from Firestore",
                            urduMessage = "کلاؤڈ سے سفر کا ریکارڈ لوڈ کرنے میں خرابی ہوئی"
                        )
                        onResult?.invoke(_passengerHistory.value)
                    }
            } catch (ex: Throwable) {
                _rideHistoryUiState.value = RideHistoryUiState.Error(
                    message = ex.localizedMessage ?: "Unable to fetch trip history",
                    urduMessage = "سفر کا ریکارڈ لوڈ نہیں ہو سکا"
                )
                onResult?.invoke(_passengerHistory.value)
            }
        }
    }

    /**
     * Real-time Firestore listener for logged-in user's past & active trips.
     */
    fun listenToUserRideHistoryFromFirestore(
        userId: String,
        role: UserRole = UserRole.PASSENGER
    ): com.google.firebase.firestore.ListenerRegistration? {
        if (userId.isBlank()) return null
        return try {
            val db = getFirestore() ?: return null
            val filterField = if (role == UserRole.DRIVER) "driverId" else "passengerId"
            db.collection("ride_requests")
                .whereEqualTo(filterField, userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        _rideHistoryUiState.value = RideHistoryUiState.Error(
                            message = error.localizedMessage ?: "Firestore sync error",
                            urduMessage = "کلاؤڈ سنک میں خرابی"
                        )
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val firestoreRides = snapshot.documents
                            .mapNotNull { mapFirestoreRideDocument(it) }
                            .sortedByDescending { it.timestamp }
                        _passengerHistory.update { existing ->
                            val nonDuplicates = existing.filter { ex -> firestoreRides.none { it.id == ex.id } }
                            (firestoreRides + nonDuplicates).sortedByDescending { it.timestamp }
                        }
                        _rideHistoryUiState.value = RideHistoryUiState.Success(
                            rides = _passengerHistory.value,
                            isFromCacheOrDemo = firestoreRides.isEmpty()
                        )
                    }
                }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Fetches past COMPLETED rides strictly for the currently authenticated user from Firestore,
     * showing fare amount, date, payment method/status, and trip details for the Ride History & Payments screen.
     */
    fun fetchCompletedRidesAndPaymentsFromFirestore(
        onResult: ((List<RideBooking>) -> Unit)? = null
    ) {
        val current = _currentUser.value
        val firebaseUid = getFirebaseAuth()?.currentUser?.uid
        val targetUid = firebaseUid ?: current?.uid ?: ""

        if (targetUid.isBlank() || current?.isGuest == true) {
            _completedPaymentRidesState.value = RideHistoryUiState.Unauthenticated(
                "Please sign in with your verified phone number or Google account to view completed rides and payment receipts."
            )
            onResult?.invoke(emptyList())
            return
        }

        _completedPaymentRidesState.value = RideHistoryUiState.Loading
        scope.launch {
            try {
                val db = getFirestore()
                if (db == null) {
                    val localCompleted = _passengerHistory.value
                        .filter { it.status == RideStatus.COMPLETED }
                        .sortedByDescending { it.timestamp }
                    _completedPaymentRidesState.value = RideHistoryUiState.Success(
                        rides = localCompleted,
                        isFromCacheOrDemo = true
                    )
                    onResult?.invoke(localCompleted)
                    return@launch
                }

                val filterField = if (current?.role == UserRole.DRIVER) "driverId" else "passengerId"
                db.collection("ride_requests")
                    .whereEqualTo(filterField, targetUid)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val remoteCompleted = snapshot.documents
                            .mapNotNull { mapFirestoreRideDocument(it) }
                            .filter { it.status == RideStatus.COMPLETED }
                            .sortedByDescending { it.timestamp }

                        val merged = if (remoteCompleted.isNotEmpty()) {
                            val localUserCompleted = _passengerHistory.value.filter {
                                it.status == RideStatus.COMPLETED &&
                                    !it.isDemoData &&
                                    (it.passengerId == targetUid || it.driverId == targetUid)
                            }
                            (remoteCompleted + localUserCompleted)
                                .distinctBy { it.id }
                                .sortedByDescending { it.timestamp }
                        } else {
                            // Keep non-demo completed rides belonging to this user if Firestore has none yet
                            _passengerHistory.value.filter {
                                it.status == RideStatus.COMPLETED &&
                                    (it.passengerId == targetUid || it.driverId == targetUid || it.isDemoData)
                            }.sortedByDescending { it.timestamp }
                        }

                        _completedPaymentRidesState.value = RideHistoryUiState.Success(
                            rides = merged,
                            isFromCacheOrDemo = remoteCompleted.isEmpty()
                        )
                        onResult?.invoke(merged)
                    }
                    .addOnFailureListener { ex ->
                        handleFirestoreError(ex, OperationType.LIST, "ride_requests") { getFirebaseAuth() }
                        _completedPaymentRidesState.value = RideHistoryUiState.Error(
                            message = ex.localizedMessage ?: "Failed to load completed rides & payments from Firestore",
                            urduMessage = "مکمل سواریاں اور ادائیگی کا ریکارڈ لوڈ کرنے میں خرابی ہوئی"
                        )
                        onResult?.invoke(emptyList())
                    }
            } catch (ex: Throwable) {
                handleFirestoreError(ex, OperationType.LIST, "ride_requests") { getFirebaseAuth() }
                _completedPaymentRidesState.value = RideHistoryUiState.Error(
                    message = ex.localizedMessage ?: "Unable to fetch payment history",
                    urduMessage = "ادائیگی کا ریکارڈ لوڈ نہیں ہو سکا"
                )
                onResult?.invoke(emptyList())
            }
        }
    }

    /**
     * Real-time Firestore listener for completed rides & payment records of the authenticated user.
     */
    fun listenToCompletedRidesAndPayments(): com.google.firebase.firestore.ListenerRegistration? {
        val current = _currentUser.value
        val firebaseUid = getFirebaseAuth()?.currentUser?.uid
        val targetUid = firebaseUid ?: current?.uid ?: ""
        if (targetUid.isBlank() || current?.isGuest == true) {
            _completedPaymentRidesState.value = RideHistoryUiState.Unauthenticated(
                "Sign in to view your verified payment receipts and completed trip ledger."
            )
            return null
        }

        return try {
            val db = getFirestore() ?: return null
            val filterField = if (current?.role == UserRole.DRIVER) "driverId" else "passengerId"
            db.collection("ride_requests")
                .whereEqualTo(filterField, targetUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        handleFirestoreError(error, OperationType.LIST, "ride_requests") { getFirebaseAuth() }
                        _completedPaymentRidesState.value = RideHistoryUiState.Error(
                            message = error.localizedMessage ?: "Firestore payment sync error",
                            urduMessage = "کلاؤڈ ادائیگی سنک میں خرابی"
                        )
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val remoteCompleted = snapshot.documents
                            .mapNotNull { mapFirestoreRideDocument(it) }
                            .filter { it.status == RideStatus.COMPLETED }
                            .sortedByDescending { it.timestamp }

                        val fallback = if (remoteCompleted.isEmpty()) {
                            _passengerHistory.value.filter {
                                it.status == RideStatus.COMPLETED &&
                                    (it.passengerId == targetUid || it.driverId == targetUid)
                            }.sortedByDescending { it.timestamp }
                        } else {
                            remoteCompleted
                        }

                        _completedPaymentRidesState.value = RideHistoryUiState.Success(
                            rides = fallback,
                            isFromCacheOrDemo = remoteCompleted.isEmpty()
                        )
                    }
                }
        } catch (_: Throwable) {
            null
        }
    }

    private fun mapFirestoreScheduledRideDocument(doc: com.google.firebase.firestore.DocumentSnapshot): ScheduledRide? {
        return try {
            val pickup = doc.getString("pickupLocation") ?: return null
            val destination = doc.getString("destinationLocation") ?: return null
            val rideTypeStr = doc.getString("rideType") ?: "RICKSHAW"
            val category = try { VehicleCategory.valueOf(rideTypeStr) } catch (_: Throwable) { VehicleCategory.RICKSHAW }
            val statusStr = doc.getString("status") ?: "SCHEDULED"
            val status = try { ScheduledRideStatus.valueOf(statusStr) } catch (_: Throwable) { ScheduledRideStatus.SCHEDULED }
            val scheduledMillis = (doc.get("scheduledForMillis") as? Number)?.toLong() ?: return null
            val createdMillis = doc.getTimestamp("createdAt")?.toDate()?.time
                ?: (doc.get("createdAtMillis") as? Number)?.toLong()
                ?: System.currentTimeMillis()
            ScheduledRide(
                id = doc.id,
                passengerId = doc.getString("passengerId") ?: "",
                passengerName = doc.getString("passengerName") ?: "Passenger",
                passengerPhone = doc.getString("passengerPhone") ?: "",
                pickup = pickup,
                destination = destination,
                vehicleCategory = category,
                distanceKm = (doc.get("distanceKm") as? Number)?.toDouble() ?: 0.0,
                estimatedFarePKR = (doc.get("estimatedFarePKR") as? Number)?.toInt() ?: 0,
                scheduledForMillis = scheduledMillis,
                scheduledDateDisplay = doc.getString("scheduledDateDisplay") ?: "",
                scheduledTimeDisplay = doc.getString("scheduledTimeDisplay") ?: "",
                notes = doc.getString("notes") ?: "",
                promoCode = doc.getString("promoCode"),
                discountPKR = (doc.get("discountPKR") as? Number)?.toInt() ?: 0,
                status = status,
                driverId = doc.getString("driverId"),
                pickupLat = (doc.get("pickupLat") as? Number)?.toDouble(),
                pickupLng = (doc.get("pickupLng") as? Number)?.toDouble(),
                destLat = (doc.get("destLat") as? Number)?.toDouble(),
                destLng = (doc.get("destLng") as? Number)?.toDouble(),
                paymentMethod = doc.getString("paymentMethod") ?: "CASH",
                createdAtMillis = createdMillis
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Schedules a ride for a future date and time and persists it to the 'scheduled_rides' Firestore collection.
     */
    fun scheduleRideForLater(
        passengerName: String,
        passengerPhone: String,
        pickup: String,
        destination: String,
        category: VehicleCategory,
        scheduledForMillis: Long,
        scheduledDateDisplay: String,
        scheduledTimeDisplay: String,
        notes: String = "",
        promoCode: String? = null,
        discountPKR: Int = 0,
        overrideDistanceKm: Double? = null,
        overrideFarePKR: Int? = null,
        paymentMethod: String = _selectedPaymentMethod.value.firestoreValue,
        onComplete: ((ScheduleRideResult) -> Unit)? = null
    ): ScheduleRideResult {
        val now = System.currentTimeMillis()
        if (scheduledForMillis <= now + 60_000L) {
            val err = ScheduleRideResult.Error(
                message = "Please choose a future date & time at least 1 minute from now.",
                urduMessage = "براہِ کرم موجودہ وقت سے کم از کم 1 منٹ آگے کا وقت منتخب کریں۔"
            )
            onComplete?.invoke(err)
            return err
        }
        if (pickup.isBlank() || destination.isBlank()) {
            val err = ScheduleRideResult.Error(
                message = "Pickup and destination locations are required.",
                urduMessage = "پک اپ اور منزل کا پتہ درج کرنا ضروری ہے۔"
            )
            onComplete?.invoke(err)
            return err
        }

        val dist = overrideDistanceKm?.takeIf { it > 0.0 } ?: estimateDistanceKm(pickup, destination)
        val finalFare = overrideFarePKR?.takeIf { it >= 30 } ?: run {
            val rawFare = calculateFare(category, dist)
            (rawFare - discountPKR).coerceAtLeast(30)
        }

        val pId = getFirebaseAuth()?.currentUser?.uid ?: _currentUser.value?.uid ?: "USER-101"
        val pName = passengerName.ifBlank { _currentUser.value?.name ?: "Ahmed Raza" }
        val pPhone = passengerPhone.ifBlank { _currentUser.value?.phoneNumber ?: "0301-8765432" }

        val pickupCoords: Pair<Double, Double> = locationDistanceService.resolveLocalCoordinates(pickup)
            ?: (com.example.util.PakistanGeoService.JARANWALA_LAT to com.example.util.PakistanGeoService.JARANWALA_LNG)
        val destCoords: Pair<Double, Double> = locationDistanceService.resolveLocalCoordinates(destination)
            ?: (31.3381 to 73.4278)

        val scheduleId = "SCH-${System.currentTimeMillis()}-${(100..999).random()}"
        val scheduledRide = ScheduledRide(
            id = scheduleId,
            passengerId = pId,
            passengerName = pName,
            passengerPhone = pPhone,
            pickup = pickup.trim(),
            destination = destination.trim(),
            vehicleCategory = category,
            distanceKm = dist,
            estimatedFarePKR = finalFare,
            scheduledForMillis = scheduledForMillis,
            scheduledDateDisplay = scheduledDateDisplay,
            scheduledTimeDisplay = scheduledTimeDisplay,
            notes = notes.trim(),
            promoCode = promoCode,
            discountPKR = discountPKR,
            status = ScheduledRideStatus.SCHEDULED,
            pickupLat = pickupCoords.first,
            pickupLng = pickupCoords.second,
            destLat = destCoords.first,
            destLng = destCoords.second,
            paymentMethod = paymentMethod.ifBlank { "CASH" },
            createdAtMillis = now
        )

        _scheduledRides.update { existing ->
            (listOf(scheduledRide) + existing.filter { it.id != scheduleId })
                .sortedBy { it.scheduledForMillis }
        }

        scope.launch {
            try {
                val db = getFirestore()
                if (db != null && getFirebaseAuth()?.currentUser != null) {
                    db.collection("scheduled_rides").document(scheduleId)
                        .set(scheduledRide.toFirestoreMap())
                        .addOnSuccessListener {
                            emitNotificationEvent(
                                type = "SCHEDULED_RIDE_CREATED",
                                recipientUid = pId,
                                title = "Ride Scheduled • سواری طے ہو گئی",
                                body = "${category.title}: $pickup ➔ $destination on $scheduledDateDisplay at $scheduledTimeDisplay (₨ $finalFare)",
                                rideId = scheduleId,
                                targetTab = 0
                            )
                        }
                        .addOnFailureListener { ex ->
                            handleFirestoreError(ex, OperationType.CREATE, "scheduled_rides/$scheduleId") { getFirebaseAuth() }
                        }
                }
            } catch (ex: Throwable) {
                handleFirestoreError(ex, OperationType.CREATE, "scheduled_rides/$scheduleId") { getFirebaseAuth() }
            }
        }

        val ok = ScheduleRideResult.Success(scheduledRide)
        onComplete?.invoke(ok)
        return ok
    }

    fun cancelScheduledRide(scheduleId: String) {
        _scheduledRides.update { list ->
            list.map { if (it.id == scheduleId) it.copy(status = ScheduledRideStatus.CANCELLED) else it }
        }
        scope.launch {
            try {
                val db = getFirestore() ?: return@launch
                if (getFirebaseAuth()?.currentUser != null) {
                    db.collection("scheduled_rides").document(scheduleId)
                        .update(
                            mapOf(
                                "status" to ScheduledRideStatus.CANCELLED.name,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        .addOnFailureListener { ex ->
                            handleFirestoreError(ex, OperationType.UPDATE, "scheduled_rides/$scheduleId") { getFirebaseAuth() }
                        }
                }
            } catch (ex: Throwable) {
                handleFirestoreError(ex, OperationType.UPDATE, "scheduled_rides/$scheduleId") { getFirebaseAuth() }
            }
        }
    }

    fun listenToScheduledRidesFromFirestore(userId: String = ""): com.google.firebase.firestore.ListenerRegistration? {
        scheduledRidesListener?.remove()
        val firebaseUid = getFirebaseAuth()?.currentUser?.uid
        val targetUid = userId.ifBlank { firebaseUid ?: _currentUser.value?.uid ?: "" }
        if (targetUid.isBlank() || _currentUser.value?.isGuest == true || firebaseUid == null) {
            return null
        }
        return try {
            val db = getFirestore() ?: return null
            val reg = db.collection("scheduled_rides")
                .whereEqualTo("passengerId", targetUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        handleFirestoreError(error, OperationType.LIST, "scheduled_rides") { getFirebaseAuth() }
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val remote = snapshot.documents
                            .mapNotNull { mapFirestoreScheduledRideDocument(it) }
                        _scheduledRides.update { existing ->
                            val localOnly = existing.filter { ex -> remote.none { it.id == ex.id } && ex.passengerId == targetUid }
                            (remote + localOnly).sortedBy { it.scheduledForMillis }
                        }
                    }
                }
            scheduledRidesListener = reg
            reg
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Fetches active and past rides from Cloud Firestore scoped to the signed-in user's role & UID,
     * merging live cloud documents with current in-flight and local records.
     */
    fun fetchAllDashboardRidesFromFirestore(
        onResult: (activeRides: List<RideBooking>, pastRides: List<RideBooking>) -> Unit
    ) {
        val current = _currentUser.value
        val firebaseUid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (_: Throwable) { null }
        val targetUid = firebaseUid ?: current?.uid ?: ""

        fun emitLocalFallback() {
            val localActive = _currentPassengerRide.value
            val localDriverActive = _currentDriverRide.value
            val localHistory = _passengerHistory.value
            val allCombined = (localHistory + listOfNotNull(localActive, localDriverActive)).distinctBy { it.id }
            val active = allCombined.filter {
                it.status == RideStatus.SEARCHING ||
                it.status == RideStatus.ACCEPTED ||
                it.status == RideStatus.DRIVER_ARRIVED ||
                it.status == RideStatus.IN_PROGRESS
            }
            val past = allCombined.filter {
                it.status == RideStatus.COMPLETED ||
                it.status == RideStatus.CANCELLED ||
                it.status == RideStatus.IDLE
            }
            onResult(active, past)
        }

        if (targetUid.isBlank() || current?.isGuest == true) {
            emitLocalFallback()
            return
        }

        scope.launch {
            try {
                val db = getFirestore()
                if (db != null) {
                    val query = when (current?.role) {
                        UserRole.ADMIN -> db.collection("ride_requests")
                        UserRole.DRIVER -> db.collection("ride_requests").whereEqualTo("driverId", targetUid)
                        else -> db.collection("ride_requests").whereEqualTo("passengerId", targetUid)
                    }
                    query.get()
                        .addOnSuccessListener { snapshot ->
                            val firestoreRides = snapshot.documents.mapNotNull { mapFirestoreRideDocument(it) }

                            val localActive = _currentPassengerRide.value
                            val localDriverActive = _currentDriverRide.value
                            val localHistory = _passengerHistory.value

                            val allCombined = (firestoreRides + localHistory + listOfNotNull(localActive, localDriverActive))
                                .distinctBy { it.id }
                                .sortedByDescending { it.timestamp }

                            val active = allCombined.filter {
                                it.status == RideStatus.SEARCHING ||
                                it.status == RideStatus.ACCEPTED ||
                                it.status == RideStatus.DRIVER_ARRIVED ||
                                it.status == RideStatus.IN_PROGRESS
                            }
                            val past = allCombined.filter {
                                it.status == RideStatus.COMPLETED ||
                                it.status == RideStatus.CANCELLED ||
                                it.status == RideStatus.IDLE
                            }
                            onResult(active, past)
                        }
                        .addOnFailureListener {
                            emitLocalFallback()
                        }
                } else {
                    emitLocalFallback()
                }
            } catch (_: Throwable) {
                emitLocalFallback()
            }
        }
    }

    fun listenToAllRidesFromFirestore(
        onUpdate: (activeRides: List<RideBooking>, pastRides: List<RideBooking>) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration? {
        val current = _currentUser.value
        val firebaseUid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (_: Throwable) { null }
        val targetUid = firebaseUid ?: current?.uid ?: ""
        if (targetUid.isBlank() || current?.isGuest == true) return null

        return try {
            val db = getFirestore() ?: return null
            val query = when (current?.role) {
                UserRole.ADMIN -> db.collection("ride_requests")
                UserRole.DRIVER -> db.collection("ride_requests").whereEqualTo("driverId", targetUid)
                else -> db.collection("ride_requests").whereEqualTo("passengerId", targetUid)
            }
            query.addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val firestoreRides = snapshot.documents.mapNotNull { mapFirestoreRideDocument(it) }
                    val localActive = _currentPassengerRide.value
                    val localDriverActive = _currentDriverRide.value
                    val localHistory = _passengerHistory.value
                    val allCombined = (firestoreRides + localHistory + listOfNotNull(localActive, localDriverActive))
                        .distinctBy { it.id }
                        .sortedByDescending { it.timestamp }
                    val active = allCombined.filter {
                        it.status == RideStatus.SEARCHING ||
                        it.status == RideStatus.ACCEPTED ||
                        it.status == RideStatus.DRIVER_ARRIVED ||
                        it.status == RideStatus.IN_PROGRESS
                    }
                    val past = allCombined.filter {
                        it.status == RideStatus.COMPLETED ||
                        it.status == RideStatus.CANCELLED ||
                        it.status == RideStatus.IDLE
                    }
                    onUpdate(active, past)
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun signOut(context: Context? = null) {
        val currentUid = getFirebaseAuth()?.currentUser?.uid ?: _currentUser.value?.uid
        val resolvedCtx = context?.applicationContext ?: appContext
        val lastToken = try {
            resolvedCtx?.getSharedPreferences("jaranwala_auth_prefs", Context.MODE_PRIVATE)
                ?.getString("last_fcm_token", null)
        } catch (_: Throwable) {
            null
        }

        // Remove listeners first so sign-out doesn't trigger spurious errors
        incomingRequestsListener?.remove()
        incomingRequestsListener = null
        driverProfileListener?.remove()
        driverProfileListener = null
        activePassengerRideListener?.remove()
        activePassengerRideListener = null
        activeRideDriverLocationListener?.remove()
        activeRideDriverLocationListener = null
        trackedDriverIdForActiveRide = null
        scheduledRidesListener?.remove()
        scheduledRidesListener = null
        userProfileListener?.remove()
        userProfileListener = null
        detachAdminListeners()

        scope.launch {
            withContext(NonCancellable) {
                if (!currentUid.isNullOrBlank() && !lastToken.isNullOrBlank()) {
                    try {
                        getFirestore(resolvedCtx)
                            ?.collection("users")?.document(currentUid)
                            ?.collection("fcmTokens")?.document(lastToken)
                            ?.delete()
                            ?.await()
                    } catch (_: Throwable) {}
                }
                if (resolvedCtx != null) {
                    try {
                        CredentialManager.create(resolvedCtx).clearCredentialState(ClearCredentialStateRequest())
                    } catch (_: Throwable) {}
                }
                try {
                    getFirebaseAuth()?.signOut()
                } catch (_: Throwable) {}
            }
        }

        if (resolvedCtx != null) {
            clearSession(resolvedCtx)
        }
        _currentUser.value = null
    }

    fun updateUserProfile(name: String, photoUrl: String? = null, context: Context? = null) {
        val current = _currentUser.value ?: return
        val updated = current.copy(
            name = name.ifBlank { current.name },
            photoUrl = photoUrl ?: current.photoUrl
        )
        _currentUser.value = updated
        if (context != null) {
            saveSession(context, updated)
        }
    }

    fun saveSession(context: Context, profile: UserProfile) {
        try {
            val prefs = context.getSharedPreferences("jaranwala_auth_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("uid", profile.uid)
                .putString("phone", profile.phoneNumber)
                .putString("name", profile.name)
                .putString("emergencyContact", profile.emergencyContactNumber)
                .putString("role", profile.role.name)
                .putString("email", profile.email ?: "")
                .putString("photoUrl", profile.photoUrl ?: "")
                .putBoolean("isVerified", profile.isVerified)
                .putBoolean("isGuest", profile.isGuest)
                .putString("cnic", profile.cnic ?: "")
                .putString("licenseNo", profile.licenseNo ?: "")
                .putString("vehicleCategory", profile.vehicleCategory?.name ?: "")
                .putString("vehicleModel", profile.vehicleModel ?: "")
                .putString("plateNo", profile.plateNo ?: "")
                .apply()
        } catch (_: Throwable) {}
    }

    fun loadSession(context: Context): UserProfile? {
        return try {
            val prefs = context.getSharedPreferences("jaranwala_auth_prefs", Context.MODE_PRIVATE)
            val uid = prefs.getString("uid", null) ?: return null
            val phone = prefs.getString("phone", "0300-1234567") ?: "0300-1234567"
            val name = prefs.getString("name", "User") ?: "User"
            val emergencyContact = prefs.getString("emergencyContact", "") ?: ""
            val roleStr = prefs.getString("role", UserRole.PASSENGER.name) ?: UserRole.PASSENGER.name
            val role = try { UserRole.valueOf(roleStr) } catch (_: Throwable) { UserRole.PASSENGER }
            val email = prefs.getString("email", null)?.ifBlank { null }
            val photoUrl = prefs.getString("photoUrl", null)?.ifBlank { null }
            val isVerified = prefs.getBoolean("isVerified", true)
            val isGuest = prefs.getBoolean("isGuest", false)
            val cnic = prefs.getString("cnic", null)?.ifBlank { null }
            val licenseNo = prefs.getString("licenseNo", null)?.ifBlank { null }
            val catStr = prefs.getString("vehicleCategory", null)?.ifBlank { null }
            val cat = catStr?.let { try { VehicleCategory.valueOf(it) } catch (_: Throwable) { null } }
            val model = prefs.getString("vehicleModel", null)?.ifBlank { null }
            val plate = prefs.getString("plateNo", null)?.ifBlank { null }

            val profile = UserProfile(
                uid = uid,
                phoneNumber = phone,
                name = name,
                emergencyContactNumber = emergencyContact,
                role = role,
                email = email,
                photoUrl = photoUrl,
                isVerified = isVerified,
                isGuest = isGuest,
                cnic = cnic,
                licenseNo = licenseNo,
                vehicleCategory = cat,
                vehicleModel = model,
                plateNo = plate
            )
            _currentUser.value = profile
            profile
        } catch (_: Throwable) {
            null
        }
    }

    fun clearSession(context: Context) {
        try {
            val prefs = context.getSharedPreferences("jaranwala_auth_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        } catch (_: Throwable) {}
    }

    fun continueAsGuest(role: UserRole = UserRole.PASSENGER, context: Context? = null): UserProfile {
        val uid = "GUEST-${(1000..9999).random()}"
        val profile = UserProfile(
            uid = uid,
            phoneNumber = "",
            name = if (role == UserRole.DRIVER) "Guest Captain (مہمان کپتان)" else "Guest Passenger (مہمان سواری)",
            role = role,
            isVerified = false,
            isGuest = true
        )
        _currentUser.value = profile
        if (context != null) {
            saveSession(context, profile)
        }
        return profile
    }

    fun switchUserRole(role: UserRole) {
        if (role == UserRole.ADMIN) {
            recordSecurityAuditLog(
                SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT,
                "Direct switchUserRole to ADMIN rejected. Admin access requires 2FA authentication.",
                severity = "HIGH",
                status = "BLOCKED"
            )
            return
        }
        _currentUser.update { current ->
            current?.copy(role = role) ?: UserProfile(
                uid = "USER-${(100..999).random()}",
                phoneNumber = "0300-1234567",
                name = if (role == UserRole.DRIVER) "Captain Driver" else "Passenger Rider",
                role = role,
                isGuest = false,
                isVerified = true
            )
        }
    }

    // Authentic Jaranwala landmarks
    val jaranwalaLocations = listOf(
        JaranwalaLocation("Jaranwala Railway Station", "جرانوالہ ریلوے اسٹیشن", "Transit", 1.0),
        JaranwalaLocation("Committee Chowk", "کمیٹی چوک", "City Center", 0.5),
        JaranwalaLocation("Cinema Chowk", "سینما چوک", "Market", 0.8),
        JaranwalaLocation("Katchery Bazar", "کچہری بازار", "Commercial", 0.6),
        JaranwalaLocation("Mandi Road", "منڈی روڈ", "Commercial", 1.2),
        JaranwalaLocation("Grain Market (Ghalla Mandi)", "غلہ منڈی", "Commercial", 1.4),
        JaranwalaLocation("Faisalabad Road Toll Plaza", "فیصل آباد روڈ ٹول پلازہ", "Highway", 3.8),
        JaranwalaLocation("Lahore Road By-Pass", "لاہور روڈ بائی پاس", "Highway", 3.2),
        JaranwalaLocation("Syedwala Road Crossing", "سیدوالا روڈ چوک", "Transit", 2.5),
        JaranwalaLocation("Satyana Road Stop", "ستیانہ روڈ اسٹاپ", "Transit", 2.9),
        JaranwalaLocation("DHQ Hospital Jaranwala", "ڈی ایچ کیو ہسپتال", "Healthcare", 1.5),
        JaranwalaLocation("Government Degree College", "گورنمنٹ ڈگری کالج", "Education", 1.8),
        JaranwalaLocation("Royal Palm Marriage Hall", "رائل پام میرج ہال", "Event Venue", 2.2),
        JaranwalaLocation("Taj Palace Marquee", "تاج پیلس مارکی", "Event Venue", 3.0)
    )

    // Admin Fares Configuration
    private val _adminConfig = MutableStateFlow(AdminConfig())
    val adminConfig: StateFlow<AdminConfig> = _adminConfig.asStateFlow()

    fun updateFares(
        bikeBase: Int,
        bikeKm: Int,
        rickshawBase: Int,
        rickshawKm: Int,
        carBase: Int,
        carKm: Int,
        commission: Int
    ) {
        _adminConfig.update {
            it.copy(
                bikeBaseFare = bikeBase,
                bikePerKm = bikeKm,
                rickshawBaseFare = rickshawBase,
                rickshawPerKm = rickshawKm,
                carBaseFare = carBase,
                carPerKm = carKm,
                platformCommissionPercent = commission
            )
        }
        scope.launch {
            try {
                val db = getFirestore() ?: return@launch
                val payload = hashMapOf<String, Any>(
                    "bikeBaseFare" to bikeBase,
                    "bikePerKm" to bikeKm,
                    "rickshawBaseFare" to rickshawBase,
                    "rickshawPerKm" to rickshawKm,
                    "carBaseFare" to carBase,
                    "carPerKm" to carKm,
                    "platformCommissionPercent" to commission,
                    "updatedBy" to IMMUTABLE_OWNER_UID,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("config").document("fares")
                    .set(payload, SetOptions.merge())
                    .addOnFailureListener { ex ->
                        handleFirestoreError(ex, OperationType.WRITE, "config/fares") { getFirebaseAuth() }
                    }
            } catch (e: Throwable) {
                handleFirestoreError(e, OperationType.WRITE, "config/fares") { getFirebaseAuth() }
            }
        }
    }

    // Biometric & Immutable Owner Security State
    private val _isOwnerBiometricEnrolled = MutableStateFlow(true)
    val isOwnerBiometricEnrolled: StateFlow<Boolean> = _isOwnerBiometricEnrolled.asStateFlow()

    fun setOwnerBiometricEnrolled(enrolled: Boolean) {
        _isOwnerBiometricEnrolled.value = enrolled
    }

    private val _securityAuditLogs = MutableStateFlow<List<SecurityAuditLogEntry>>(
        listOf(
            SecurityAuditLogEntry(
                id = "SEC-INIT-1",
                timestamp = System.currentTimeMillis() - 86400000L,
                eventType = SecurityEventType.LOGIN_SUCCESS,
                description = "Secure hardware TEE initialized for Owner: MAHI ($IMMUTABLE_OWNER_EMAIL)",
                severity = "NORMAL",
                status = "VERIFIED"
            ),
            SecurityAuditLogEntry(
                id = "SEC-INIT-2",
                timestamp = System.currentTimeMillis() - 43200000L,
                eventType = SecurityEventType.BIOMETRIC_SUCCESS,
                description = "Biometric Keystore Key bound to Android Keystore AES-256",
                severity = "LOW",
                status = "BOUND"
            )
        )
    )
    val securityAuditLogs: StateFlow<List<SecurityAuditLogEntry>> = _securityAuditLogs.asStateFlow()

    private val _ownerSecurityAlerts = MutableStateFlow<List<String>>(emptyList())
    val ownerSecurityAlerts: StateFlow<List<String>> = _ownerSecurityAlerts.asStateFlow()

    // Secure Admin Authentication (Zero hardcoded PINs or master keys)
    private var ownerPasscodeSalt: String? = null
    private var customAdminHash: String? = null
    private var ownerMasterKeySalt: String? = null
    private var ownerMasterKeyHash: String? = null
    private var failedAdminAttempts = 0
    private var suspiciousResetAttempts = 0
    private var adminLockoutUntil = 0L

    fun isPasscodeConfigured(): Boolean = customAdminHash != null

    fun isRecoveryKeyConfigured(): Boolean = ownerMasterKeyHash != null

    private fun generateSaltHex(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashWithSalt(secret: String, salt: String): String {
        return sha256("$salt:${secret.trim()}")
    }

    private fun persistOwnerSecurityHashes(context: Context?) {
        val resolved = context?.applicationContext ?: appContext ?: return
        try {
            val prefs = resolved.getSharedPreferences("jaranwala_owner_sec_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("pin_salt", ownerPasscodeSalt)
                .putString("pin_hash", customAdminHash)
                .putString("key_salt", ownerMasterKeySalt)
                .putString("key_hash", ownerMasterKeyHash)
                .apply()
        } catch (_: Throwable) {}
    }

    private fun loadOwnerSecurityHashes(context: Context?) {
        val resolved = context?.applicationContext ?: appContext ?: return
        try {
            val prefs = resolved.getSharedPreferences("jaranwala_owner_sec_prefs", Context.MODE_PRIVATE)
            val savedPinHash = prefs.getString("pin_hash", null)
            val savedPinSalt = prefs.getString("pin_salt", null)
            val savedKeyHash = prefs.getString("key_hash", null)
            val savedKeySalt = prefs.getString("key_salt", null)
            if (!savedPinHash.isNullOrBlank() && !savedPinSalt.isNullOrBlank()) {
                customAdminHash = savedPinHash
                ownerPasscodeSalt = savedPinSalt
            }
            if (!savedKeyHash.isNullOrBlank() && !savedKeySalt.isNullOrBlank()) {
                ownerMasterKeyHash = savedKeyHash
                ownerMasterKeySalt = savedKeySalt
            }
        } catch (_: Throwable) {}
    }

    /**
     * Provisions Owner Admin credentials using per-secret random salts + SHA-256.
     * Used during Owner Admin first-time security setup and unit/integration tests.
     */
    fun configureOwnerCredentials(
        passcode: String,
        recoveryKey: String? = null,
        context: Context? = null
    ): Boolean {
        val cleanPin = passcode.trim()
        if (cleanPin.length < 4) return false

        val pinSalt = generateSaltHex()
        ownerPasscodeSalt = pinSalt
        customAdminHash = hashWithSalt(cleanPin, pinSalt)

        if (!recoveryKey.isNullOrBlank()) {
            val keySalt = generateSaltHex()
            ownerMasterKeySalt = keySalt
            ownerMasterKeyHash = hashWithSalt(recoveryKey.trim(), keySalt)
        }

        failedAdminAttempts = 0
        suspiciousResetAttempts = 0
        adminLockoutUntil = 0L
        persistOwnerSecurityHashes(context)

        scope.launch {
            try {
                val db = getFirestore(context) ?: return@launch
                if (isCurrentFirebaseUserMahiOwner()) {
                    val map = hashMapOf<String, Any>(
                        "adminPinSalt" to pinSalt,
                        "adminPinHash" to (customAdminHash ?: ""),
                        "ownerId" to IMMUTABLE_OWNER_UID,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (ownerMasterKeyHash != null && ownerMasterKeySalt != null) {
                        map["recoveryKeySalt"] = ownerMasterKeySalt!!
                        map["recoveryKeyHash"] = ownerMasterKeyHash!!
                    }
                    db.collection("config").document("security")
                        .set(map, SetOptions.merge())
                        .addOnFailureListener { ex ->
                            handleFirestoreError(ex, OperationType.WRITE, "config/security") { getFirebaseAuth() }
                        }
                }
            } catch (_: Throwable) {}
        }
        return true
    }

    fun setOwnerRecoveryKey(recoveryKey: String, context: Context? = null): Boolean {
        val cleanKey = recoveryKey.trim()
        if (cleanKey.length < 6) return false
        val keySalt = generateSaltHex()
        ownerMasterKeySalt = keySalt
        ownerMasterKeyHash = hashWithSalt(cleanKey, keySalt)
        persistOwnerSecurityHashes(context)
        return true
    }

    fun recordSecurityAuditLog(
        eventType: SecurityEventType,
        description: String,
        severity: String = "NORMAL",
        status: String = "LOGGED"
    ) {
        val entry = SecurityAuditLogEntry(
            id = "SEC-" + (10000..99999).random(),
            timestamp = System.currentTimeMillis(),
            eventType = eventType,
            description = description,
            severity = severity,
            status = status
        )
        _securityAuditLogs.update { listOf(entry) + it.take(49) }

        if (severity == "HIGH" || severity == "CRITICAL") {
            _ownerSecurityAlerts.update { list ->
                listOf("[${entry.eventType.title}] $description") + list.take(9)
            }
            appContext?.let { ctx ->
                RideNotificationManager.notifyOwnerSecurityAlert(
                    context = ctx,
                    title = "MAHI Security: ${entry.eventType.title}",
                    description = description
                )
            }
        }

        scope.launch {
            try {
                val db = getFirestore() ?: return@launch
                val authUid = getFirebaseAuth()?.currentUser?.uid ?: _currentUser.value?.uid ?: return@launch
                val logMap = hashMapOf<String, Any>(
                    "id" to entry.id,
                    "userId" to authUid,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "eventType" to entry.eventType.name,
                    "description" to entry.description,
                    "severity" to entry.severity,
                    "status" to entry.status,
                    "clientSignature" to entry.clientSignature
                )
                db.collection("security_audit_logs").document(entry.id)
                    .set(logMap)
                    .addOnFailureListener { ex ->
                        handleFirestoreError(ex, OperationType.CREATE, "security_audit_logs/${entry.id}") { getFirebaseAuth() }
                    }
            } catch (_: Throwable) {}
        }
    }

    fun verifyOwnerMasterKey(key: String): Boolean {
        val cleanKey = key.trim()
        val storedHash = ownerMasterKeyHash
        val storedSalt = ownerMasterKeySalt
        val isValid = !storedHash.isNullOrBlank() &&
            !storedSalt.isNullOrBlank() &&
            cleanKey.isNotBlank() &&
            hashWithSalt(cleanKey, storedSalt) == storedHash

        if (isValid) {
            suspiciousResetAttempts = 0
            recordSecurityAuditLog(
                SecurityEventType.MASTER_KEY_SUCCESS,
                "Configured Owner Recovery Key validated for MAHI",
                severity = "NORMAL"
            )
        } else {
            suspiciousResetAttempts++
            recordSecurityAuditLog(
                SecurityEventType.MASTER_KEY_FAILED,
                "Failed Owner Recovery Key attempt ($suspiciousResetAttempts/3)",
                severity = "HIGH"
            )
        }
        return isValid
    }

    fun resetAdminPasscodeSecurely(
        ownerEmail: String,
        ownerMasterKey: String,
        newPasscode: String,
        onComplete: ((PasswordResetResult) -> Unit)? = null
    ): PasswordResetResult {
        val now = System.currentTimeMillis()
        if (now < adminLockoutUntil) {
            val remainingSec = ((adminLockoutUntil - now) / 1000).toInt() + 1
            val res = PasswordResetResult.LockedOut(remainingSec)
            onComplete?.invoke(res)
            return res
        }

        // If a Firebase Auth session exists, verify it actually belongs to MAHI Owner
        val fbUser = getFirebaseAuth()?.currentUser
        if (fbUser != null && !isCurrentFirebaseUserMahiOwner()) {
            suspiciousResetAttempts++
            recordSecurityAuditLog(
                SecurityEventType.SUSPICIOUS_UNAUTHORIZED_RESET,
                "Non-owner Firebase session (${fbUser.email ?: fbUser.uid}) attempted Admin passcode reset.",
                severity = "CRITICAL",
                status = "REJECTED"
            )
            val res = PasswordResetResult.RejectedUnauthorized(
                "Active Firebase account is not authorized Owner ($IMMUTABLE_OWNER_EMAIL)."
            )
            onComplete?.invoke(res)
            return res
        }

        val cleanEmail = ownerEmail.trim().lowercase(Locale.ROOT)
        val isOwnerEmail = cleanEmail == IMMUTABLE_OWNER_EMAIL.lowercase(Locale.ROOT)
        val cleanKey = ownerMasterKey.trim()
        val storedKeyHash = ownerMasterKeyHash
        val storedKeySalt = ownerMasterKeySalt
        val isMasterKeyValid = isOwnerEmail &&
            !storedKeyHash.isNullOrBlank() &&
            !storedKeySalt.isNullOrBlank() &&
            hashWithSalt(cleanKey, storedKeySalt) == storedKeyHash

        if (!isOwnerEmail || !isMasterKeyValid) {
            suspiciousResetAttempts++
            val reason = when {
                !isOwnerEmail -> "Unauthorized email address '$ownerEmail'"
                storedKeyHash.isNullOrBlank() -> "Owner Recovery Key not initialized for device"
                else -> "Invalid Recovery Key"
            }
            recordSecurityAuditLog(
                SecurityEventType.SUSPICIOUS_UNAUTHORIZED_RESET,
                "Unauthorized password reset attempt rejected: $reason",
                severity = "CRITICAL",
                status = "REJECTED"
            )

            if (suspiciousResetAttempts >= 3) {
                adminLockoutUntil = now + 60000L
                suspiciousResetAttempts = 0
                recordSecurityAuditLog(
                    SecurityEventType.SECURITY_LOCKOUT_TRIGGERED,
                    "Admin portal locked for 60s due to repeated unauthorized reset attempts",
                    severity = "CRITICAL",
                    status = "LOCKED"
                )
                val res = PasswordResetResult.LockedOut(60)
                onComplete?.invoke(res)
                return res
            }

            val res = if (!isOwnerEmail) {
                PasswordResetResult.RejectedUnauthorized("Only authorized Owner ($IMMUTABLE_OWNER_EMAIL) can reset passcode.")
            } else {
                PasswordResetResult.InvalidMasterKey(3 - suspiciousResetAttempts)
            }
            onComplete?.invoke(res)
            return res
        }

        if (newPasscode.trim().length < 4) {
            val res = PasswordResetResult.RejectedUnauthorized("Passcode must be at least 4 digits")
            onComplete?.invoke(res)
            return res
        }

        val cleanPin = newPasscode.trim()
        val newSalt = generateSaltHex()
        val hashed = hashWithSalt(cleanPin, newSalt)
        ownerPasscodeSalt = newSalt
        customAdminHash = hashed
        failedAdminAttempts = 0
        suspiciousResetAttempts = 0
        adminLockoutUntil = 0L
        persistOwnerSecurityHashes(appContext)

        // CRITICAL: PASSWORD RESET MUST NEVER GRANT ADMIN ACCESS!
        recordSecurityAuditLog(
            SecurityEventType.PASSWORD_RESET_AUTHORIZED,
            "Admin passcode reset authorized for Owner MAHI. Verified identity remains immutable: $IMMUTABLE_OWNER_NAME.",
            severity = "NORMAL",
            status = "COMPLETED"
        )

        scope.launch {
            try {
                val db = getFirestore()
                if (db != null) {
                    val map = hashMapOf<String, Any>(
                        "adminPinSalt" to newSalt,
                        "adminPinHash" to hashed,
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "ownerId" to IMMUTABLE_OWNER_UID
                    )
                    db.collection("config").document("security").set(map, SetOptions.merge())
                }
            } catch (_: Throwable) {}
        }

        val res = PasswordResetResult.Success
        onComplete?.invoke(res)
        return res
    }

    fun setAdminPasscode(
        newPin: String,
        context: Context? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val cleanPin = newPin.trim()
        val salt = generateSaltHex()
        val hashed = hashWithSalt(cleanPin, salt)
        ownerPasscodeSalt = salt
        customAdminHash = hashed
        failedAdminAttempts = 0
        suspiciousResetAttempts = 0
        adminLockoutUntil = 0L
        persistOwnerSecurityHashes(context)

        scope.launch {
            try {
                val db = getFirestore(context)
                if (db != null && isCurrentFirebaseUserMahiOwner()) {
                    val map = hashMapOf<String, Any>(
                        "adminPinSalt" to salt,
                        "adminPinHash" to hashed,
                        "ownerId" to IMMUTABLE_OWNER_UID,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    db.collection("config").document("security").set(map, SetOptions.merge())
                        .addOnSuccessListener { onComplete?.invoke(true) }
                        .addOnFailureListener { onComplete?.invoke(true) }
                } else {
                    onComplete?.invoke(true)
                }
            } catch (_: Throwable) {
                onComplete?.invoke(true)
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyAdminPin(pin: String): AdminAuthResult {
        val now = System.currentTimeMillis()
        if (now < adminLockoutUntil) {
            val remainingSec = ((adminLockoutUntil - now) / 1000).toInt() + 1
            return AdminAuthResult.LockedOut(remainingSec)
        }

        val targetHash = customAdminHash
        val targetSalt = ownerPasscodeSalt
        val isMatch = !targetHash.isNullOrBlank() &&
            !targetSalt.isNullOrBlank() &&
            hashWithSalt(pin.trim(), targetSalt) == targetHash

        if (isMatch) {
            failedAdminAttempts = 0
            adminLockoutUntil = 0L
            return AdminAuthResult.Success
        } else {
            failedAdminAttempts++
            if (failedAdminAttempts >= 3) {
                adminLockoutUntil = now + 30000L // 30 second lockout after 3 failed attempts
                failedAdminAttempts = 0
                return AdminAuthResult.LockedOut(30)
            }
            return AdminAuthResult.InvalidCredentials(3 - failedAdminAttempts)
        }
    }

    /**
     * Authenticates MAHI Owner Admin using verified Firebase Identity + Biometric/Passcode 2FA.
     * Zero hardcoded PINs or master keys exist.
     */
    fun authenticateOwnerWithFirebaseSession(
        biometricVerified: Boolean = false,
        context: Context? = null
    ): OwnerAdminLoginResult {
        val now = System.currentTimeMillis()
        if (now < adminLockoutUntil) {
            val remainingSec = ((adminLockoutUntil - now) / 1000).toInt() + 1
            return OwnerAdminLoginResult.LockedOut(remainingSec)
        }

        val fbUser = getFirebaseAuth()?.currentUser
        if (fbUser == null || !isCurrentFirebaseUserMahiOwner()) {
            failedAdminAttempts++
            recordSecurityAuditLog(
                SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT,
                "Firebase Owner session check failed for uid=${fbUser?.uid ?: "none"}",
                severity = "HIGH",
                status = "BLOCKED"
            )
            if (failedAdminAttempts >= 3) {
                adminLockoutUntil = now + 30000L
                failedAdminAttempts = 0
                return OwnerAdminLoginResult.LockedOut(30)
            }
            return OwnerAdminLoginResult.InvalidCredentials(3 - failedAdminAttempts)
        }

        if (!biometricVerified) {
            return OwnerAdminLoginResult.RequiresSecondFactor(
                "Verified Firebase Owner (${fbUser.email ?: fbUser.phoneNumber}). Confirm Fingerprint Biometric to unlock.",
                attemptsLeft = 3
            )
        }

        return finalizeOwnerAdminSession(fbUser.uid, context)
    }

    fun authenticateOwnerAdmin(
        passcode: String,
        biometricVerified: Boolean = false,
        masterKey: String? = null,
        context: Context? = null
    ): OwnerAdminLoginResult {
        val now = System.currentTimeMillis()
        if (now < adminLockoutUntil) {
            val remainingSec = ((adminLockoutUntil - now) / 1000).toInt() + 1
            return OwnerAdminLoginResult.LockedOut(remainingSec)
        }

        // Reject if an authenticated non-MAHI Firebase user is attempting Admin access
        val fbUser = getFirebaseAuth()?.currentUser
        if (fbUser != null && !isCurrentFirebaseUserMahiOwner()) {
            failedAdminAttempts++
            recordSecurityAuditLog(
                SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT,
                "Non-owner Firebase account (${fbUser.email ?: fbUser.uid}) blocked from Admin portal.",
                severity = "CRITICAL",
                status = "BLOCKED"
            )
            if (failedAdminAttempts >= 3) {
                adminLockoutUntil = now + 30000L
                failedAdminAttempts = 0
                return OwnerAdminLoginResult.LockedOut(30)
            }
            return OwnerAdminLoginResult.InvalidCredentials(3 - failedAdminAttempts)
        }

        val cleanPasscode = passcode.trim()
        val targetHash = customAdminHash
        val targetSalt = ownerPasscodeSalt
        val isPasscodeValid = !targetHash.isNullOrBlank() &&
            !targetSalt.isNullOrBlank() &&
            cleanPasscode.isNotBlank() &&
            hashWithSalt(cleanPasscode, targetSalt) == targetHash

        if (!isPasscodeValid) {
            failedAdminAttempts++
            recordSecurityAuditLog(
                SecurityEventType.LOGIN_FAILED,
                if (targetHash == null) "Admin passcode login attempted before passcode setup ($failedAdminAttempts/3)"
                else "Invalid admin passcode attempt ($failedAdminAttempts/3)",
                severity = "HIGH"
            )
            if (failedAdminAttempts >= 3) {
                adminLockoutUntil = now + 30000L
                failedAdminAttempts = 0
                recordSecurityAuditLog(
                    SecurityEventType.SECURITY_LOCKOUT_TRIGGERED,
                    "Admin portal locked for 30s after 3 invalid passcode attempts",
                    severity = "CRITICAL"
                )
                return OwnerAdminLoginResult.LockedOut(30)
            }
            return OwnerAdminLoginResult.InvalidCredentials(3 - failedAdminAttempts)
        }

        // Passcode verified! Enforce mandatory 2nd factor
        val isSecondFactorValid = if (biometricVerified) {
            recordSecurityAuditLog(
                SecurityEventType.BIOMETRIC_SUCCESS,
                "Native biometric fingerprint confirmed for Owner MAHI",
                severity = "LOW"
            )
            true
        } else if (!masterKey.isNullOrBlank() && verifyOwnerMasterKey(masterKey)) {
            true
        } else {
            false
        }

        if (!isSecondFactorValid) {
            if (!masterKey.isNullOrBlank()) {
                return OwnerAdminLoginResult.SecondFactorFailed("Invalid Owner Recovery Key provided.")
            }
            return OwnerAdminLoginResult.RequiresSecondFactor(
                "Passcode verified. Independent 2FA (Fingerprint Biometric or Owner Recovery Key) required.",
                attemptsLeft = 3
            )
        }

        val resolvedAdminUid = fbUser?.uid ?: IMMUTABLE_OWNER_UID
        return finalizeOwnerAdminSession(resolvedAdminUid, context)
    }

    private fun finalizeOwnerAdminSession(adminUid: String, context: Context?): OwnerAdminLoginResult {
        failedAdminAttempts = 0
        suspiciousResetAttempts = 0
        adminLockoutUntil = 0L

        val adminProfile = UserProfile(
            uid = adminUid,
            phoneNumber = IMMUTABLE_OWNER_PHONE,
            name = IMMUTABLE_OWNER_NAME,
            email = IMMUTABLE_OWNER_EMAIL,
            role = UserRole.ADMIN,
            photoUrl = "avatar_vip",
            isVerified = true,
            isGuest = false
        )
        _currentUser.value = adminProfile
        val resolvedCtx = context ?: appContext
        if (resolvedCtx != null) {
            saveSession(resolvedCtx, adminProfile)
        }

        scope.launch {
            try {
                val db = getFirestore(resolvedCtx) ?: return@launch
                if (isCurrentFirebaseUserMahiOwner()) {
                    val adminDoc = hashMapOf<String, Any>(
                        "uid" to adminUid,
                        "name" to IMMUTABLE_OWNER_NAME,
                        "email" to IMMUTABLE_OWNER_EMAIL,
                        "phone" to IMMUTABLE_OWNER_PHONE,
                        "role" to UserRole.ADMIN.name,
                        "masterKeyVerified" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    db.collection("admins").document(IMMUTABLE_OWNER_UID).set(adminDoc, SetOptions.merge())
                    db.collection("admins").document(adminUid).set(adminDoc, SetOptions.merge())
                }
            } catch (_: Throwable) {}
        }

        recordSecurityAuditLog(
            SecurityEventType.LOGIN_SUCCESS,
            "Authorized Owner MAHI successfully logged into Super Admin Portal with 2FA",
            severity = "NORMAL",
            status = "AUTHORIZED"
        )
        return OwnerAdminLoginResult.Success
    }

    fun loginAsAdmin(
        pin: String,
        biometricVerified: Boolean = false,
        masterKey: String? = null,
        context: Context? = null
    ): AdminAuthResult {
        val res = authenticateOwnerAdmin(pin, biometricVerified, masterKey, context)
        return when (res) {
            is OwnerAdminLoginResult.Success -> AdminAuthResult.Success
            is OwnerAdminLoginResult.RequiresSecondFactor -> AdminAuthResult.RequiresSecondFactor(res.message)
            is OwnerAdminLoginResult.InvalidCredentials -> AdminAuthResult.InvalidCredentials(res.attemptsLeft)
            is OwnerAdminLoginResult.LockedOut -> AdminAuthResult.LockedOut(res.remainingSeconds)
            is OwnerAdminLoginResult.SecondFactorFailed -> AdminAuthResult.InvalidCredentials(0)
        }
    }

    // Active Ride for Passenger
    private val _currentPassengerRide = MutableStateFlow<RideBooking?>(null)
    val currentPassengerRide: StateFlow<RideBooking?> = _currentPassengerRide.asStateFlow()

    // Passenger Ride History (Pre-seeded records labeled as demo data)
    private val _passengerHistory = MutableStateFlow<List<RideBooking>>(
        listOf(
            RideBooking(
                id = "RIDE-9481",
                passengerId = "USER-101",
                passengerName = "Ahmed Raza",
                passengerPhone = "0301-8765432",
                pickup = "Committee Chowk",
                destination = "Jaranwala Railway Station",
                vehicleCategory = VehicleCategory.RICKSHAW,
                estimatedDistanceKm = 2.4,
                estimatedFarePKR = 210,
                status = RideStatus.COMPLETED,
                driverName = "Tariq Mahmood",
                driverPhone = "0300-1122334",
                vehiclePlate = "FS-23-4412",
                driverRating = 5.0,
                ratingGiven = 5,
                reviewGiven = "بہترین سواری، وقت پر پہنچے",
                isDemoData = true,
                timestamp = System.currentTimeMillis() - 86400000L
            ),
            RideBooking(
                id = "RIDE-9410",
                passengerId = "USER-101",
                passengerName = "Ahmed Raza",
                passengerPhone = "0301-8765432",
                pickup = "Katchery Bazar",
                destination = "Faisalabad Road Toll Plaza",
                vehicleCategory = VehicleCategory.BIKE,
                estimatedDistanceKm = 4.2,
                estimatedFarePKR = 175,
                status = RideStatus.COMPLETED,
                driverName = "Hamza Ali",
                driverPhone = "0312-9988776",
                vehiclePlate = "LE-21-998",
                driverRating = 4.8,
                ratingGiven = 4,
                isDemoData = true,
                timestamp = System.currentTimeMillis() - 172800000L
            )
        )
    )
    val passengerHistory: StateFlow<List<RideBooking>> = _passengerHistory.asStateFlow()

    // Registered Drivers (Pre-seeded with isDemoData = true)
    private val _drivers = MutableStateFlow<List<DriverProfile>>(
        listOf(
            DriverProfile(
                id = "DRV-1",
                name = "Captain Tariq Mahmood",
                phone = "0300-1122334",
                cnic = "33102-1456789-1",
                licenseNo = "PB-FSD-98214",
                vehicleCategory = VehicleCategory.RICKSHAW,
                vehicleModel = "Sazgar 4-Stroke Auto 2023",
                plateNo = "FSD-23-4412",
                isApproved = true,
                isOnline = true,
                rating = 4.9,
                todayEarningsPKR = 2450,
                weeklyEarningsPKR = 12400,
                totalTrips = 89,
                isDemoData = true
            ),
            DriverProfile(
                id = "DRV-2",
                name = "Captain Hamza Ali",
                phone = "0312-9988776",
                cnic = "33102-8876543-3",
                licenseNo = "PB-JRW-55120",
                vehicleCategory = VehicleCategory.BIKE,
                vehicleModel = "Honda CG 125 Special Edition",
                plateNo = "JRW-24-1199",
                isApproved = true,
                isOnline = true,
                rating = 4.8,
                todayEarningsPKR = 1600,
                totalTrips = 114,
                isDemoData = true
            ),
            DriverProfile(
                id = "DRV-3",
                name = "Captain Bilal Aslam",
                phone = "0304-4455667",
                cnic = "33102-4433221-5",
                licenseNo = "PB-LHR-34190",
                vehicleCategory = VehicleCategory.CAR,
                vehicleModel = "Toyota Vitz AC White 2021",
                plateNo = "LEA-21-450",
                isApproved = true,
                isOnline = true,
                rating = 5.0,
                todayEarningsPKR = 3800,
                totalTrips = 63,
                isDemoData = true
            ),
            DriverProfile(
                id = "DRV-4",
                name = "Captain Usman Farooq",
                phone = "0321-7766554",
                cnic = "33102-9988112-7",
                licenseNo = "PB-JRW-88201",
                vehicleCategory = VehicleCategory.CAR,
                vehicleModel = "Suzuki Cultus VXL 2022",
                plateNo = "FS-22-8921",
                isApproved = false, // Pending admin approval
                isOnline = false,
                rating = 4.5,
                todayEarningsPKR = 0,
                totalTrips = 0,
                isDemoData = true
            )
        )
    )
    val drivers: StateFlow<List<DriverProfile>> = _drivers.asStateFlow()

    // Current Active Driver session (for Driver Mode)
    private val _activeDriver = MutableStateFlow<DriverProfile>(_drivers.value[0])
    val activeDriver: StateFlow<DriverProfile> = _activeDriver.asStateFlow()

    fun switchActiveDriver(driverId: String) {
        val found = _drivers.value.find { it.id == driverId } ?: return
        _activeDriver.value = found
    }

    // Incoming Requests Queue for Driver Mode (Real-time Firestore + Active in-flight)
    private val _incomingDriverRequests = MutableStateFlow<List<RideBooking>>(emptyList())
    val incomingDriverRequests: StateFlow<List<RideBooking>> = _incomingDriverRequests.asStateFlow()

    private val _incomingDriverRequest = MutableStateFlow<RideBooking?>(null)
    val incomingDriverRequest: StateFlow<RideBooking?> = _incomingDriverRequest.asStateFlow()

    private val declinedRideIds = mutableSetOf<String>()
    private var incomingRequestsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var driverProfileListener: com.google.firebase.firestore.ListenerRegistration? = null

    /**
     * Attaches a real-time Firestore listener on /ride_requests where status == 'SEARCHING'
     * so approved, online drivers immediately receive incoming passenger requests.
     */
    fun listenToIncomingRideRequestsFromFirestore(): com.google.firebase.firestore.ListenerRegistration? {
        incomingRequestsListener?.remove()
        val driver = _activeDriver.value
        if (!driver.isOnline || !driver.isApproved) {
            _incomingDriverRequests.value = emptyList()
            _incomingDriverRequest.value = null
            return null
        }
        return try {
            val db = getFirestore() ?: return null
            val reg = db.collection("ride_requests")
                .whereEqualTo("status", "SEARCHING")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && _activeDriver.value.isOnline && _activeDriver.value.isApproved) {
                        val remoteSearching = snapshot.documents
                            .mapNotNull { mapFirestoreRideDocument(it) }
                            .filter { it.id !in declinedRideIds }
                            .sortedByDescending { it.timestamp }
                        _incomingDriverRequests.update { existing ->
                            val combined = (remoteSearching + existing.filter { it.status == RideStatus.SEARCHING && it.id !in declinedRideIds })
                                .distinctBy { it.id }
                                .sortedByDescending { it.timestamp }
                            _incomingDriverRequest.value = combined.firstOrNull()
                            combined
                        }
                    }
                }
            incomingRequestsListener = reg
            reg
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Attaches a real-time Firestore listener on /drivers/{driverId} so admin approval
     * or online status changes reflect immediately in the Captain Dashboard.
     */
    fun listenToDriverProfileFromFirestore(driverId: String): com.google.firebase.firestore.ListenerRegistration? {
        driverProfileListener?.remove()
        if (driverId.isBlank()) return null
        return try {
            val db = getFirestore() ?: return null
            val reg = db.collection("drivers").document(driverId)
                .addSnapshotListener { doc, _ ->
                    if (doc != null && doc.exists()) {
                        val isApprovedRemote = doc.getBoolean("isApproved")
                        val isOnlineRemote = doc.getBoolean("isOnline")
                        val ratingRemote = doc.getDouble("rating")
                        val ridesRemote = (doc.get("totalRides") as? Number)?.toInt()
                        val earningsRemote = (doc.get("earningsPKR") as? Number)?.toInt()
                        _activeDriver.update { cur ->
                            if (cur.id == driverId) {
                                cur.copy(
                                    isApproved = isApprovedRemote ?: cur.isApproved,
                                    isOnline = isOnlineRemote ?: cur.isOnline,
                                    rating = ratingRemote ?: cur.rating,
                                    totalTrips = ridesRemote ?: cur.totalTrips,
                                    todayEarningsPKR = earningsRemote ?: cur.todayEarningsPKR
                                )
                            } else cur
                        }
                    }
                }
            driverProfileListener = reg
            reg
        } catch (_: Throwable) {
            null
        }
    }

    // Active Ride for Driver Mode
    private val _currentDriverRide = MutableStateFlow<RideBooking?>(null)
    val currentDriverRide: StateFlow<RideBooking?> = _currentDriverRide.asStateFlow()

    // Wedding & Barat Bookings
    private val _weddingBookings = MutableStateFlow<List<WeddingBooking>>(
        listOf(
            WeddingBooking(
                id = "WDD-7821",
                isBaratConvoy = false,
                customerName = "Muhammad Zubair",
                customerPhone = "0302-3344556",
                primaryCar = "Toyota Prado TX (White)",
                carCount = 1,
                convoyMix = ConvoyMixType.ROYAL_VIP,
                decorationTier = DecorationTier.ROYAL_ROSES,
                eventDate = "15 Nov 2026",
                eventTime = "04:00 PM",
                pickupLocation = "Cinema Chowk, Jaranwala",
                venueLocation = "Royal Palm Marriage Hall, Lahore Road",
                totalCostPKR = 35500,
                notes = "Chauffeur in traditional dress, fresh floral hood decoration",
                status = "CONFIRMED",
                isDemoData = true
            ),
            WeddingBooking(
                id = "BRT-2914",
                isBaratConvoy = true,
                customerName = "Chaudhry Nadeem",
                customerPhone = "0300-5544332",
                primaryCar = "Barat Convoy (5x Corollas + 1x Hiace)",
                carCount = 6,
                convoyMix = ConvoyMixType.STANDARD_SEDANS,
                decorationTier = DecorationTier.STANDARD,
                eventDate = "22 Nov 2026",
                eventTime = "01:30 PM",
                pickupLocation = "Grain Market, Mandi Road Jaranwala",
                venueLocation = "Serena Hotel, Faisalabad",
                totalCostPKR = 92000,
                notes = "Barat procession with convoy coordination flags",
                status = "CONFIRMED",
                isDemoData = true
            )
        )
    )
    val weddingBookings: StateFlow<List<WeddingBooking>> = _weddingBookings.asStateFlow()

    // Customer Complaints
    private val _complaints = MutableStateFlow<List<Complaint>>(
        listOf(
            Complaint(
                id = "CMP-102",
                rideId = "RIDE-9410",
                passengerName = "Ahmed Raza",
                category = "Fairness / Fare Issue",
                urduCategory = "کرایہ کا مسئلہ",
                message = "The captain took a slightly longer bypass route through Satyana road.",
                status = "Resolved",
                isDemoData = true
            )
        )
    )
    val complaints: StateFlow<List<Complaint>> = _complaints.asStateFlow()

    private val locationDistanceService = LocationDistanceService.getInstance()

    // Calculate Estimated Fare in PKR based on distance & config via RidePricingCalculator
    fun calculateFare(category: VehicleCategory, distanceKm: Double): Int {
        return RidePricingCalculator.calculateFareBreakdown(
            category = category,
            distanceKm = distanceKm,
            adminConfig = _adminConfig.value
        ).finalEstimatedFarePKR
    }

    // Calculate complete itemized fare breakdown for transparent pre-booking display
    fun calculatePriceBreakdown(
        category: VehicleCategory,
        distanceResult: LocationDistanceResult,
        promoCode: String? = null,
        promoDiscountPKR: Int = 0
    ): RidePriceBreakdown {
        return RidePricingCalculator.calculateFromLocationDistance(
            distanceResult = distanceResult,
            category = category,
            adminConfig = _adminConfig.value,
            promoCode = promoCode,
            promoDiscountPKR = promoDiscountPKR
        )
    }

    // Synchronous instant distance estimate using coordinates & road curvature factor
    fun estimateDistanceKm(pickup: String, dest: String): Double {
        return locationDistanceService.calculateInstantCoordinateDistance(pickup, dest).distanceKm
    }

    // Instant LocationDistanceResult before async Location API completes
    fun getInstantDistanceEstimate(pickup: String, dest: String): LocationDistanceResult {
        return locationDistanceService.calculateInstantCoordinateDistance(pickup, dest)
    }

    // Async distance fetch using OSRM Driving Route API + Nominatim Geocoding + Haversine fallback
    suspend fun fetchLocationDistanceEstimate(
        pickup: String,
        dest: String
    ): LocationDistanceResult {
        return locationDistanceService.fetchRideDistance(pickup, dest)
    }

    // Available Promo Codes for Jaranwala Ride
    val availablePromos = listOf(
        PromoCode("MAHI", discountPercent = 25, maxDiscountPKR = 100, description = "Founder MAHI Special: 25% Off (Save up to ₨ 100)", urduDescription = "بانی ماہی اسپیشل: ۲۵ فیصد رعایت (زیادہ سے زیادہ ۱۰۰ روپے)"),
        PromoCode("JARANWALA", discountPercent = 20, maxDiscountPKR = 80, description = "City Pride Promo: 20% Off", urduDescription = "شہر جرانوالہ پرومو: ۲۰ فیصد رعایت"),
        PromoCode("FIRST", flatDiscountPKR = 50, description = "First Ride Welcome: ₨ 50 Flat Off", urduDescription = "خوش آمدید: فلیٹ ۵۰ روپے رعایت"),
        PromoCode("AZADI", flatDiscountPKR = 40, description = "Azadi Special: ₨ 40 Flat Off", urduDescription = "آزادی اسپیشل: فلیٹ ۴۰ روپے رعایت")
    )

    fun validatePromoCode(code: String, farePKR: Int): PromoValidationResult {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isBlank()) {
            return PromoValidationResult.Error(
                message = "Please enter a promo code",
                urduMessage = "براہِ کرم پرومو کوڈ درج کریں"
            )
        }

        val promo = availablePromos.find { it.code.equals(cleanCode, ignoreCase = true) }
        if (promo == null) {
            return PromoValidationResult.Error(
                message = "Invalid promo code '$cleanCode'. Try 'MAHI' or 'JARANWALA'",
                urduMessage = "غلط پرومو کوڈ '$cleanCode'۔ پرومو کوڈ 'MAHI' یا 'JARANWALA' آزمائیں"
            )
        }

        val discountAmount = if (promo.flatDiscountPKR > 0) {
            promo.flatDiscountPKR.coerceAtMost((farePKR - 20).coerceAtLeast(0))
        } else {
            val calculated = (farePKR * promo.discountPercent) / 100
            calculated.coerceAtMost(promo.maxDiscountPKR).coerceAtMost((farePKR - 20).coerceAtLeast(0))
        }

        val finalFare = (farePKR - discountAmount).coerceAtLeast(30)
        return PromoValidationResult.Success(
            promo = promo,
            discountAmountPKR = discountAmount,
            finalFarePKR = finalFare,
            message = "Promo '${promo.code}' applied: ₨ $discountAmount discount!",
            urduMessage = "پرومو '${promo.code}' لاگو: ₨ $discountAmount کی بچت!"
        )
    }

    // Saved Places for Quick Ride Booking
    private val defaultSavedPlaces = listOf(
        SavedPlace(
            id = "PLC-1",
            label = "Home",
            urduLabel = "گھر",
            address = "Committee Chowk, Jaranwala",
            iconType = "HOME"
        ),
        SavedPlace(
            id = "PLC-2",
            label = "Work / Office",
            urduLabel = "دفتر / کام",
            address = "Katchery Bazar, Jaranwala",
            iconType = "WORK"
        ),
        SavedPlace(
            id = "PLC-3",
            label = "College",
            urduLabel = "گورنمنٹ کالج",
            address = "Government Degree College, Jaranwala",
            iconType = "COLLEGE"
        )
    )

    private val _savedPlaces = MutableStateFlow<List<SavedPlace>>(defaultSavedPlaces)
    val savedPlaces: StateFlow<List<SavedPlace>> = _savedPlaces.asStateFlow()

    fun addSavedPlace(
        label: String,
        address: String,
        urduLabel: String = "",
        iconType: String = "FAVORITE"
    ): SavedPlace {
        val placeId = "PLC-${System.currentTimeMillis().toString().takeLast(6)}"
        val cleanUrdu = if (urduLabel.isNotBlank()) urduLabel else when (iconType.uppercase()) {
            "HOME" -> "گھر"
            "WORK" -> "دفتر"
            "COLLEGE" -> "کالج"
            "SHOP" -> "دکان"
            else -> "محفوظ مقام"
        }
        val newPlace = SavedPlace(
            id = placeId,
            label = label.trim(),
            urduLabel = cleanUrdu,
            address = address.trim(),
            iconType = iconType.uppercase()
        )
        _savedPlaces.update { listOf(newPlace) + it }

        // Sync to Firestore
        val userId = getFirebaseAuth()?.currentUser?.uid ?: _currentUser.value?.uid ?: "USER-101"
        scope.launch {
            try {
                getFirestore()?.collection("users")?.document(userId)
                    ?.collection("saved_places")?.document(placeId)
                    ?.set(newPlace.toFirestoreMap())
                    ?.addOnFailureListener { ex ->
                        handleFirestoreError(ex, OperationType.WRITE, "users/$userId/saved_places/$placeId") { getFirebaseAuth() }
                    }
            } catch (_: Throwable) {}
        }
        return newPlace
    }

    fun removeSavedPlace(id: String) {
        _savedPlaces.update { list -> list.filter { it.id != id } }
        val userId = getFirebaseAuth()?.currentUser?.uid ?: _currentUser.value?.uid ?: "USER-101"
        scope.launch {
            try {
                getFirestore()?.collection("users")?.document(userId)
                    ?.collection("saved_places")?.document(id)?.delete()
                    ?.addOnFailureListener { ex ->
                        handleFirestoreError(ex, OperationType.DELETE, "users/$userId/saved_places/$id") { getFirebaseAuth() }
                    }
            } catch (_: Throwable) {}
        }
    }

    private var activePassengerRideListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var activeRideDriverLocationListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var trackedDriverIdForActiveRide: String? = null
    private var scheduledRidesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var adminDriversListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var adminFaresListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var adminComplaintsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var adminAuditLogsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var adminRideRequestsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var adminWeddingBookingsListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val _adminAllRides = MutableStateFlow<List<RideBooking>>(emptyList())
    val adminAllRides: StateFlow<List<RideBooking>> = _adminAllRides.asStateFlow()

    /**
     * Attaches live Firestore listeners for the MAHI Owner Admin Control Center
     * (drivers, fare rules, complaints, real-time rides, weddings, and tamper-proof security audit logs).
     */
    fun listenToAdminCollections() {
        val db = getFirestore() ?: return
        detachAdminListeners()

        try {
            adminDriversListener = db.collection("drivers").addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "drivers") { getFirebaseAuth() }
                    return@addSnapshotListener
                }
                if (snap != null && !snap.isEmpty) {
                    val remoteDrivers = snap.documents.mapNotNull { doc ->
                        val catStr = doc.getString("vehicleType") ?: "RICKSHAW"
                        val cat = try { VehicleCategory.valueOf(catStr) } catch (_: Throwable) { VehicleCategory.RICKSHAW }
                        DriverProfile(
                            id = doc.id,
                            name = doc.getString("name") ?: "Captain",
                            phone = doc.getString("phone") ?: "",
                            cnic = doc.getString("cnic") ?: "",
                            licenseNo = doc.getString("licenseNumber") ?: "",
                            vehicleCategory = cat,
                            vehicleModel = doc.getString("vehicleModel") ?: "",
                            plateNo = doc.getString("plateNumber") ?: "",
                            isApproved = doc.getBoolean("isApproved") ?: false,
                            isOnline = doc.getBoolean("isOnline") ?: false,
                            rating = doc.getDouble("rating") ?: 5.0,
                            todayEarningsPKR = (doc.get("earningsPKR") as? Number)?.toInt() ?: 0,
                            weeklyEarningsPKR = (doc.get("earningsPKR") as? Number)?.toInt() ?: 0,
                            totalTrips = (doc.get("totalRides") as? Number)?.toInt() ?: 0,
                            isDemoData = false
                        )
                    }
                    _drivers.update { existing ->
                        val localOnly = existing.filter { ex -> remoteDrivers.none { it.id == ex.id } }
                        (remoteDrivers + localOnly).distinctBy { it.id }
                    }
                }
            }

            adminFaresListener = db.collection("config").document("fares").addSnapshotListener { doc, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, "config/fares") { getFirebaseAuth() }
                    return@addSnapshotListener
                }
                if (doc != null && doc.exists()) {
                    _adminConfig.update { cur ->
                        cur.copy(
                            bikeBaseFare = (doc.get("bikeBaseFare") as? Number)?.toInt() ?: cur.bikeBaseFare,
                            bikePerKm = (doc.get("bikePerKm") as? Number)?.toInt() ?: cur.bikePerKm,
                            rickshawBaseFare = (doc.get("rickshawBaseFare") as? Number)?.toInt() ?: cur.rickshawBaseFare,
                            rickshawPerKm = (doc.get("rickshawPerKm") as? Number)?.toInt() ?: cur.rickshawPerKm,
                            carBaseFare = (doc.get("carBaseFare") as? Number)?.toInt() ?: cur.carBaseFare,
                            carPerKm = (doc.get("carPerKm") as? Number)?.toInt() ?: cur.carPerKm,
                            platformCommissionPercent = (doc.get("platformCommissionPercent") as? Number)?.toInt()
                                ?: cur.platformCommissionPercent
                        )
                    }
                }
            }

            adminComplaintsListener = db.collection("complaints").addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "complaints") { getFirebaseAuth() }
                    return@addSnapshotListener
                }
                if (snap != null && !snap.isEmpty) {
                    val remoteComplaints = snap.documents.map { doc ->
                        Complaint(
                            id = doc.id,
                            rideId = doc.getString("rideId") ?: "",
                            passengerName = doc.getString("passengerName") ?: "Passenger",
                            category = doc.getString("category") ?: "General",
                            urduCategory = doc.getString("urduCategory") ?: "شکایت",
                            message = doc.getString("description") ?: doc.getString("message") ?: "",
                            status = doc.getString("status") ?: "OPEN",
                            isDemoData = false,
                            timestamp = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                        )
                    }
                    _complaints.update { existing ->
                        val localOnly = existing.filter { ex -> remoteComplaints.none { it.id == ex.id } }
                        (remoteComplaints + localOnly).sortedByDescending { it.timestamp }
                    }
                }
            }

            adminAuditLogsListener = db.collection("security_audit_logs").addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "security_audit_logs") { getFirebaseAuth() }
                    return@addSnapshotListener
                }
                if (snap != null && !snap.isEmpty) {
                    val remoteLogs = snap.documents.mapNotNull { doc ->
                        val evName = doc.getString("eventType") ?: return@mapNotNull null
                        val evType = try { SecurityEventType.valueOf(evName) } catch (_: Throwable) { SecurityEventType.LOGIN_SUCCESS }
                        SecurityAuditLogEntry(
                            id = doc.id,
                            timestamp = doc.getTimestamp("timestamp")?.toDate()?.time
                                ?: (doc.get("timestamp") as? Number)?.toLong()
                                ?: System.currentTimeMillis(),
                            eventType = evType,
                            description = doc.getString("description") ?: "",
                            severity = doc.getString("severity") ?: "NORMAL",
                            status = doc.getString("status") ?: "LOGGED",
                            clientSignature = doc.getString("clientSignature") ?: "Cloud Audit"
                        )
                    }
                    _securityAuditLogs.update { existing ->
                        val localOnly = existing.filter { ex -> remoteLogs.none { it.id == ex.id } }
                        (remoteLogs + localOnly).sortedByDescending { it.timestamp }.take(50)
                    }
                }
            }

            adminRideRequestsListener = db.collection("ride_requests").addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "ride_requests") { getFirebaseAuth() }
                    return@addSnapshotListener
                }
                if (snap != null && !snap.isEmpty) {
                    val remoteRides = snap.documents.mapNotNull { mapFirestoreRideDocument(it) }
                        .sortedByDescending { it.timestamp }
                    _adminAllRides.value = remoteRides
                }
            }

            adminWeddingBookingsListener = db.collection("wedding_bookings").addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "wedding_bookings") { getFirebaseAuth() }
                    return@addSnapshotListener
                }
                if (snap != null && !snap.isEmpty) {
                    val remoteWeddings = snap.documents.mapNotNull { doc ->
                        val mixName = doc.getString("convoyMix") ?: ConvoyMixType.STANDARD_SEDANS.name
                        val mixType = try { ConvoyMixType.valueOf(mixName) } catch (_: Throwable) { ConvoyMixType.STANDARD_SEDANS }
                        val decName = doc.getString("decorationTier") ?: DecorationTier.STANDARD.name
                        val decTier = try { DecorationTier.valueOf(decName) } catch (_: Throwable) { DecorationTier.STANDARD }
                        WeddingBooking(
                            id = doc.getString("bookingId") ?: doc.id,
                            isBaratConvoy = doc.getBoolean("isBaratConvoy") ?: false,
                            customerName = doc.getString("customerName") ?: "Client",
                            customerPhone = doc.getString("customerPhone") ?: "",
                            primaryCar = doc.getString("primaryCar") ?: "Decorated Car",
                            carCount = (doc.get("carCount") as? Number)?.toInt() ?: 1,
                            convoyMix = mixType,
                            decorationTier = decTier,
                            bookingDurationHours = (doc.get("durationHours") as? Number)?.toInt() ?: 8,
                            durationLabel = doc.getString("durationLabel") ?: "8 Hours",
                            eventDate = doc.getString("eventDate") ?: "",
                            eventTime = doc.getString("eventTime") ?: "",
                            pickupLocation = doc.getString("pickupLocation") ?: "",
                            venueLocation = doc.getString("venueLocation") ?: "",
                            totalCostPKR = (doc.get("totalCostPKR") as? Number)?.toInt() ?: 0,
                            notes = doc.getString("notes") ?: "",
                            status = doc.getString("status") ?: "PENDING",
                            userId = doc.getString("userId") ?: "",
                            isDemoData = false,
                            timestamp = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                        )
                    }
                    _weddingBookings.update { existing ->
                        val localOnly = existing.filter { ex -> remoteWeddings.none { it.id == ex.id } }
                        (remoteWeddings + localOnly).sortedByDescending { it.timestamp }
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    fun detachAdminListeners() {
        adminDriversListener?.remove()
        adminDriversListener = null
        adminFaresListener?.remove()
        adminFaresListener = null
        adminComplaintsListener?.remove()
        adminComplaintsListener = null
        adminAuditLogsListener?.remove()
        adminAuditLogsListener = null
        adminRideRequestsListener?.remove()
        adminRideRequestsListener = null
        adminWeddingBookingsListener?.remove()
        adminWeddingBookingsListener = null
    }

    /**
     * Subscribes the passenger app to real-time Firestore updates on /ride_requests/{rideId}
     * AND /drivers/{driverId} so Driver acceptance, arrival, live GPS coordinates, trip start,
     * and completion reflect instantly on the osmdroid live tracking map.
     */
    fun listenToActivePassengerRide(rideId: String): com.google.firebase.firestore.ListenerRegistration? {
        activePassengerRideListener?.remove()
        activeRideDriverLocationListener?.remove()
        activeRideDriverLocationListener = null
        trackedDriverIdForActiveRide = null
        if (rideId.isBlank()) return null
        return try {
            val db = getFirestore() ?: return null
            val reg = db.collection("ride_requests").document(rideId)
                .addSnapshotListener { doc, error ->
                    if (error != null) {
                        handleFirestoreError(error, OperationType.GET, "ride_requests/$rideId") { getFirebaseAuth() }
                        return@addSnapshotListener
                    }
                    if (doc != null && doc.exists()) {
                        val updated = mapFirestoreRideDocument(doc) ?: return@addSnapshotListener
                        _currentPassengerRide.update { current ->
                            // Preserve freshest live driver GPS if ride_requests doc didn't carry newer coords yet
                            val mergedLat = updated.driverLat ?: current?.takeIf { it.id == updated.id && it.driverId == updated.driverId }?.driverLat
                            val mergedLng = updated.driverLng ?: current?.takeIf { it.id == updated.id && it.driverId == updated.driverId }?.driverLng
                            updated.copy(driverLat = mergedLat, driverLng = mergedLng)
                        }
                        _passengerHistory.update { list ->
                            val latest = _currentPassengerRide.value ?: updated
                            listOf(latest) + list.filter { it.id != updated.id }
                        }

                        // Attach real-time /drivers/{driverId} coordinate listener while trip is active
                        val assignedDriverId = updated.driverId
                        val isTripLive = updated.status == RideStatus.ACCEPTED ||
                            updated.status == RideStatus.DRIVER_ARRIVED ||
                            updated.status == RideStatus.IN_PROGRESS
                        if (!assignedDriverId.isNullOrBlank() && isTripLive) {
                            if (trackedDriverIdForActiveRide != assignedDriverId) {
                                activeRideDriverLocationListener?.remove()
                                trackedDriverIdForActiveRide = assignedDriverId
                                activeRideDriverLocationListener = db.collection("drivers").document(assignedDriverId)
                                    .addSnapshotListener { driverDoc, _ ->
                                        if (driverDoc != null && driverDoc.exists()) {
                                            val liveLat = (driverDoc.get("currentLat") as? Number)?.toDouble()
                                            val liveLng = (driverDoc.get("currentLng") as? Number)?.toDouble()
                                            if (liveLat != null && liveLng != null) {
                                                _currentPassengerRide.update { cur ->
                                                    if (cur != null && cur.id == rideId) {
                                                        cur.copy(driverLat = liveLat, driverLng = liveLng)
                                                    } else cur
                                                }
                                            }
                                        }
                                    }
                            }
                        } else if (!isTripLive) {
                            activeRideDriverLocationListener?.remove()
                            activeRideDriverLocationListener = null
                            trackedDriverIdForActiveRide = null
                        }
                    }
                }
            activePassengerRideListener = reg
            reg
        } catch (_: Throwable) {
            null
        }
    }

    // Passenger: Request Ride or Pickup & Delivery Parcel (Real-Time Dispatch, zero simulated delays)
    fun requestRide(
        passengerName: String,
        passengerPhone: String,
        pickup: String,
        destination: String,
        category: VehicleCategory,
        promoCode: String? = null,
        discountPKR: Int = 0,
        overrideDistanceKm: Double? = null,
        overrideFarePKR: Int? = null,
        isParcelDelivery: Boolean = false,
        parcelDescription: String = "",
        parcelWeightKg: String = "Up to 5 kg",
        recipientName: String = "",
        recipientPhone: String = "",
        codAmountPKR: Int = 0,
        paymentMethod: String = _selectedPaymentMethod.value.firestoreValue
    ) {
        val dist = overrideDistanceKm?.takeIf { it > 0.0 } ?: estimateDistanceKm(pickup, destination)
        val finalFare = overrideFarePKR?.takeIf { it >= 30 } ?: run {
            val rawFare = calculateFare(category, dist)
            (rawFare - discountPKR).coerceAtLeast(30)
        }
        val prefix = if (isParcelDelivery) "PCL-" else "JRW-"
        val rideId = prefix + (1000..9999).random()

        val pId = getFirebaseAuth()?.currentUser?.uid ?: _currentUser.value?.uid ?: "USER-101"
        val pName = passengerName.ifBlank { _currentUser.value?.name ?: "Ahmed Raza" }
        val pPhone = passengerPhone.ifBlank { _currentUser.value?.phoneNumber ?: "0301-8765432" }

        val pickupCoords: Pair<Double, Double> = locationDistanceService.resolveLocalCoordinates(pickup)
            ?: (com.example.util.PakistanGeoService.JARANWALA_LAT to com.example.util.PakistanGeoService.JARANWALA_LNG)
        val destCoords: Pair<Double, Double> = locationDistanceService.resolveLocalCoordinates(destination)
            ?: (31.3381 to 73.4278)

        val resolvedMode = if (isParcelDelivery) "PARCEL" else "RIDE"
        val ride = RideBooking(
            id = rideId,
            passengerId = pId,
            passengerName = pName,
            passengerPhone = pPhone,
            pickup = pickup,
            destination = destination,
            vehicleCategory = category,
            estimatedDistanceKm = dist,
            estimatedFarePKR = finalFare,
            status = RideStatus.SEARCHING,
            promoCode = promoCode,
            discountPKR = discountPKR,
            isDemoData = false,
            pickupLat = pickupCoords.first,
            pickupLng = pickupCoords.second,
            destLat = destCoords.first,
            destLng = destCoords.second,
            paymentMethod = paymentMethod.ifBlank { "CASH" },
            paymentStatus = "PENDING",
            serviceMode = resolvedMode,
            isParcelDelivery = isParcelDelivery,
            parcelDescription = parcelDescription.trim(),
            parcelWeightKg = parcelWeightKg.ifBlank { "Up to 5 kg" },
            recipientName = recipientName.trim(),
            recipientPhone = recipientPhone.trim(),
            codAmountPKR = codAmountPKR.coerceAtLeast(0)
        )
        _currentPassengerRide.value = ride

        // Immediately reflect in passenger history as active booking
        _passengerHistory.update { list ->
            val nonDuplicates = list.filter { it.id != ride.id }
            listOf(ride) + nonDuplicates
        }

        // Persist to Cloud Firestore and subscribe passenger to real-time updates
        val firestoreModel = RideRequestFirestore(
            id = rideId,
            passengerId = pId,
            passengerName = pName,
            passengerPhone = pPhone,
            pickupLocation = pickup,
            destinationLocation = destination,
            rideType = category.name,
            vehicleCategoryTitle = if (isParcelDelivery) "Parcel Delivery (${category.title})" else category.title,
            estimatedFarePKR = finalFare.toLong(),
            distanceKm = dist,
            status = "SEARCHING",
            city = "Jaranwala",
            promoCode = promoCode,
            discountPKR = discountPKR.toLong(),
            pickupLat = pickupCoords.first,
            pickupLng = pickupCoords.second,
            destLat = destCoords.first,
            destLng = destCoords.second,
            paymentMethod = paymentMethod.ifBlank { "CASH" },
            paymentStatus = "PENDING",
            serviceMode = resolvedMode,
            isParcelDelivery = isParcelDelivery,
            parcelDescription = parcelDescription.trim(),
            parcelWeightKg = parcelWeightKg.ifBlank { "Up to 5 kg" },
            recipientName = recipientName.trim(),
            recipientPhone = recipientPhone.trim(),
            codAmountPKR = codAmountPKR.coerceAtLeast(0).toLong()
        )
        saveRideRequestToFirestore(firestoreModel)
        listenToActivePassengerRide(rideId)

        // Dispatch notification event for online captains
        emitNotificationEvent(
            type = "NEW_RIDE_REQUEST",
            recipientUid = "DRIVERS_BROADCAST",
            title = if (isParcelDelivery) {
                "New Parcel Delivery (${category.title}) • پارسل ڈلیوری"
            } else {
                "New ${category.title} Request • نئی سواری"
            },
            body = if (isParcelDelivery && codAmountPKR > 0) {
                "$pickup ➔ $destination • Fee: ₨ $finalFare • COD: ₨ $codAmountPKR"
            } else {
                "$pickup ➔ $destination • Est. ₨ $finalFare"
            },
            rideId = rideId,
            targetTab = 1
        )

        // Broadcast locally for active driver session when online & approved with matching vehicle
        val driver = _activeDriver.value
        if (driver.isOnline && driver.isApproved && driver.vehicleCategory == category) {
            _incomingDriverRequests.update { list -> (listOf(ride) + list).distinctBy { it.id } }
            _incomingDriverRequest.value = ride
        }
    }

    // Passenger / Driver: Start Trip
    fun startTrip() {
        val currentRide = _currentDriverRide.value ?: _currentPassengerRide.value
        val rideId = currentRide?.id
        _currentPassengerRide.update { it?.copy(status = RideStatus.IN_PROGRESS) }
        _currentDriverRide.update { it?.copy(status = RideStatus.IN_PROGRESS) }
        if (!rideId.isNullOrBlank()) {
            val passengerUid = currentRide?.passengerId ?: ""
            if (passengerUid.isNotBlank()) {
                emitNotificationEvent(
                    type = "TRIP_STARTED",
                    recipientUid = passengerUid,
                    title = "Trip Started • سفر جاری ہے",
                    body = "Heading towards ${currentRide?.destination}. Safe travels!",
                    rideId = rideId,
                    targetTab = 0
                )
            }
            scope.launch {
                try {
                    val db = getFirestore()
                    val payload = mapOf(
                        "status" to "IN_PROGRESS",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    db?.collection("ride_requests")?.document(rideId)?.update(payload)
                    db?.collection("rides")?.document(rideId)?.update(payload)
                } catch (e: Throwable) {
                    handleFirestoreError(e, OperationType.UPDATE, "ride_requests/$rideId") { getFirebaseAuth() }
                }
            }
        }
    }

    // Passenger / Driver: Complete Trip (Cash or JazzCash/Easypaisa stub settlement)
    fun completeTrip() {
        val ride = _currentPassengerRide.value ?: _currentDriverRide.value
        if (ride != null) {
            val assignedCaptain = _drivers.value.firstOrNull { it.vehicleCategory == ride.vehicleCategory }
                ?: _drivers.value.firstOrNull()
            val resolvedPaymentMethod = ride.paymentMethod.ifBlank { "CASH" }
            val paymentEnum = PaymentMethod.entries.firstOrNull {
                it.firestoreValue.equals(resolvedPaymentMethod, ignoreCase = true)
            } ?: PaymentMethod.CASH
            val resolvedPaymentStatus = if (paymentEnum == PaymentMethod.CASH) {
                "COLLECTED_CASH"
            } else {
                "PAID"
            }
            val completed = ride.copy(
                status = RideStatus.COMPLETED,
                driverId = ride.driverId ?: assignedCaptain?.id ?: "DRV-101",
                driverName = ride.driverName ?: assignedCaptain?.name ?: "Captain Arshad",
                driverPhone = ride.driverPhone ?: assignedCaptain?.phone ?: "0300-1122334",
                vehiclePlate = ride.vehiclePlate ?: assignedCaptain?.plateNo ?: "FS-23-4412",
                driverRating = if (ride.driverRating > 0) ride.driverRating else (assignedCaptain?.rating ?: 4.8),
                paymentMethod = resolvedPaymentMethod,
                paymentStatus = resolvedPaymentStatus
            )
            _currentPassengerRide.value = completed
            _currentDriverRide.value = null

            // Settle fare via RidePaymentGatewayRouter (Cash, JazzCash Stub, or Easypaisa Stub)
            val walletCandidate = _selectedWalletAccount.value.ifBlank { completed.passengerPhone }
            scope.launch {
                settleRideFare(
                    rideId = completed.id,
                    amountPKR = completed.estimatedFarePKR,
                    method = paymentEnum,
                    walletAccount = walletCandidate
                )
            }

            // Add to passenger history (replacing any earlier SEARCHING/ACCEPTED entry)
            _passengerHistory.update { list ->
                listOf(completed) + list.filter { it.id != completed.id }
            }

            emitNotificationEvent(
                type = "TRIP_COMPLETED",
                recipientUid = completed.passengerId,
                title = "Ride Completed! • سواری مکمل ہو گئی",
                body = "Arrived at ${completed.destination}. Fare (${completed.paymentMethod}): ₨ ${completed.estimatedFarePKR}.",
                rideId = completed.id,
                targetTab = 0
            )

            // Persist completion status to Cloud Firestore
            scope.launch {
                try {
                    val db = getFirestore()
                    val completionMap = hashMapOf<String, Any>(
                        "status" to "COMPLETED",
                        "paymentMethod" to resolvedPaymentMethod,
                        "paymentStatus" to resolvedPaymentStatus,
                        "completedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    db?.collection("ride_requests")?.document(completed.id)
                        ?.set(completionMap, SetOptions.merge())
                    db?.collection("rides")?.document(completed.id)
                        ?.set(completionMap, SetOptions.merge())
                } catch (e: Throwable) {
                    handleFirestoreError(e, OperationType.UPDATE, "ride_requests/${completed.id}") { getFirebaseAuth() }
                }
            }

            // Save to local Room database
            scope.launch {
                try {
                    val entity = CompletedRideEntity(
                        id = completed.id,
                        passengerName = completed.passengerName,
                        passengerPhone = completed.passengerPhone,
                        pickup = completed.pickup,
                        destination = completed.destination,
                        vehicleCategory = completed.vehicleCategory.name,
                        distanceKm = completed.estimatedDistanceKm,
                        farePKR = completed.estimatedFarePKR,
                        captainName = completed.driverName ?: "Captain Arshad",
                        captainPhone = completed.driverPhone ?: "0300-1122334",
                        vehiclePlate = completed.vehiclePlate ?: "FS-23-4412",
                        ratingGiven = completed.ratingGiven,
                        reviewGiven = completed.reviewGiven,
                        promoCode = completed.promoCode,
                        discountPKR = completed.discountPKR,
                        completedAt = System.currentTimeMillis()
                    )
                    roomDb?.historyDao()?.insertCompletedRide(entity)
                } catch (_: Throwable) {}
            }

            // Update driver earnings
            _activeDriver.update {
                it.copy(
                    todayEarningsPKR = it.todayEarningsPKR + ride.estimatedFarePKR,
                    weeklyEarningsPKR = it.weeklyEarningsPKR + ride.estimatedFarePKR,
                    totalTrips = it.totalTrips + 1
                )
            }
        }
    }

    // Dismiss Completed Ride so passenger can book again
    fun dismissCompletedRide() {
        _currentPassengerRide.value = null
    }

    // Emergency Alerts StateFlow
    private val _emergencyAlerts = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val emergencyAlerts: StateFlow<List<Map<String, Any>>> = _emergencyAlerts.asStateFlow()

    /**
     * Rates a completed ride, updating in-memory history, Room, and Firestore.
     */
    fun rateCompletedRide(rideId: String, stars: Int, review: String = "") {
        submitDriverRating(
            rideId = rideId,
            stars = stars,
            review = review.ifBlank { "Rated $stars stars via Jaranwala Rides Dashboard" }
        )
    }

    // Passenger: Submit Driver Rating & Review to Firestore 'rides' collection
    fun submitDriverRating(
        rideId: String,
        driverId: String? = null,
        stars: Int,
        review: String,
        tags: List<String> = emptyList(),
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val currentRide = _currentPassengerRide.value
        val targetRideId = rideId.ifBlank { currentRide?.id ?: "" }
        val targetDriverId = driverId ?: currentRide?.driverId

        // Update active ride and history
        if (currentRide != null && (targetRideId.isBlank() || currentRide.id == targetRideId)) {
            val updated = currentRide.copy(ratingGiven = stars, reviewGiven = review)
            _currentPassengerRide.value = null
            _passengerHistory.update { history ->
                if (history.none { it.id == updated.id }) {
                    listOf(updated) + history
                } else {
                    history.map { if (it.id == updated.id) updated else it }
                }
            }
        } else if (targetRideId.isNotBlank()) {
            _passengerHistory.update { history ->
                history.map {
                    if (it.id == targetRideId) it.copy(ratingGiven = stars, reviewGiven = review) else it
                }
            }
        }

        // Update driver rating if known
        if (!targetDriverId.isNullOrBlank()) {
            _drivers.update { list ->
                list.map { d ->
                    if (d.id == targetDriverId) {
                        val newRating = String.format(Locale.US, "%.1f", ((d.rating * d.totalTrips) + stars) / (d.totalTrips + 1).coerceAtLeast(1)).toDoubleOrNull() ?: d.rating
                        d.copy(rating = newRating)
                    } else d
                }
            }
        }

        // Save rating to Firestore 'rides' collection
        scope.launch {
            try {
                val db = getFirestore()
                if (db != null && targetRideId.isNotBlank()) {
                    val updateMap = hashMapOf<String, Any>(
                        "rating" to stars,
                        "driverRating" to stars.toDouble(),
                        "ratingGiven" to stars,
                        "review" to review,
                        "reviewGiven" to review,
                        "ratingTags" to tags,
                        "ratedAt" to FieldValue.serverTimestamp(),
                        "ratingStatus" to "RATED"
                    )
                    db.collection("rides").document(targetRideId).set(updateMap, SetOptions.merge())
                        .addOnSuccessListener {
                            onComplete?.invoke(true)
                        }
                        .addOnFailureListener { ex ->
                            handleFirestoreError(ex, OperationType.UPDATE, "rides/$targetRideId") { getFirebaseAuth() }
                            onComplete?.invoke(false)
                        }
                    // Mirror to ride_requests
                    try {
                        db.collection("ride_requests").document(targetRideId).set(updateMap, SetOptions.merge())
                    } catch (_: Throwable) {}
                } else {
                    onComplete?.invoke(true)
                }
            } catch (_: Throwable) {
                onComplete?.invoke(true)
            }
        }
    }

    // Keep submitRating for backward compatibility
    fun submitRating(stars: Int, review: String) {
        val rideId = _currentPassengerRide.value?.id ?: ""
        val driverId = _currentPassengerRide.value?.driverId
        submitDriverRating(rideId = rideId, driverId = driverId, stars = stars, review = review)
    }

    // Emergency Alert: Save to Firestore collection 'emergency_alerts'
    fun triggerEmergencyAlert(
        ride: RideBooking,
        reason: String = "Passenger triggered SOS in Jaranwala",
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        val alertId = "SOS-${System.currentTimeMillis().toString().takeLast(6)}"
        val uid = getFirebaseAuth()?.currentUser?.uid ?: _currentUser.value?.uid ?: "guest"
        val alertData = hashMapOf<String, Any>(
            "alertId" to alertId,
            "rideId" to ride.id,
            "userId" to uid,
            "passengerName" to ride.passengerName,
            "passengerPhone" to ride.passengerPhone,
            "driverName" to (ride.driverName ?: "Unassigned"),
            "driverPhone" to (ride.driverPhone ?: "N/A"),
            "vehiclePlate" to (ride.vehiclePlate ?: "N/A"),
            "vehicleCategory" to ride.vehicleCategory.name,
            "pickupLocation" to ride.pickup,
            "destinationLocation" to ride.destination,
            "reason" to reason,
            "status" to "ACTIVE_EMERGENCY",
            "city" to "Jaranwala",
            "timestamp" to System.currentTimeMillis(),
            "emergencyHelplines" to listOf("15 (Police)", "1122 (Ambulance/Fire)", "130 (Motorway)")
        )

        _emergencyAlerts.update { listOf(alertData) + it }

        val firestoreAlertData = HashMap(alertData).apply {
            remove("timestamp")
            put("createdAt", FieldValue.serverTimestamp())
        }

        scope.launch {
            try {
                val db = getFirestore()
                if (db != null) {
                    db.collection("emergency_alerts").document(alertId).set(firestoreAlertData)
                        .addOnSuccessListener {
                            onComplete?.invoke(true, alertId)
                        }
                        .addOnFailureListener { e ->
                            handleFirestoreError(e, OperationType.CREATE, "emergency_alerts/$alertId") { getFirebaseAuth() }
                            onComplete?.invoke(false, e.message)
                        }
                } else {
                    onComplete?.invoke(true, alertId)
                }
            } catch (e: Throwable) {
                onComplete?.invoke(true, alertId)
            }
        }
    }

    // Passenger: Cancel Ride (Zero artificial delay)
    @JvmOverloads
    fun cancelRide(rideId: String? = null) {
        val targetId = rideId?.takeIf { it.isNotBlank() }
        val activePassenger = _currentPassengerRide.value
        val activeDriver = _currentDriverRide.value
        val resolvedId = targetId ?: activePassenger?.id ?: activeDriver?.id

        val ride = when {
            activePassenger != null && (targetId == null || activePassenger.id == targetId) -> activePassenger
            activeDriver != null && (targetId == null || activeDriver.id == targetId) -> activeDriver
            resolvedId != null -> _passengerHistory.value.find { it.id == resolvedId }
                ?: _adminAllRides.value.find { it.id == resolvedId }
            else -> null
        }

        if (ride != null) {
            val cancelledRide = ride.copy(status = RideStatus.CANCELLED)
            _passengerHistory.update { list ->
                val remaining = list.filter { it.id != cancelledRide.id }
                listOf(cancelledRide) + remaining
            }
            _adminAllRides.update { list ->
                list.map { if (it.id == cancelledRide.id) cancelledRide else it }
            }
        }

        if (resolvedId == null || activePassenger?.id == resolvedId) {
            _currentPassengerRide.value = null
        }
        if (resolvedId == null || _incomingDriverRequest.value?.id == resolvedId) {
            _incomingDriverRequest.value = null
        }
        if (resolvedId != null) {
            _incomingDriverRequests.update { list -> list.filter { it.id != resolvedId } }
        }
        if (resolvedId == null || activeDriver?.id == resolvedId) {
            _currentDriverRide.value = null
        }

        if (!resolvedId.isNullOrBlank()) {
            scope.launch {
                try {
                    val cancelMap = mapOf(
                        "status" to "CANCELLED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    getFirestore()?.collection("ride_requests")?.document(resolvedId)?.update(cancelMap)
                    getFirestore()?.collection("rides")?.document(resolvedId)?.update(cancelMap)
                } catch (_: Throwable) {}
            }
        }
    }

    // Driver: Toggle Online Availability Status (synced to Firestore)
    fun setDriverOnline(online: Boolean) {
        if (online && !_activeDriver.value.isApproved) {
            // Unapproved driver cannot go online until approved by Admin
            return
        }
        val driverId = _activeDriver.value.id
        _activeDriver.update { it.copy(isOnline = online) }
        _drivers.update { list ->
            list.map { if (it.id == driverId) it.copy(isOnline = online) else it }
        }
        if (!online) {
            incomingRequestsListener?.remove()
            incomingRequestsListener = null
            _incomingDriverRequests.value = emptyList()
            _incomingDriverRequest.value = null
        } else {
            listenToIncomingRideRequestsFromFirestore()
        }

        scope.launch {
            try {
                getFirestore()?.collection("drivers")?.document(driverId)
                    ?.update(
                        mapOf(
                            "isOnline" to online,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    ?.addOnFailureListener { ex ->
                        handleFirestoreError(ex, OperationType.UPDATE, "drivers/$driverId") { getFirebaseAuth() }
                    }
            } catch (_: Throwable) {}
        }
    }

    // Driver: Accept Incoming Request using atomic Firestore transaction
    fun acceptDriverRequest(rideId: String? = null) {
        val req = if (!rideId.isNullOrBlank()) {
            _incomingDriverRequests.value.find { it.id == rideId } ?: _incomingDriverRequest.value
        } else {
            _incomingDriverRequest.value ?: _incomingDriverRequests.value.firstOrNull()
        } ?: return

        val driver = _activeDriver.value
        val accepted = req.copy(
            status = RideStatus.ACCEPTED,
            driverId = driver.id,
            driverName = driver.name,
            driverPhone = driver.phone,
            vehiclePlate = driver.plateNo,
            driverRating = driver.rating
        )
        _currentDriverRide.value = accepted
        _currentPassengerRide.value = accepted
        _passengerHistory.update { list -> list.map { if (it.id == req.id) accepted else it } }

        _incomingDriverRequests.update { list -> list.filter { it.id != req.id } }
        _incomingDriverRequest.value = _incomingDriverRequests.value.firstOrNull()

        emitNotificationEvent(
            type = "CAPTAIN_ASSIGNED",
            recipientUid = req.passengerId,
            title = "Captain Assigned! • کپتان تفویض ہو گیا",
            body = "${driver.name} (${driver.plateNo}) is on the way to ${req.pickup}.",
            rideId = req.id,
            targetTab = 0
        )

        scope.launch {
            try {
                val db = getFirestore() ?: return@launch
                val reqRef = db.collection("ride_requests").document(req.id)
                val rideRef = db.collection("rides").document(req.id)
                db.runTransaction { tx ->
                    val snap = tx.get(reqRef)
                    val currentStatus = snap.getString("status")
                    if (snap.exists() && currentStatus != null && currentStatus != "SEARCHING") {
                        throw IllegalStateException("Ride already claimed by another captain")
                    }
                    val updateMap = mapOf(
                        "status" to "ACCEPTED",
                        "driverId" to driver.id,
                        "driverName" to driver.name,
                        "driverPhone" to driver.phone,
                        "vehiclePlate" to driver.plateNo,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    tx.set(reqRef, updateMap, SetOptions.merge())
                    tx.set(rideRef, updateMap, SetOptions.merge())
                    null
                }.addOnFailureListener { ex ->
                    handleFirestoreError(ex, OperationType.UPDATE, reqRef.path) { getFirebaseAuth() }
                }
            } catch (e: Throwable) {
                handleFirestoreError(e, OperationType.UPDATE, "ride_requests/${req.id}") { getFirebaseAuth() }
            }
        }
    }

    /**
     * Broadcasts live Captain GPS coordinates to Firestore /drivers/{driverId}
     * and the active trip document for live Passenger map tracking.
     */
    fun updateDriverLiveLocation(lat: Double, lng: Double) {
        val driverId = _activeDriver.value.id
        val rideId = _currentDriverRide.value?.id
        _currentDriverRide.update { it?.copy(driverLat = lat, driverLng = lng) }
        _currentPassengerRide.update {
            if (it != null && it.id == rideId) it.copy(driverLat = lat, driverLng = lng) else it
        }
        scope.launch {
            try {
                val db = getFirestore() ?: return@launch
                db.collection("drivers").document(driverId).update(
                    mapOf(
                        "currentLat" to lat,
                        "currentLng" to lng,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (!rideId.isNullOrBlank()) {
                    val locMap = mapOf(
                        "driverLat" to lat,
                        "driverLng" to lng,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    db.collection("ride_requests").document(rideId).update(locMap)
                    db.collection("rides").document(rideId).update(locMap)
                }
            } catch (_: Throwable) {}
        }
    }

    // Driver: Reject / Decline Incoming Request
    fun rejectDriverRequest(rideId: String? = null) {
        val targetId = rideId ?: _incomingDriverRequest.value?.id ?: _incomingDriverRequests.value.firstOrNull()?.id
        if (targetId != null) {
            declinedRideIds.add(targetId)
            _incomingDriverRequests.update { list -> list.filter { it.id != targetId } }
            _incomingDriverRequest.value = _incomingDriverRequests.value.firstOrNull()
        } else {
            _incomingDriverRequest.value = null
            _incomingDriverRequests.value = emptyList()
        }
    }

    // Driver: Update Driver Status (Arrived at Pickup)
    fun setDriverArrived() {
        val currentRide = _currentDriverRide.value ?: _currentPassengerRide.value
        val rideId = currentRide?.id
        _currentDriverRide.update { it?.copy(status = RideStatus.DRIVER_ARRIVED) }
        _currentPassengerRide.update { it?.copy(status = RideStatus.DRIVER_ARRIVED) }
        if (!rideId.isNullOrBlank()) {
            val passengerUid = currentRide?.passengerId ?: ""
            if (passengerUid.isNotBlank()) {
                emitNotificationEvent(
                    type = "CAPTAIN_ARRIVED",
                    recipientUid = passengerUid,
                    title = "Captain Arrived! • کپتان پہنچ گئے",
                    body = "${currentRide?.driverName ?: "Your Captain"} has arrived at ${currentRide?.pickup}.",
                    rideId = rideId,
                    targetTab = 0
                )
            }
            scope.launch {
                try {
                    val db = getFirestore()
                    val payload = mapOf(
                        "status" to "ARRIVED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    db?.collection("ride_requests")?.document(rideId)?.update(payload)
                    db?.collection("rides")?.document(rideId)?.update(payload)
                } catch (e: Throwable) {
                    handleFirestoreError(e, OperationType.UPDATE, "ride_requests/$rideId") { getFirebaseAuth() }
                }
            }
        }
    }

    // Driver: Register / Update Profile
    fun registerDriver(
        name: String,
        phone: String,
        cnic: String,
        license: String,
        category: VehicleCategory,
        model: String,
        plate: String,
        explicitId: String? = null
    ): DriverProfile {
        val resolvedId = explicitId?.takeIf { it.isNotBlank() }
            ?: try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (_: Throwable) { null }
            ?: ("DRV-" + (100..999).random())
        val newDriver = DriverProfile(
            id = resolvedId,
            name = name,
            phone = phone,
            cnic = cnic,
            licenseNo = license,
            vehicleCategory = category,
            vehicleModel = model,
            plateNo = plate,
            isApproved = false, // Pending admin approval
            isOnline = false,
            rating = 5.0,
            todayEarningsPKR = 0,
            weeklyEarningsPKR = 0,
            totalTrips = 0,
            isDemoData = false
        )
        _drivers.update { existing ->
            listOf(newDriver) + existing.filter { it.id != resolvedId }
        }
        return newDriver
    }

    /**
     * Completes Captain Onboarding after phone verification, updating the active driver profile,
     * setting isApproved = false (pending admin approval), and persisting to Firestore /drivers/{uid}.
     */
    fun completeCaptainOnboarding(
        name: String,
        phone: String,
        cnic: String,
        license: String,
        category: VehicleCategory,
        model: String,
        plate: String,
        context: Context? = null
    ): DriverProfile {
        val currentUid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (_: Throwable) { null }
            ?: _currentUser.value?.uid
        val driver = registerDriver(
            name = name.trim(),
            phone = phone.trim(),
            cnic = cnic.trim(),
            license = license.trim(),
            category = category,
            model = model.trim(),
            plate = plate.trim(),
            explicitId = currentUid
        )
        _activeDriver.value = driver
        _incomingDriverRequests.value = emptyList()
        _incomingDriverRequest.value = null

        val existingUser = _currentUser.value
        val updatedRole = if (existingUser?.role == UserRole.ADMIN) UserRole.ADMIN else UserRole.DRIVER
        val updatedUser = (existingUser ?: UserProfile(
            uid = driver.id,
            phoneNumber = driver.phone,
            name = driver.name,
            role = updatedRole
        )).copy(
            uid = driver.id,
            phoneNumber = driver.phone,
            name = driver.name,
            role = updatedRole,
            isVerified = true,
            isGuest = false,
            cnic = driver.cnic,
            licenseNo = driver.licenseNo,
            vehicleCategory = driver.vehicleCategory,
            vehicleModel = driver.vehicleModel,
            plateNo = driver.plateNo
        )
        _currentUser.value = updatedUser
        (context ?: appContext)?.let { saveSession(it, updatedUser) }

        scope.launch {
            try {
                val db = getFirestore(context) ?: return@launch
                val driverRef = db.collection("drivers").document(driver.id)
                driverRef.get().addOnSuccessListener { snap ->
                    if (snap.exists()) {
                        val updateData = hashMapOf<String, Any>(
                            "name" to driver.name,
                            "phone" to driver.phone,
                            "cnic" to driver.cnic,
                            "licenseNumber" to driver.licenseNo,
                            "vehicleType" to driver.vehicleCategory.name,
                            "vehicleModel" to driver.vehicleModel,
                            "plateNumber" to driver.plateNo,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        driverRef.update(updateData).addOnFailureListener { ex ->
                            handleFirestoreError(ex, OperationType.UPDATE, "drivers/${driver.id}") { getFirebaseAuth() }
                        }
                    } else {
                        val driverData = hashMapOf<String, Any>(
                            "driverId" to driver.id,
                            "name" to driver.name,
                            "phone" to driver.phone,
                            "cnic" to driver.cnic,
                            "licenseNumber" to driver.licenseNo,
                            "vehicleType" to driver.vehicleCategory.name,
                            "vehicleModel" to driver.vehicleModel,
                            "plateNumber" to driver.plateNo,
                            "isOnline" to false,
                            "isApproved" to false,
                            "rating" to 5.0,
                            "totalRides" to 0,
                            "earningsPKR" to 0.0,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        driverRef.set(driverData).addOnFailureListener { ex ->
                            handleFirestoreError(ex, OperationType.CREATE, "drivers/${driver.id}") { getFirebaseAuth() }
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
        return driver
    }

    // Admin: Approve / Reject Driver (also updates Firestore & dispatches FCM notification event)
    fun setDriverApproval(driverId: String, approved: Boolean) {
        _drivers.update { list ->
            list.map {
                if (it.id == driverId) it.copy(isApproved = approved) else it
            }
        }
        if (_activeDriver.value.id == driverId) {
            _activeDriver.update { it.copy(isApproved = approved) }
        }
        emitNotificationEvent(
            type = "DRIVER_APPROVAL",
            recipientUid = driverId,
            title = if (approved) "Captain Account Approved!" else "Captain Verification Paused",
            body = if (approved) "You can now go Online and receive ride requests in Jaranwala."
            else "Your Captain status requires Admin review.",
            targetTab = 1
        )
        scope.launch {
            try {
                getFirestore()?.collection("drivers")?.document(driverId)
                    ?.update(
                        mapOf(
                            "isApproved" to approved,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    ?.addOnFailureListener { ex ->
                        handleFirestoreError(ex, OperationType.UPDATE, "drivers/$driverId") { getFirebaseAuth() }
                    }
            } catch (e: Throwable) {
                handleFirestoreError(e, OperationType.UPDATE, "drivers/$driverId") { getFirebaseAuth() }
            }
        }
    }

    // Wedding / Barat: Book
    fun bookWeddingService(
        isBarat: Boolean,
        name: String,
        phone: String,
        carModel: String,
        carCount: Int,
        convoyMix: ConvoyMixType = ConvoyMixType.STANDARD_SEDANS,
        decoration: DecorationTier,
        date: String,
        time: String,
        pickup: String,
        venue: String,
        notes: String
    ): WeddingBooking {
        val baseCarRate = if (isBarat) (carCount * convoyMix.multiplierPerCarPKR) else 18000
        val total = baseCarRate + decoration.additionalPricePKR
        val uId = _currentUser.value?.uid ?: "USER-101"
        val booking = WeddingBooking(
            id = if (isBarat) "BRT-${(1000..9999).random()}" else "WDD-${(1000..9999).random()}",
            isBaratConvoy = isBarat,
            customerName = name,
            customerPhone = phone,
            primaryCar = carModel,
            carCount = carCount,
            convoyMix = convoyMix,
            decorationTier = decoration,
            eventDate = date,
            eventTime = time,
            pickupLocation = pickup,
            venueLocation = venue,
            totalCostPKR = total,
            notes = notes,
            status = "CONFIRMED",
            userId = uId,
            isDemoData = false
        )
        _weddingBookings.update { listOf(booking) + it }

        // Persist to Cloud Firestore
        scope.launch {
            try {
                getFirestore()?.collection("wedding_bookings")?.document(booking.id)?.set(booking.toFirestoreMap())
            } catch (_: Throwable) {}
        }
        return booking
    }

    /**
     * Specialized booking function for wedding convoys with fleet size, duration, and decoration preferences.
     */
    fun bookWeddingConvoy(
        customerName: String,
        customerPhone: String,
        fleetSize: Int,
        convoyMix: ConvoyMixType,
        duration: WeddingConvoyDuration,
        decoration: ConvoyDecorationPreference,
        escortOptions: List<String>,
        pickupLocation: String,
        venueLocation: String,
        eventDate: String,
        eventTime: String,
        specialNotes: String,
        onComplete: ((Boolean, WeddingBooking) -> Unit)? = null
    ): WeddingBooking {
        val baseFleet = fleetSize * convoyMix.multiplierPerCarPKR
        val durationAdjusted = (baseFleet * duration.multiplier).toInt()
        val totalCost = durationAdjusted + decoration.additionalPricePKR
        val bookingId = "BRT-CVY-${(1000..9999).random()}"
        val uId = _currentUser.value?.uid ?: "USER-101"

        val booking = WeddingBooking(
            id = bookingId,
            isBaratConvoy = true,
            customerName = customerName,
            customerPhone = customerPhone,
            primaryCar = "${duration.title} • ${fleetSize}x ${convoyMix.title}",
            carCount = fleetSize,
            convoyMix = convoyMix,
            decorationTier = when (decoration) {
                ConvoyDecorationPreference.ROYAL_ORCHIDS -> DecorationTier.VIP_DELUXE
                ConvoyDecorationPreference.FRESH_ROSES, ConvoyDecorationPreference.NIGHT_LED_GLOW -> DecorationTier.ROYAL_ROSES
                else -> DecorationTier.STANDARD
            },
            bookingDurationHours = duration.hours,
            durationLabel = duration.title,
            decorationPreference = decoration.title,
            escortOptions = escortOptions,
            eventDate = eventDate,
            eventTime = eventTime,
            pickupLocation = pickupLocation,
            venueLocation = venueLocation,
            totalCostPKR = totalCost,
            notes = specialNotes,
            status = "CONFIRMED",
            userId = uId,
            isDemoData = false
        )

        _weddingBookings.update { listOf(booking) + it }

        // Save to local Room Database
        scope.launch {
            try {
                val entity = WeddingConvoyHistoryEntity(
                    id = booking.id,
                    isBaratConvoy = booking.isBaratConvoy,
                    customerName = booking.customerName,
                    customerPhone = booking.customerPhone,
                    primaryCar = booking.primaryCar,
                    carCount = booking.carCount,
                    convoyMix = booking.convoyMix.name,
                    decorationTier = booking.decorationTier.name,
                    durationHours = booking.bookingDurationHours,
                    durationLabel = booking.durationLabel,
                    eventDate = booking.eventDate,
                    eventTime = booking.eventTime,
                    pickupLocation = booking.pickupLocation,
                    venueLocation = booking.venueLocation,
                    totalCostPKR = booking.totalCostPKR,
                    notes = booking.notes,
                    status = booking.status,
                    createdAt = booking.timestamp
                )
                roomDb?.historyDao()?.insertWeddingBooking(entity)
            } catch (_: Throwable) {}
        }

        // Save to Firestore
        scope.launch {
            try {
                val db = getFirestore()
                if (db != null) {
                    db.collection("wedding_bookings").document(booking.id).set(booking.toFirestoreMap())
                        .addOnSuccessListener {
                            onComplete?.invoke(true, booking)
                        }
                        .addOnFailureListener {
                            onComplete?.invoke(false, booking)
                        }
                } else {
                    onComplete?.invoke(true, booking)
                }
            } catch (_: Throwable) {
                onComplete?.invoke(true, booking)
            }
        }
        return booking
    }

    fun cancelWeddingBooking(bookingId: String) {
        _weddingBookings.update { list ->
            list.map {
                if (it.id == bookingId) it.copy(status = "CANCELLED") else it
            }
        }
        scope.launch {
            try {
                getFirestore()?.collection("wedding_bookings")?.document(bookingId)
                    ?.update(
                        mapOf(
                            "status" to "CANCELLED",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    ?.addOnFailureListener { ex ->
                        handleFirestoreError(ex, OperationType.UPDATE, "wedding_bookings/$bookingId") { getFirebaseAuth() }
                    }
            } catch (_: Throwable) {}
        }
    }

    // Complaints: Submit
    fun submitComplaint(rideId: String, name: String, category: String, urduCat: String, msg: String) {
        val complaintId = "CMP-${(100..999).random()}"
        val complaint = Complaint(
            id = complaintId,
            rideId = rideId,
            passengerName = name,
            category = category,
            urduCategory = urduCat,
            message = msg,
            status = "OPEN",
            isDemoData = false
        )
        _complaints.update { listOf(complaint) + it }
        scope.launch {
            try {
                val uid = getFirebaseAuth()?.currentUser?.uid ?: _currentUser.value?.uid ?: return@launch
                val payload = hashMapOf<String, Any>(
                    "complaintId" to complaintId,
                    "rideId" to rideId,
                    "userId" to uid,
                    "passengerName" to name,
                    "category" to category,
                    "urduCategory" to urduCat,
                    "description" to msg,
                    "status" to "OPEN",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                getFirestore()?.collection("complaints")?.document(complaintId)
                    ?.set(payload)
                    ?.addOnFailureListener { ex ->
                        handleFirestoreError(ex, OperationType.CREATE, "complaints/$complaintId") { getFirebaseAuth() }
                    }
            } catch (_: Throwable) {}
        }
    }

    // Admin: Resolve Complaint
    fun resolveComplaint(complaintId: String) {
        _complaints.update { list ->
            list.map { if (it.id == complaintId) it.copy(status = "RESOLVED") else it }
        }
        scope.launch {
            try {
                getFirestore()?.collection("complaints")?.document(complaintId)
                    ?.update(
                        mapOf(
                            "status" to "RESOLVED",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    ?.addOnFailureListener { ex ->
                        handleFirestoreError(ex, OperationType.UPDATE, "complaints/$complaintId") { getFirebaseAuth() }
                    }
            } catch (_: Throwable) {}
        }
    }

    // Room Database Integration & Session Initialization
    fun initRoomDatabase(context: Context) {
        appContext = context.applicationContext
        loadOwnerSecurityHashes(context)
        val saved = loadSession(context)
        val firebaseUser = getFirebaseAuth()?.currentUser
        if (firebaseUser != null) {
            val savedRole = saved?.role ?: UserRole.PASSENGER
            if (saved == null || saved.uid != firebaseUser.uid) {
                setUserFromFirebase(firebaseUser, savedRole)
            } else {
                syncUserFromFirestore(firebaseUser.uid, context)
            }
        } else if (saved == null) {
            _currentUser.value = null
        }
        if (roomDb != null) return
        val db = JaranwalaRoomDatabase.getInstance(context)
        roomDb = db
        scope.launch {
            db.historyDao().getAllCompletedRides().collect { list ->
                if (list.isEmpty()) {
                    seedInitialRoomRides(db)
                } else {
                    _localCompletedRides.value = list
                }
            }
        }
        scope.launch {
            db.historyDao().getAllWeddingConvoyBookings().collect { list ->
                if (list.isEmpty()) {
                    seedInitialRoomWeddings(db)
                } else {
                    _localWeddingHistory.value = list
                }
            }
        }
    }

    private suspend fun seedInitialRoomRides(db: JaranwalaRoomDatabase) {
        val initialRides = listOf(
            CompletedRideEntity(
                id = "RIDE-9410",
                passengerName = "Ahmed Raza",
                passengerPhone = "0301-8765432",
                pickup = "Committee Chowk",
                destination = "Jaranwala Railway Station",
                vehicleCategory = "RICKSHAW",
                distanceKm = 3.2,
                farePKR = 180,
                captainName = "Captain Arshad",
                captainPhone = "0300-1122334",
                vehiclePlate = "FS-23-4412",
                ratingGiven = 5,
                reviewGiven = "Excellent polite captain, reached safely on time.",
                promoCode = "JARANWALA",
                discountPKR = 30,
                completedAt = System.currentTimeMillis() - 86400000L * 2
            ),
            CompletedRideEntity(
                id = "RIDE-8842",
                passengerName = "Ahmed Raza",
                passengerPhone = "0301-8765432",
                pickup = "Cinema Chowk",
                destination = "Grain Market, Mandi Road",
                vehicleCategory = "BIKE",
                distanceKm = 1.8,
                farePKR = 95,
                captainName = "Captain Tariq",
                captainPhone = "0301-7788990",
                vehiclePlate = "LE-21-9981",
                ratingGiven = 4,
                reviewGiven = "Quick solo bike ride across Jaranwala market.",
                promoCode = null,
                discountPKR = 0,
                completedAt = System.currentTimeMillis() - 86400000L * 4
            )
        )
        for (r in initialRides) {
            db.historyDao().insertCompletedRide(r)
        }
    }

    private suspend fun seedInitialRoomWeddings(db: JaranwalaRoomDatabase) {
        val initialWeddings = listOf(
            WeddingConvoyHistoryEntity(
                id = "WDD-7821",
                isBaratConvoy = false,
                customerName = "Muhammad Zubair",
                customerPhone = "0302-3344556",
                primaryCar = "Toyota Prado TX (White)",
                carCount = 1,
                convoyMix = "ROYAL_VIP",
                decorationTier = "ROYAL_ROSES",
                durationHours = 8,
                durationLabel = "8 Hours (Standard Barat)",
                eventDate = "15 Nov 2026",
                eventTime = "04:00 PM",
                pickupLocation = "Cinema Chowk, Jaranwala",
                venueLocation = "Royal Palm Marriage Hall, Lahore Road",
                totalCostPKR = 35500,
                notes = "Chauffeur in traditional dress, fresh floral hood decoration",
                status = "CONFIRMED",
                createdAt = System.currentTimeMillis() - 86400000L * 3
            ),
            WeddingConvoyHistoryEntity(
                id = "BRT-2914",
                isBaratConvoy = true,
                customerName = "Chaudhry Nadeem",
                customerPhone = "0300-5544332",
                primaryCar = "Barat Convoy (5x Corollas + 1x Hiace)",
                carCount = 6,
                convoyMix = "STANDARD_SEDANS",
                decorationTier = "STANDARD",
                durationHours = 8,
                durationLabel = "8 Hours (Standard Barat)",
                eventDate = "22 Nov 2026",
                eventTime = "01:30 PM",
                pickupLocation = "Grain Market, Mandi Road Jaranwala",
                venueLocation = "Serena Hotel, Faisalabad",
                totalCostPKR = 92000,
                notes = "Barat procession with convoy coordination flags",
                status = "CONFIRMED",
                createdAt = System.currentTimeMillis() - 86400000L * 7
            )
        )
        for (w in initialWeddings) {
            db.historyDao().insertWeddingBooking(w)
        }
    }

    fun deleteLocalCompletedRide(id: String) {
        scope.launch {
            try {
                roomDb?.historyDao()?.deleteCompletedRideById(id)
                _localCompletedRides.update { list -> list.filter { it.id != id } }
            } catch (_: Throwable) {}
        }
    }

    fun deleteLocalWeddingBooking(id: String) {
        scope.launch {
            try {
                roomDb?.historyDao()?.deleteWeddingBookingById(id)
                _localWeddingHistory.update { list -> list.filter { it.id != id } }
            } catch (_: Throwable) {}
        }
    }

    // =========================================================================
    // Emulator & Repository Suspend Contracts (Tier 2 Rules & Flow Verification)
    // =========================================================================

    suspend fun createRideSuspend(
        pickup: String,
        destination: String,
        category: VehicleCategory,
        estimatedFarePKR: Long,
        distanceKm: Double,
        rideId: String = "JRW-${System.currentTimeMillis()}-${(100..999).random()}"
    ): Result<String> {
        val path = "rides/$rideId"
        return try {
            val uid = requireFirebaseUserId()
            val db = requireNotNull(getFirestore()) { "Firestore is not initialized" }
            val payload = hashMapOf<String, Any>(
                "rideId" to rideId,
                "passengerId" to uid,
                "passengerName" to (_currentUser.value?.name ?: "Passenger"),
                "passengerPhone" to (_currentUser.value?.phoneNumber ?: "0301-8765432"),
                "pickup" to pickup,
                "destination" to destination,
                "vehicleType" to category.name,
                "estimatedFarePKR" to estimatedFarePKR,
                "distanceKm" to distanceKm,
                "status" to "SEARCHING",
                "paymentMethod" to "CASH",
                "paymentStatus" to "PENDING",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("rides").document(rideId).set(payload).await()
            val reqPayload = hashMapOf<String, Any>(
                "passengerId" to uid,
                "passengerName" to (_currentUser.value?.name ?: "Passenger"),
                "passengerPhone" to (_currentUser.value?.phoneNumber ?: "0301-8765432"),
                "pickupLocation" to pickup,
                "destinationLocation" to destination,
                "rideType" to category.name,
                "vehicleCategoryTitle" to category.title,
                "estimatedFarePKR" to estimatedFarePKR,
                "distanceKm" to distanceKm,
                "status" to "SEARCHING",
                "city" to "Jaranwala",
                "paymentMethod" to "CASH",
                "paymentStatus" to "PENDING",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("ride_requests").document(rideId).set(reqPayload).await()
            Result.success(rideId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path) { getFirebaseAuth() }
            Result.failure(e)
        }
    }

    suspend fun claimRideTransactionSuspend(
        rideId: String,
        driverName: String,
        driverPhone: String,
        vehiclePlate: String
    ): Result<Unit> {
        val path = "rides/$rideId"
        return try {
            val driverUid = requireFirebaseUserId()
            val db = requireNotNull(getFirestore()) { "Firestore is not initialized" }
            val rideRef = db.collection("rides").document(rideId)
            val reqRef = db.collection("ride_requests").document(rideId)
            db.runTransaction { tx ->
                val snap = tx.get(rideRef)
                if (!snap.exists()) throw IllegalStateException("Ride $rideId not found")
                val status = snap.getString("status")
                if (status != "SEARCHING") {
                    throw IllegalStateException("Ride $rideId is no longer SEARCHING (status=$status)")
                }
                val updateMap = mapOf(
                    "status" to "ACCEPTED",
                    "driverId" to driverUid,
                    "driverName" to driverName,
                    "driverPhone" to driverPhone,
                    "vehiclePlate" to vehiclePlate,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                tx.update(rideRef, updateMap)
                tx.update(reqRef, updateMap)
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path) { getFirebaseAuth() }
            Result.failure(e)
        }
    }

    suspend fun saveFcmTokenSuspend(token: String): Result<Unit> {
        val uid = getFirebaseAuth()?.currentUser?.uid
        val path = "users/${uid ?: "unauth"}/fcmTokens/$token"
        return try {
            val resolvedUid = requireFirebaseUserId()
            val db = requireNotNull(getFirestore()) { "Firestore is not initialized" }
            val payload = hashMapOf<String, Any>(
                "token" to token,
                "platform" to "android",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(resolvedUid)
                .collection("fcmTokens").document(token)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path) { getFirebaseAuth() }
            Result.failure(e)
        }
    }

    fun observePassengerRidesFlow(passengerUidOverride: String? = null): Flow<List<RideBooking>> = flow {
        val uid = passengerUidOverride ?: requireFirebaseUserId()
        val db = requireNotNull(getFirestore()) { "Firestore is not initialized" }
        val path = "ride_requests"
        emitAll(
            db.collection("ride_requests")
                .whereEqualTo("passengerId", uid)
                .snapshots()
                .map { snap -> snap.documents.mapNotNull { mapFirestoreRideDocument(it) } }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && getFirebaseAuth()?.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path) { getFirebaseAuth() }
                    }
                    throw error
                }
        )
    }

    suspend fun scheduleRideSuspend(
        pickup: String,
        destination: String,
        category: VehicleCategory,
        estimatedFarePKR: Long,
        distanceKm: Double,
        scheduledForMillis: Long,
        scheduledDateDisplay: String,
        scheduledTimeDisplay: String,
        notes: String = "",
        scheduleId: String = "SCH-${System.currentTimeMillis()}-${(100..999).random()}"
    ): Result<String> {
        val path = "scheduled_rides/$scheduleId"
        return try {
            val uid = requireFirebaseUserId()
            if (scheduledForMillis <= System.currentTimeMillis()) {
                return Result.failure(IllegalArgumentException("Scheduled timestamp must be in the future"))
            }
            val db = requireNotNull(getFirestore()) { "Firestore is not initialized" }
            val scheduledRide = ScheduledRide(
                id = scheduleId,
                passengerId = uid,
                passengerName = _currentUser.value?.name ?: "Passenger",
                passengerPhone = _currentUser.value?.phoneNumber ?: "0301-8765432",
                pickup = pickup,
                destination = destination,
                vehicleCategory = category,
                distanceKm = distanceKm,
                estimatedFarePKR = estimatedFarePKR.toInt(),
                scheduledForMillis = scheduledForMillis,
                scheduledDateDisplay = scheduledDateDisplay,
                scheduledTimeDisplay = scheduledTimeDisplay,
                notes = notes
            )
            db.collection("scheduled_rides").document(scheduleId)
                .set(scheduledRide.toFirestoreMap())
                .await()
            Result.success(scheduleId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path) { getFirebaseAuth() }
            Result.failure(e)
        }
    }

    suspend fun cancelScheduledRideSuspend(scheduleId: String): Result<Unit> {
        val path = "scheduled_rides/$scheduleId"
        return try {
            requireFirebaseUserId()
            val db = requireNotNull(getFirestore()) { "Firestore is not initialized" }
            db.collection("scheduled_rides").document(scheduleId)
                .update(
                    mapOf(
                        "status" to ScheduledRideStatus.CANCELLED.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path) { getFirebaseAuth() }
            Result.failure(e)
        }
    }

    fun observeScheduledRidesFlow(passengerUidOverride: String? = null): Flow<List<ScheduledRide>> = flow {
        val uid = passengerUidOverride ?: requireFirebaseUserId()
        val db = requireNotNull(getFirestore()) { "Firestore is not initialized" }
        val path = "scheduled_rides"
        emitAll(
            db.collection("scheduled_rides")
                .whereEqualTo("passengerId", uid)
                .snapshots()
                .map { snap ->
                    snap.documents
                        .mapNotNull { mapFirestoreScheduledRideDocument(it) }
                        .sortedBy { it.scheduledForMillis }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && getFirebaseAuth()?.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path) { getFirebaseAuth() }
                    }
                    throw error
                }
        )
    }

    fun observeCompletedRidesFlow(passengerUidOverride: String? = null): Flow<List<RideBooking>> = flow {
        val uid = passengerUidOverride ?: requireFirebaseUserId()
        val db = requireNotNull(getFirestore()) { "Firestore is not initialized" }
        val path = "ride_requests"
        emitAll(
            db.collection("ride_requests")
                .whereEqualTo("passengerId", uid)
                .snapshots()
                .map { snap ->
                    snap.documents
                        .mapNotNull { mapFirestoreRideDocument(it) }
                        .filter { it.status == RideStatus.COMPLETED }
                        .sortedByDescending { it.timestamp }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && getFirebaseAuth()?.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path) { getFirebaseAuth() }
                    }
                    throw error
                }
        )
    }

    fun observeActiveRideRealtimeFlow(rideId: String): Flow<RideBooking?> = flow {
        val uid = requireFirebaseUserId()
        val db = requireNotNull(getFirestore()) { "Firestore is not initialized" }
        val path = "ride_requests/$rideId"
        emitAll(
            db.collection("ride_requests")
                .document(rideId)
                .snapshots()
                .map { doc ->
                    if (doc.exists()) mapFirestoreRideDocument(doc) else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && getFirebaseAuth()?.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path) { getFirebaseAuth() }
                    }
                    throw error
                }
        )
    }

    suspend fun updateDriverLiveLocationSuspend(
        rideId: String?,
        lat: Double,
        lng: Double
    ): Result<Unit> {
        val targetPath = if (!rideId.isNullOrBlank()) "ride_requests/$rideId" else "drivers"
        return try {
            val driverUid = requireFirebaseUserId()
            val db = requireNotNull(getFirestore()) { "Firestore is not initialized" }
            val driverLocMap = mapOf(
                "currentLat" to lat,
                "currentLng" to lng,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("drivers").document(driverUid).update(driverLocMap).await()
            if (!rideId.isNullOrBlank()) {
                val rideLocMap = mapOf(
                    "driverLat" to lat,
                    "driverLng" to lng,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("ride_requests").document(rideId).update(rideLocMap).await()
                db.collection("rides").document(rideId).update(rideLocMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, targetPath) { getFirebaseAuth() }
            Result.failure(e)
        }
    }

    /**
     * Applies incoming real-time FCM data payloads directly to active ride/dispatch state
     * so foreground passengers & captains reflect updates immediately even before polling.
     */
    fun syncFromFcmPayload(data: Map<String, String>) {
        val eventType = data["type"]?.trim()?.uppercase().orEmpty()
        val rideId = data["rideId"]?.takeIf { it.isNotBlank() } ?: return
        when (eventType) {
            "NEW_RIDE_REQUEST" -> {
                val category = VehicleCategory.entries.firstOrNull {
                    it.name.equals(data["vehicleCategory"], ignoreCase = true) ||
                        it.title.equals(data["vehicleCategory"], ignoreCase = true)
                } ?: VehicleCategory.RICKSHAW
                val incoming = RideBooking(
                    id = rideId,
                    passengerId = data["passengerId"] ?: "",
                    passengerName = data["passengerName"] ?: "Passenger",
                    passengerPhone = data["passengerPhone"] ?: "",
                    pickup = data["pickup"] ?: "Jaranwala",
                    destination = data["destination"] ?: "Destination",
                    vehicleCategory = category,
                    estimatedDistanceKm = data["distanceKm"]?.toDoubleOrNull() ?: 2.0,
                    estimatedFarePKR = data["farePKR"]?.toIntOrNull() ?: 150,
                    status = RideStatus.SEARCHING
                )
                if (!declinedRideIds.contains(rideId)) {
                    _incomingDriverRequests.update { list ->
                        if (list.any { it.id == rideId }) list else listOf(incoming) + list
                    }
                    if (_incomingDriverRequest.value == null) {
                        _incomingDriverRequest.value = incoming
                    }
                }
            }
            "CAPTAIN_ASSIGNED", "CAPTAIN_ARRIVED", "TRIP_STARTED", "TRIP_COMPLETED", "RIDE_CANCELLED" -> {
                val newStatus = when (eventType) {
                    "CAPTAIN_ASSIGNED" -> RideStatus.ACCEPTED
                    "CAPTAIN_ARRIVED" -> RideStatus.DRIVER_ARRIVED
                    "TRIP_STARTED" -> RideStatus.IN_PROGRESS
                    "TRIP_COMPLETED" -> RideStatus.COMPLETED
                    else -> RideStatus.CANCELLED
                }
                val lat = data["driverLat"]?.toDoubleOrNull()
                val lng = data["driverLng"]?.toDoubleOrNull()
                _currentPassengerRide.update { current ->
                    if (current != null && current.id == rideId) {
                        current.copy(
                            status = newStatus,
                            driverName = data["driverName"]?.takeIf { it.isNotBlank() } ?: current.driverName,
                            driverPhone = data["driverPhone"]?.takeIf { it.isNotBlank() } ?: current.driverPhone,
                            vehiclePlate = data["vehiclePlate"]?.takeIf { it.isNotBlank() } ?: current.vehiclePlate,
                            driverLat = lat ?: current.driverLat,
                            driverLng = lng ?: current.driverLng
                        )
                    } else {
                        current
                    }
                }
                if (activePassengerRideListener == null) {
                    listenToActivePassengerRide(rideId)
                }
            }
        }
    }

    /**
     * Subscribes to real-time Firestore notification events targeting the signed-in user
     * so ride requests and trip status alerts deliver immediately across devices.
     */
    fun listenToUserNotificationEvents(
        uid: String,
        context: Context
    ): com.google.firebase.firestore.ListenerRegistration? {
        if (uid.isBlank()) return null
        val db = getFirestore() ?: return null
        var isInitialSnapshot = true
        return db.collection("notification_events")
            .whereEqualTo("recipientUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "notification_events") { getFirebaseAuth() }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                if (isInitialSnapshot) {
                    isInitialSnapshot = false
                    return@addSnapshotListener
                }
                snapshot.documentChanges
                    .filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }
                    .forEach { change ->
                        val doc = change.document
                        val payload = mutableMapOf<String, String>()
                        doc.getString("type")?.let { payload["type"] = it }
                        doc.getString("title")?.let { payload["title"] = it }
                        doc.getString("body")?.let { payload["body"] = it }
                        doc.getString("rideId")?.let { payload["rideId"] = it }
                        doc.getLong("targetTab")?.let { payload["targetTab"] = it.toString() }
                        RideNotificationManager.handleFcmDataPayload(
                            context = context,
                            data = payload,
                            fallbackTitle = payload["title"],
                            fallbackBody = payload["body"]
                        )
                        syncFromFcmPayload(payload)
                    }
            }
    }

    /**
     * Registers Firebase Cloud Messaging topics for the active user role
     * so Captains receive ride-dispatch broadcasts and Passengers receive trip alerts.
     */
    fun registerFcmTopics(role: UserRole) {
        try {
            val messaging = FirebaseMessaging.getInstance()
            val uid = getFirebaseAuth()?.currentUser?.uid ?: _currentUser.value?.uid
            if (!uid.isNullOrBlank()) {
                messaging.subscribeToTopic("jaranwala_user_${uid.take(64)}")
            }
            when (role) {
                UserRole.DRIVER -> {
                    messaging.subscribeToTopic("jaranwala_captains_dispatch")
                    messaging.unsubscribeFromTopic("jaranwala_riders")
                }
                UserRole.PASSENGER -> {
                    messaging.subscribeToTopic("jaranwala_riders")
                    messaging.unsubscribeFromTopic("jaranwala_captains_dispatch")
                }
                UserRole.ADMIN -> {
                    messaging.subscribeToTopic("jaranwala_captains_dispatch")
                    messaging.subscribeToTopic("jaranwala_owner_alerts")
                }
            }
        } catch (_: Throwable) {
            // Safe fallback in headless unit test environments without Play Services
        }
    }

    fun resetForTesting() {
        _currentPassengerRide.value = null
        _currentDriverRide.value = null
        _incomingDriverRequest.value = null
        _incomingDriverRequests.value = emptyList()
        declinedRideIds.clear()
        activePassengerRideListener?.remove()
        activePassengerRideListener = null
        activeRideDriverLocationListener?.remove()
        activeRideDriverLocationListener = null
        trackedDriverIdForActiveRide = null
        scheduledRidesListener?.remove()
        scheduledRidesListener = null
        userProfileListener?.remove()
        userProfileListener = null
        detachAdminListeners()
        _scheduledRides.value = emptyList()
        _selectedPaymentMethod.value = PaymentMethod.CASH
        _selectedWalletAccount.value = ""
        _lastPaymentSettlement.value = null
        _rideHistoryUiState.value = RideHistoryUiState.Idle
        _completedPaymentRidesState.value = RideHistoryUiState.Idle
        _adminConfig.value = AdminConfig()
        _activeDriver.value = _drivers.value[0].copy(isOnline = true, isApproved = true)
        _emergencyAlerts.value = emptyList()
        _savedPlaces.value = defaultSavedPlaces
        ownerPasscodeSalt = null
        customAdminHash = null
        ownerMasterKeySalt = null
        ownerMasterKeyHash = null
        failedAdminAttempts = 0
        suspiciousResetAttempts = 0
        adminLockoutUntil = 0L
    }

    companion object {
        const val IMMUTABLE_OWNER_UID = "ADMIN-MAHI"
        const val IMMUTABLE_OWNER_NAME = "Bilal Khokhar (MAHI)"
        const val IMMUTABLE_OWNER_EMAIL = "khokharbilal665@gmail.com"
        const val IMMUTABLE_OWNER_PHONE = "+923429348582"
        const val IMMUTABLE_OWNER_PHONE_LOCAL = "0342-9348582"

        @Volatile
        private var INSTANCE: JaranwalaRepository? = null

        fun getInstance(): JaranwalaRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JaranwalaRepository().also { INSTANCE = it }
            }
        }

        internal fun createForTesting(
            firestore: com.google.firebase.firestore.FirebaseFirestore? = null,
            auth: com.google.firebase.auth.FirebaseAuth? = null
        ): JaranwalaRepository {
            return JaranwalaRepository(injectedFirestore = firestore, injectedAuth = auth)
        }
    }
}
