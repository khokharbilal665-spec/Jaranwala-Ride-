package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class JaranwalaRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createRideSuspend_authenticatedPassenger_createsSearchingRide() = runBlocking {
        val uid = signInTestUser(PASSENGER_EMAIL)
        val repository = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)
        val rideId = "JRW-TEST-${UUID.randomUUID().toString().take(8)}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createRideSuspend(
                pickup = "Committee Chowk, Jaranwala",
                destination = "Railway Station Jaranwala",
                category = VehicleCategory.RICKSHAW,
                estimatedFarePKR = 180L,
                distanceKm = 2.6,
                rideId = rideId
            )
        }
        assertTrue("Expected ride create success, got: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        assertEquals(rideId, createResult.getOrThrow())

        val doc = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("ride_requests").document(rideId).get().await()
        }
        assertTrue(doc.exists())
        assertEquals(uid, doc.getString("passengerId"))
        assertEquals("SEARCHING", doc.getString("status"))
        assertEquals("CASH", doc.getString("paymentMethod"))
    }

    @Test
    fun observePassengerRidesFlow_emitsRealTimeRideUpdate() = runBlocking {
        signInTestUser(PASSENGER_EMAIL)
        val repository = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)
        val rideId = "JRW-FLOW-${UUID.randomUUID().toString().take(8)}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createRideSuspend(
                pickup = "Grain Market Jaranwala",
                destination = "THQ Hospital Jaranwala",
                category = VehicleCategory.BIKE,
                estimatedFarePKR = 110L,
                distanceKm = 1.8,
                rideId = rideId
            ).getOrThrow()
        }

        val emittedRides = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePassengerRidesFlow().first { list -> list.any { it.id == rideId } }
        }
        assertTrue(emittedRides.any { it.id == rideId && it.status == RideStatus.SEARCHING })
    }

    @Test
    fun observePassengerRidesFlow_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observePassengerRidesFlow(passengerUidOverride = "some_passenger_uid").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated ride_requests listener")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        }
    }

    @Test
    fun claimRideTransactionSuspend_unapprovedDriver_failsWithPermissionDenied() = runBlocking {
        // Passenger creates ride
        signInTestUser(PASSENGER_EMAIL)
        val passengerRepo = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)
        val rideId = "JRW-UNAPPR-${UUID.randomUUID().toString().take(8)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            passengerRepo.createRideSuspend(
                pickup = "Cinema Chowk",
                destination = "Chak 240 GB",
                category = VehicleCategory.CAR,
                estimatedFarePKR = 350L,
                distanceKm = 6.2,
                rideId = rideId
            ).getOrThrow()
        }

        // Unapproved driver registers with isApproved = false (unique per run so createdAt never collides across re-runs)
        val uniqueDriverEmail = "unapproved.${UUID.randomUUID().toString().take(8)}@jaranwala.pk"
        val driverUid = signInTestUser(uniqueDriverEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("drivers").document(driverUid).set(
                mapOf(
                    "driverId" to driverUid,
                    "name" to "Captain Pending",
                    "phone" to "0300-1231231",
                    "vehicleType" to "CAR",
                    "isOnline" to false,
                    "isApproved" to false,
                    "createdAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
            ).await()
        }

        val driverRepo = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)
        val claimResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            driverRepo.claimRideTransactionSuspend(
                rideId = rideId,
                driverName = "Captain Pending",
                driverPhone = "0300-1231231",
                vehiclePlate = "FSD-000"
            )
        }
        assertTrue("Expected unapproved driver claim to fail", claimResult.isFailure)
        val firestoreException = claimResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun saveFcmTokenSuspend_authenticatedUser_persistsToken() = runBlocking {
        val uid = signInTestUser(PASSENGER_EMAIL)
        val repository = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)
        val tokenId = "fcm_tok_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveFcmTokenSuspend(tokenId)
        }
        assertTrue("Expected FCM token save to succeed: ${saveResult.exceptionOrNull()}", saveResult.isSuccess)

        val tokenDoc = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(uid).collection("fcmTokens").document(tokenId).get().await()
        }
        assertTrue(tokenDoc.exists())
        assertEquals(tokenId, tokenDoc.getString("token"))
        assertEquals("android", tokenDoc.getString("platform"))
    }

    @Test
    fun adminConfigSecurity_nonOwner_failsWithPermissionDenied() = runBlocking {
        signInTestUser(PASSENGER_EMAIL)

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("config").document("security").get().await()
            }
            fail("Expected PERMISSION_DENIED when non-owner reads /config/security")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("config").document("security").set(
                    mapOf("ownerPasscodeHash" to "forged")
                ).await()
            }
            fail("Expected PERMISSION_DENIED when non-owner writes /config/security")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    @Test
    fun scheduleRideSuspend_authenticatedPassenger_createsScheduledRide() = runBlocking {
        val uid = signInTestUser(PASSENGER_EMAIL)
        val repository = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)
        val scheduleId = "SCH-RULE-${UUID.randomUUID().toString().take(8)}"
        val futureMs = System.currentTimeMillis() + 7_200_000L

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.scheduleRideSuspend(
                pickup = "Committee Chowk, Jaranwala",
                destination = "Faisalabad Clock Tower",
                category = VehicleCategory.CAR,
                estimatedFarePKR = 1650L,
                distanceKm = 36.4,
                scheduledForMillis = futureMs,
                scheduledDateDisplay = "16 Nov 2026",
                scheduledTimeDisplay = "09:30 AM",
                notes = "Airport morning trip",
                scheduleId = scheduleId
            )
        }
        assertTrue("Expected scheduled ride creation to succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(scheduleId, result.getOrThrow())

        val doc = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("scheduled_rides").document(scheduleId).get().await()
        }
        assertTrue(doc.exists())
        assertEquals(uid, doc.getString("passengerId"))
        assertEquals("SCHEDULED", doc.getString("status"))
        assertEquals(futureMs, doc.getLong("scheduledForMillis"))

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeScheduledRidesFlow().first { list -> list.any { it.id == scheduleId } }
        }
        assertTrue(emitted.any { it.id == scheduleId && it.status == ScheduledRideStatus.SCHEDULED })
    }

    @Test
    fun observeScheduledRidesFlow_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeScheduledRidesFlow(passengerUidOverride = "some_passenger_uid").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated scheduled_rides listener")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        }
    }

    @Test
    fun cancelScheduledRideSuspend_crossUser_failsWithPermissionDenied() = runBlocking {
        signInTestUser(PASSENGER_EMAIL)
        val ownerRepo = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)
        val scheduleId = "SCH-ISO-${UUID.randomUUID().toString().take(8)}"
        val futureMs = System.currentTimeMillis() + 3_600_000L

        withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.scheduleRideSuspend(
                pickup = "Grain Market",
                destination = "Railway Station Jaranwala",
                category = VehicleCategory.RICKSHAW,
                estimatedFarePKR = 220L,
                distanceKm = 2.9,
                scheduledForMillis = futureMs,
                scheduledDateDisplay = "16 Nov 2026",
                scheduledTimeDisplay = "04:00 PM",
                scheduleId = scheduleId
            ).getOrThrow()
        }

        // Switch to another passenger -> cancel MUST fail with PERMISSION_DENIED
        signInTestUser("other.passenger.${UUID.randomUUID().toString().take(6)}@jaranwala.pk")
        val otherRepo = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)
        val cancelResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            otherRepo.cancelScheduledRideSuspend(scheduleId)
        }
        assertTrue("Expected cross-user cancel to fail", cancelResult.isFailure)
        val firestoreException = cancelResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun observeCompletedRidesFlow_emitsOnlyCompletedRides() = runBlocking {
        signInTestUser(PASSENGER_EMAIL)
        val repository = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)
        val searchingId = "JRW-SEARCH-${UUID.randomUUID().toString().take(8)}"
        val completedId = "JRW-COMP-${UUID.randomUUID().toString().take(8)}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createRideSuspend(
                pickup = "Cinema Chowk",
                destination = "THQ Hospital",
                category = VehicleCategory.BIKE,
                estimatedFarePKR = 110L,
                distanceKm = 1.9,
                rideId = searchingId
            ).getOrThrow()
            repository.createRideSuspend(
                pickup = "Committee Chowk",
                destination = "Lahore Road Bypass",
                category = VehicleCategory.RICKSHAW,
                estimatedFarePKR = 240L,
                distanceKm = 3.5,
                rideId = completedId
            ).getOrThrow()

            // Transition completedId to COMPLETED in local Firestore emulator using admin Bearer owner :commit
            val docPath = "projects/demo-no-project/databases/$databaseId/documents/ride_requests/$completedId"
            val url = java.net.URL(
                "http://127.0.0.1:8085/v1/projects/demo-no-project/databases/$databaseId/documents:commit"
            )
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer owner")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            val body = """
                {
                  "writes": [{
                    "update": {
                      "name": "$docPath",
                      "fields": {
                        "status": {"stringValue": "COMPLETED"},
                        "paymentStatus": {"stringValue": "COLLECTED_CASH"}
                      }
                    },
                    "updateMask": {"fieldPaths": ["status", "paymentStatus"]}
                  }]
                }
            """.trimIndent()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            assertEquals("Expected emulator :commit 200 for $completedId", 200, code)
            conn.disconnect()
        }

        val completedOnly = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCompletedRidesFlow().first { list -> list.any { it.id == completedId } }
        }
        assertTrue(completedOnly.any { it.id == completedId && it.status == RideStatus.COMPLETED })
        assertTrue(completedOnly.none { it.id == searchingId })
    }

    @Test
    fun observeActiveRideRealtimeFlow_driverClaimAndLocationUpdate_emitsLiveMarkerCoords() = runBlocking {
        // 1. Passenger creates a searching ride
        signInTestUser(PASSENGER_EMAIL)
        val passengerRepo = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)
        val rideId = "JRW-TRACK-${UUID.randomUUID().toString().take(8)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            passengerRepo.createRideSuspend(
                pickup = "Committee Chowk, Jaranwala",
                destination = "Jaranwala Railway Station",
                category = VehicleCategory.RICKSHAW,
                estimatedFarePKR = 190L,
                distanceKm = 2.5,
                rideId = rideId
            ).getOrThrow()
        }

        // 2. Driver registers & gets approved by owner in emulator
        val driverEmail = "approved.driver.${UUID.randomUUID().toString().take(8)}@jaranwala.pk"
        val driverUid = signInTestUser(driverEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("drivers").document(driverUid).set(
                mapOf(
                    "driverId" to driverUid,
                    "name" to "Captain Tariq",
                    "phone" to "0300-1122334",
                    "vehicleType" to "RICKSHAW",
                    "isOnline" to true,
                    "isApproved" to false,
                    "createdAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
            ).await()

            val driverPath = "projects/demo-no-project/databases/$databaseId/documents/drivers/$driverUid"
            val url = java.net.URL(
                "http://127.0.0.1:8085/v1/projects/demo-no-project/databases/$databaseId/documents:commit"
            )
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer owner")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            val body = """
                {
                  "writes": [{
                    "update": {
                      "name": "$driverPath",
                      "fields": {
                        "isApproved": {"booleanValue": true}
                      }
                    },
                    "updateMask": {"fieldPaths": ["isApproved"]}
                  }]
                }
            """.trimIndent()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            assertEquals("Expected driver approval :commit 200", 200, conn.responseCode)
            conn.disconnect()
        }

        // 3. Approved driver claims ride & updates live GPS coordinates
        val driverRepo = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            driverRepo.claimRideTransactionSuspend(
                rideId = rideId,
                driverName = "Captain Tariq",
                driverPhone = "0300-1122334",
                vehiclePlate = "FSD-4412"
            ).getOrThrow()

            driverRepo.updateDriverLiveLocationSuspend(
                rideId = rideId,
                lat = 31.3418,
                lng = 73.4242
            ).getOrThrow()
        }

        // 4. Passenger switches back and observes live marker coordinates from Firestore real-time flow
        signInTestUser(PASSENGER_EMAIL)
        val tracked = withTimeout(FLOW_TIMEOUT_MS) {
            passengerRepo.observeActiveRideRealtimeFlow(rideId).first { booking ->
                booking != null &&
                    booking.status == RideStatus.ACCEPTED &&
                    booking.driverLat == 31.3418 &&
                    booking.driverLng == 73.4242
            }
        }
        assertNotNull(tracked)
        assertEquals(RideStatus.ACCEPTED, tracked!!.status)
        assertEquals("Captain Tariq", tracked.driverName)
        assertEquals(31.3418, tracked.driverLat ?: 0.0, 0.0001)
        assertEquals(73.4242, tracked.driverLng ?: 0.0, 0.0001)
    }

    @Test
    fun updateUserProfileFirestore_authenticatedUser_createsAndUpdatesEmergencyContact() = runBlocking {
        val uid = signInTestUser(PASSENGER_EMAIL)
        val repository = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)

        // First write (create or update /users/{uid})
        val firstResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateUserProfileFirestoreSuspend(
                displayName = "Ali Raza",
                phoneNumber = "0301-8765432",
                emergencyContactNumber = "0342-9348582"
            )
        }
        assertTrue("Expected first profile write to succeed, got: ${firstResult.exceptionOrNull()}", firstResult.isSuccess)

        val snap1 = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(uid).get().await()
        }
        assertTrue(snap1.exists())
        assertEquals("Ali Raza", snap1.getString("name"))
        assertEquals("0301-8765432", snap1.getString("phoneNumber"))
        assertEquals("0342-9348582", snap1.getString("emergencyContact"))
        val createdAt1 = snap1.getTimestamp("createdAt")
        assertNotNull(createdAt1)

        // Partial update preserves createdAt while updating display name, phone, and emergency contact
        val secondResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateUserProfileFirestoreSuspend(
                displayName = "Ali Raza Jaranwala",
                phoneNumber = "0302-1112233",
                emergencyContactNumber = "0300-7778899"
            )
        }
        assertTrue("Expected profile update to succeed, got: ${secondResult.exceptionOrNull()}", secondResult.isSuccess)

        val snap2 = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(uid).get().await()
        }
        assertEquals("Ali Raza Jaranwala", snap2.getString("name"))
        assertEquals("0302-1112233", snap2.getString("phoneNumber"))
        assertEquals("0300-7778899", snap2.getString("emergencyContact"))
        assertEquals(createdAt1, snap2.getTimestamp("createdAt"))
    }

    @Test
    fun updateUserProfileFirestore_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateUserProfileFirestoreSuspend(
                displayName = "Intruder",
                phoneNumber = "0300-0000000",
                emergencyContactNumber = "0300-1111111",
                targetUidOverride = "some_other_uid"
            )
        }
        assertTrue(result.isFailure)
        val firestoreException = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException for unauthenticated user write", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)
    }

    @Test
    fun verifyPhoneOtpAndLogin_returningUser_preservesNameAndUpdatesAllowedFields() = runBlocking {
        val uid = signInTestUser(PASSENGER_EMAIL)
        val repository = JaranwalaRepository.createForTesting(firestore = firestore, auth = auth)

        // Seed existing Firestore profile with custom name & emergency contact
        val initial = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateUserProfileFirestoreSuspend(
                displayName = "Bilal Khokhar",
                phoneNumber = "0301-8765432",
                emergencyContactNumber = "0342-9348582"
            )
        }
        assertTrue(initial.isSuccess)
        val createdSnap = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(uid).get().await()
        }
        val originalCreatedAt = createdSnap.getTimestamp("createdAt")

        // Simulate returning Phone OTP login (fullName blank as on Login tab)
        val loggedIn = repository.verifyPhoneOtpAndLogin(
            phone = "+923018765432",
            otp = "",
            role = com.example.data.UserRole.PASSENGER,
            fullName = "",
            firebaseUid = uid,
            isFirebaseSdkVerified = true
        )
        assertTrue(loggedIn)

        // Allow async Firestore update + syncUserFromFirestore to settle
        var syncedName: String? = null
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(150)
            val snap = firestore.collection("users").document(uid).get().await()
            if (snap.exists() && snap.getString("phoneNumber") == "0301-8765432") {
                syncedName = snap.getString("name")
                if (repository.currentUser.value?.name == "Bilal Khokhar") break
            }
        }

        val finalSnap = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(uid).get().await()
        }
        assertEquals("Bilal Khokhar", finalSnap.getString("name"))
        assertEquals("0342-9348582", finalSnap.getString("emergencyContact"))
        assertEquals(originalCreatedAt, finalSnap.getTimestamp("createdAt"))
        assertEquals("Bilal Khokhar", syncedName)
    }

    private companion object {
        const val PASSENGER_EMAIL = "passenger.rule@jaranwala.pk"
        const val UNAPPROVED_DRIVER_EMAIL = "unapproved.driver@jaranwala.pk"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 8000L
    }
}
