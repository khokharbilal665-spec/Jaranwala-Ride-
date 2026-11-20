package com.example

import com.example.data.AdminAuthResult
import com.example.data.AppLanguage
import com.example.data.ConvoyMixType
import com.example.data.DecorationTier
import com.example.data.DriverRegDetails
import com.example.data.JaranwalaRepository
import com.example.data.PaymentMethod
import com.example.data.RideHistoryUiState
import com.example.data.RideStatus
import com.example.data.VehicleCategory
import com.example.data.OwnerAdminLoginResult
import com.example.data.PasswordResetResult
import com.example.data.SecurityEventType
import com.example.data.UserRole
import com.example.data.WeddingCarType
import com.example.data.UserRoleSecurityManager
import com.example.data.UserAuthContext
import com.example.data.OperationPermissionResult
import com.example.util.PakistanPhoneValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExampleUnitTest {

    private lateinit var repository: JaranwalaRepository

    @Before
    fun setUp() {
        repository = JaranwalaRepository.getInstance()
        repository.resetForTesting()
    }

    @Test
    fun testFareCalculation() {
        val fareBike = repository.calculateFare(VehicleCategory.BIKE, 4.0)
        assertTrue(fareBike >= 70) // Base fare is 70 PKR

        val fareRickshaw = repository.calculateFare(VehicleCategory.RICKSHAW, 4.0)
        assertTrue(fareRickshaw > fareBike) // Rickshaw is priced higher than bike

        val fareCar = repository.calculateFare(VehicleCategory.CAR, 4.0)
        assertTrue(fareCar > fareRickshaw) // Car is highest with AC
    }

    @Test
    fun testJaranwalaLandmarksPresent() {
        assertTrue(repository.jaranwalaLocations.any { it.name.contains("Railway Station") })
        assertTrue(repository.jaranwalaLocations.any { it.name.contains("Committee Chowk") })
        assertTrue(repository.jaranwalaLocations.any { it.name.contains("Cinema Chowk") })
        assertTrue(repository.jaranwalaLocations.any { it.name.contains("Grain Market") })
    }

    @Test
    fun testPassengerBookingFlow() {
        // 1. Request ride
        repository.requestRide(
            passengerName = "Bilal Khan",
            passengerPhone = "0300-1234567",
            pickup = "Committee Chowk",
            destination = "Jaranwala Railway Station",
            category = VehicleCategory.RICKSHAW
        )

        val activeRide = repository.currentPassengerRide.value
        assertNotNull(activeRide)
        assertEquals(VehicleCategory.RICKSHAW, activeRide?.vehicleCategory)
        assertTrue(activeRide?.status == RideStatus.SEARCHING || activeRide?.status == RideStatus.ACCEPTED)

        // 2. Start Trip
        repository.startTrip()
        assertEquals(RideStatus.IN_PROGRESS, repository.currentPassengerRide.value?.status)

        // 3. Complete Trip
        repository.completeTrip()
        assertEquals(RideStatus.COMPLETED, repository.currentPassengerRide.value?.status)

        // 4. Rate Experience
        repository.submitRating(5, "Very smooth ride in Jaranwala")
        assertNull(repository.currentPassengerRide.value)
    }

    @Test
    fun testDriverFlow() {
        // 1. Online/Offline toggle
        repository.setDriverOnline(true)
        assertTrue(repository.activeDriver.value.isOnline)

        repository.setDriverOnline(false)
        assertFalse(repository.activeDriver.value.isOnline)
        assertNull(repository.incomingDriverRequest.value)

        // 2. Turn back online
        repository.setDriverOnline(true)
        val initialEarnings = repository.activeDriver.value.todayEarningsPKR

        // 3. Request ride matching driver's vehicle
        val driverCat = repository.activeDriver.value.vehicleCategory
        repository.requestRide(
            passengerName = "Test Rider",
            passengerPhone = "0301-1122334",
            pickup = "Cinema Chowk",
            destination = "Mandi Road",
            category = driverCat
        )

        val incoming = repository.incomingDriverRequest.value
        assertNotNull(incoming)

        // 4. Accept Ride
        repository.acceptDriverRequest()
        assertNull(repository.incomingDriverRequest.value)
        assertEquals(RideStatus.ACCEPTED, repository.currentDriverRide.value?.status)

        // 5. Driver Arrived
        repository.setDriverArrived()
        assertEquals(RideStatus.DRIVER_ARRIVED, repository.currentDriverRide.value?.status)

        // 6. Start & Complete Trip
        repository.startTrip()
        assertEquals(RideStatus.IN_PROGRESS, repository.currentDriverRide.value?.status)

        repository.completeTrip()
        assertNull(repository.currentDriverRide.value)
        assertTrue(repository.activeDriver.value.todayEarningsPKR > initialEarnings)
    }

    @Test
    fun testDriverRejectRequest() {
        repository.setDriverOnline(true)
        val driverCat = repository.activeDriver.value.vehicleCategory
        repository.requestRide(
            passengerName = "Decline Rider",
            passengerPhone = "0300-9988776",
            pickup = "Katchery Bazar",
            destination = "Satyana Road",
            category = driverCat
        )
        assertNotNull(repository.incomingDriverRequest.value)

        repository.rejectDriverRequest()
        assertNull(repository.incomingDriverRequest.value)
    }

    @Test
    fun testWeddingAndBaratBooking() {
        // 1. Single Bridal Car
        val bridalBooking = repository.bookWeddingService(
            isBarat = false,
            name = "Hamza Malik",
            phone = "0302-9988771",
            carModel = WeddingCarType.PRADO_TX.modelName,
            carCount = 1,
            decoration = DecorationTier.ROYAL_ROSES,
            date = "30 Nov 2026",
            time = "05:00 PM",
            pickup = "Cinema Chowk Jaranwala",
            venue = "Taj Palace Marquee",
            notes = "Fresh red rose floral garlands"
        )
        assertNotNull(bridalBooking)
        assertEquals("CONFIRMED", bridalBooking.status)
        assertFalse(bridalBooking.isBaratConvoy)
        assertFalse(bridalBooking.isDemoData)

        // 2. Barat Convoy
        val baratBooking = repository.bookWeddingService(
            isBarat = true,
            name = "Chaudhry Shahbaz",
            phone = "0300-5551234",
            carModel = "8 Cars Convoy (Executive Mix)",
            carCount = 8,
            convoyMix = ConvoyMixType.EXECUTIVE_MIX,
            decoration = DecorationTier.VIP_DELUXE,
            date = "05 Dec 2026",
            time = "02:00 PM",
            pickup = "Grain Market Jaranwala",
            venue = "Serena Hotel Faisalabad",
            notes = "Full convoy lead with coordination flags"
        )
        assertNotNull(baratBooking)
        assertTrue(baratBooking.isBaratConvoy)
        assertEquals(8, baratBooking.carCount)
        assertTrue(baratBooking.totalCostPKR >= (8 * 16000))

        // 3. Cancel Booking
        repository.cancelWeddingBooking(baratBooking.id)
        val updated = repository.weddingBookings.value.find { it.id == baratBooking.id }
        assertEquals("CANCELLED", updated?.status)
    }

    @Test
    fun testAdminSecurityGate() {
        // 1. Invalid PIN rejected
        val badResult = repository.verifyAdminPin("wrong_pin")
        assertTrue(badResult is AdminAuthResult.InvalidCredentials)

        // 2. Lockout triggered after repeated failures
        repository.verifyAdminPin("bad_pin_2")
        val lockoutResult = repository.verifyAdminPin("bad_pin_3")
        assertTrue(lockoutResult is AdminAuthResult.LockedOut)

        // 3. While locked out, any input is blocked
        val blockedResult = repository.verifyAdminPin("7860")
        assertTrue(blockedResult is AdminAuthResult.LockedOut)
    }

    @Test
    fun testAdminPasscodeConfigurationAndSuccess() {
        repository.resetForTesting()
        // Configure new passcode securely
        repository.setAdminPasscode("8899")
        assertTrue(repository.isPasscodeConfigured())

        // Wrong passcode should fail
        val wrongResult = repository.verifyAdminPin("1122")
        assertTrue(wrongResult is AdminAuthResult.InvalidCredentials)

        // Configured passcode should succeed
        val successResult = repository.verifyAdminPin("8899")
        assertTrue(successResult is AdminAuthResult.Success)
    }

    @Test
    fun testPakistanGeoServiceLocationsAndDistance() {
        // Jaranwala default coordinates
        assertEquals(31.3333, com.example.util.PakistanGeoService.JARANWALA_LAT, 0.001)
        assertEquals(73.4333, com.example.util.PakistanGeoService.JARANWALA_LNG, 0.001)

        // Verify Pakistan boundary checking
        assertTrue(com.example.util.PakistanGeoService.isWithinPakistan(31.3333, 73.4333)) // Jaranwala
        assertTrue(com.example.util.PakistanGeoService.isWithinPakistan(31.5204, 74.3587)) // Lahore
        assertTrue(com.example.util.PakistanGeoService.isWithinPakistan(24.8607, 67.0011)) // Karachi
        // US coordinates (e.g. Mountain View 37.422, -122.084) must be rejected
        assertFalse(com.example.util.PakistanGeoService.isWithinPakistan(37.422, -122.084))

        // Verify distance calculation between Jaranwala and Faisalabad is ~30-40 km
        val distFsd = com.example.util.PakistanGeoService.calculateDistanceKm(
            31.3333, 73.4333, 31.4187, 73.0791
        )
        assertTrue(distFsd in 28.0..45.0)

        // Verify search finds Jaranwala and surrounding villages
        val results = com.example.util.PakistanGeoService.searchPakistan("Syedwala")
        assertTrue(results.isNotEmpty())
        assertEquals("Syedwala, Jaranwala", results[0].name)
    }

    @Test
    fun testAdminFareUpdateAndComplaints() {
        // Update fares
        repository.updateFares(
            bikeBase = 80,
            bikeKm = 30,
            rickshawBase = 130,
            rickshawKm = 40,
            carBase = 250,
            carKm = 70,
            commission = 12
        )
        val config = repository.adminConfig.value
        assertEquals(80, config.bikeBaseFare)
        assertEquals(130, config.rickshawBaseFare)
        assertEquals(250, config.carBaseFare)
        assertEquals(12, config.platformCommissionPercent)

        // File Complaint
        repository.submitComplaint("RIDE-99", "Ali Raza", "Route Delay", "تاخیر", "Delayed arrival")
        val ticket = repository.complaints.value.find { it.rideId == "RIDE-99" }
        assertNotNull(ticket)
        assertEquals("OPEN", ticket?.status)

        // Resolve Complaint
        ticket?.let { repository.resolveComplaint(it.id) }
        val resolved = repository.complaints.value.find { it.rideId == "RIDE-99" }
        assertEquals("RESOLVED", resolved?.status)
    }

    @Test
    fun testLanguageSelection() {
        repository.setLanguage(AppLanguage.URDU)
        assertEquals(AppLanguage.URDU, repository.appLanguage.value)

        repository.setLanguage(AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, repository.appLanguage.value)

        repository.setLanguage(AppLanguage.BILINGUAL)
        assertEquals(AppLanguage.BILINGUAL, repository.appLanguage.value)
    }

    @Test
    fun testPromoCodeValidation() {
        // 1. Empty promo code
        val emptyResult = repository.validatePromoCode("", farePKR = 200)
        assertTrue(emptyResult is com.example.data.PromoValidationResult.Error)

        // 2. Invalid promo code
        val invalidResult = repository.validatePromoCode("FAKECODE", farePKR = 200)
        assertTrue(invalidResult is com.example.data.PromoValidationResult.Error)

        // 3. Valid Percentage Code: MAHI (25% off up to 100 PKR)
        val mahiResult = repository.validatePromoCode("MAHI", farePKR = 200)
        assertTrue(mahiResult is com.example.data.PromoValidationResult.Success)
        val successMahi = mahiResult as com.example.data.PromoValidationResult.Success
        assertEquals(50, successMahi.discountAmountPKR) // 25% of 200 = 50
        assertEquals(150, successMahi.finalFarePKR)

        // 4. Valid Flat Code: FIRST (50 PKR flat off)
        val firstResult = repository.validatePromoCode("FIRST", farePKR = 200)
        assertTrue(firstResult is com.example.data.PromoValidationResult.Success)
        val successFirst = firstResult as com.example.data.PromoValidationResult.Success
        assertEquals(50, successFirst.discountAmountPKR)
        assertEquals(150, successFirst.finalFarePKR)

        // 5. Case-insensitivity test
        val lowerMahi = repository.validatePromoCode("mahi", farePKR = 100)
        assertTrue(lowerMahi is com.example.data.PromoValidationResult.Success)
    }

    @Test
    fun testSavedPlacesManagement() {
        // Initial places contain Home, Work, College
        assertTrue(repository.savedPlaces.value.any { it.label == "Home" })

        // Add new saved place
        val newPlace = repository.addSavedPlace(
            label = "Taj Marquee",
            address = "Taj Palace Marquee, Jaranwala",
            urduLabel = "تاج پیلس",
            iconType = "FAVORITE"
        )
        assertNotNull(newPlace)
        assertTrue(repository.savedPlaces.value.any { it.id == newPlace.id })

        // Remove saved place
        repository.removeSavedPlace(newPlace.id)
        assertFalse(repository.savedPlaces.value.any { it.id == newPlace.id })
    }

    @Test
    fun testRoomCompletedRideEntityCreation() {
        val entity = com.example.data.local.CompletedRideEntity(
            id = "RIDE-TEST-101",
            passengerName = "Ahmed Raza",
            passengerPhone = "0300-1122334",
            pickup = "Committee Chowk, Jaranwala",
            destination = "Cinema Chowk, Jaranwala",
            vehicleCategory = "RICKSHAW",
            distanceKm = 3.5,
            farePKR = 180,
            captainName = "Captain Arshad",
            captainPhone = "0300-7654321",
            vehiclePlate = "FS-23-4412",
            ratingGiven = 5,
            reviewGiven = "Fast and polite",
            promoCode = "MAHI",
            discountPKR = 20
        )
        assertEquals("RIDE-TEST-101", entity.id)
        assertEquals(180, entity.farePKR)
        assertEquals(5, entity.ratingGiven)
        assertEquals("MAHI", entity.promoCode)
        assertTrue(entity.completedAt > 0)
    }

    @Test
    fun testRoomWeddingConvoyHistoryEntityCreation() {
        val entity = com.example.data.local.WeddingConvoyHistoryEntity(
            id = "WED-TEST-202",
            isBaratConvoy = true,
            customerName = "Chaudhry Akram",
            customerPhone = "0300-8889999",
            primaryCar = "Honda Civic & Prado Mix",
            carCount = 6,
            convoyMix = "EXECUTIVE_MIX",
            decorationTier = "VIP_DELUXE",
            durationHours = 6,
            durationLabel = "6 Hours (Standard Event)",
            eventDate = "15 Dec 2026",
            eventTime = "04:00 PM",
            pickupLocation = "Grain Market, Jaranwala",
            venueLocation = "Royal Palm Marquee, Faisalabad Road",
            totalCostPKR = 120000,
            status = "CONFIRMED"
        )
        assertEquals("WED-TEST-202", entity.id)
        assertTrue(entity.isBaratConvoy)
        assertEquals(6, entity.carCount)
        assertEquals(120000, entity.totalCostPKR)
        assertEquals("CONFIRMED", entity.status)
    }

    @Test
    fun testPostRideExperienceRatingFlow() {
        // Request and complete a ride
        repository.requestRide(
            passengerName = "Ahmed",
            passengerPhone = "0300-1112222",
            pickup = "Railway Station",
            destination = "Mandi Road",
            category = VehicleCategory.CAR
        )
        val activeRide = repository.currentPassengerRide.value
        assertNotNull(activeRide)

        // Complete the ride
        repository.completeTrip()
        assertEquals(RideStatus.COMPLETED, repository.currentPassengerRide.value?.status)

        // Submit star rating with tip and written feedback
        val initialHistoryCount = repository.passengerHistory.value.size
        repository.submitDriverRating(
            rideId = activeRide!!.id,
            driverId = "DRV-1",
            stars = 5,
            review = "Excellent driving and clean car"
        )

        // Active ride is cleared and logged into history with rating updated
        assertNull(repository.currentPassengerRide.value)
        assertEquals(initialHistoryCount, repository.passengerHistory.value.size)
        val latestHistory = repository.passengerHistory.value.first()
        assertEquals(activeRide.id, latestHistory.id)
        assertEquals(5, latestHistory.ratingGiven)
        assertEquals("Excellent driving and clean car", latestHistory.reviewGiven)
    }

    @Test
    fun testRealTimeDriverTrackingCalculation() {
        // Verify geo distance calculations and navigation launch URL
        val originLat = 31.3333
        val originLng = 73.4333
        val destLat = 31.4187
        val destLng = 73.0791

        // Interpolated driver position at 50% along route
        val driverLat = originLat + (destLat - originLat) * 0.5
        val driverLng = originLng + (destLng - originLng) * 0.5

        assertTrue(com.example.util.PakistanGeoService.isWithinPakistan(driverLat, driverLng))
        val distanceRemaining = com.example.util.PakistanGeoService.calculateDistanceKm(
            driverLat, driverLng, destLat, destLng
        )
        assertTrue(distanceRemaining > 0)
    }

    @Test
    fun testRideHailingRequestScreenCalculations() {
        val pickup = "Committee Chowk, Jaranwala"
        val destination = "Jaranwala Railway Station"
        val dist = repository.estimateDistanceKm(pickup, destination)
        assertTrue(dist > 0.0)

        // Fare checks
        val bikeFare = repository.calculateFare(VehicleCategory.BIKE, dist)
        val rickshawFare = repository.calculateFare(VehicleCategory.RICKSHAW, dist)
        val carFare = repository.calculateFare(VehicleCategory.CAR, dist)
        assertTrue(bikeFare >= 70)
        assertTrue(rickshawFare > bikeFare)
        assertTrue(carFare > rickshawFare)

        // Promo discount applied to ride request
        val promoResult = repository.validatePromoCode("MAHI", rickshawFare)
        assertTrue(promoResult is com.example.data.PromoValidationResult.Success)
        val success = promoResult as com.example.data.PromoValidationResult.Success
        val finalFare = (rickshawFare - success.discountAmountPKR).coerceAtLeast(30)
        assertTrue(finalFare < rickshawFare)
    }

    @Test
    fun testPakistanPhoneValidator() {
        // 1. Valid Jazz number
        val jazz = com.example.util.PakistanPhoneValidator.validate("0300-1234567")
        assertTrue(jazz.isValid)
        assertEquals("+923001234567", jazz.normalizedE164)
        assertEquals("0300-1234567", jazz.localFormatted)
        assertTrue(jazz.operatorName.contains("Jazz"))

        // 2. Valid Zong number with +92 prefix
        val zong = com.example.util.PakistanPhoneValidator.validate("+92 312 9876543")
        assertTrue(zong.isValid)
        assertEquals("+923129876543", zong.normalizedE164)
        assertTrue(zong.operatorName.contains("Zong"))

        // 3. Valid Telenor number without leading 0
        val telenor = com.example.util.PakistanPhoneValidator.validate("3451122334")
        assertTrue(telenor.isValid)
        assertTrue(telenor.operatorName.contains("Telenor"))

        // 4. Invalid length (too short)
        val shortNum = com.example.util.PakistanPhoneValidator.validate("03001234")
        assertFalse(shortNum.isValid)
        assertNotNull(shortNum.errorMessage)

        // 5. Invalid prefix (not 03)
        val badPrefix = com.example.util.PakistanPhoneValidator.validate("04231234567")
        assertFalse(badPrefix.isValid)

        // 6. Empty input
        val emptyNum = com.example.util.PakistanPhoneValidator.validate("")
        assertFalse(emptyNum.isValid)
    }

    @Test
    fun testGuestSessionAndSecurityRestrictions() {
        val guest = repository.continueAsGuest(com.example.data.UserRole.PASSENGER)
        assertNotNull(guest)
        assertTrue(guest.isGuest)
        assertFalse(guest.isVerified)
        assertTrue(guest.uid.startsWith("GUEST-"))

        val current = repository.currentUser.value
        assertNotNull(current)
        assertTrue(current?.isGuest == true)
        assertFalse(current?.isVerified == true)
    }

    @Test
    fun testCaptainRegistrationAndRole() {
        val driverDetails = com.example.data.DriverRegDetails(
            cnic = "33102-1234567-1",
            licenseNo = "PB-JRW-5521",
            vehicleCategory = VehicleCategory.RICKSHAW,
            vehicleModel = "Sazgar 4-Stroke 2023",
            plateNo = "FS-23-4412"
        )
        val otp = repository.requestPhoneOtp("0300-9876543")
        val success = repository.verifyPhoneOtpAndLogin(
            phone = "0300-9876543",
            otp = otp,
            role = com.example.data.UserRole.DRIVER,
            fullName = "Captain Asif",
            driverDetails = driverDetails
        )
        assertTrue(success)
        val user = repository.currentUser.value
        assertNotNull(user)
        assertEquals(com.example.data.UserRole.DRIVER, user?.role)
        assertFalse(user?.isGuest == true)
        assertTrue(user?.isVerified == true)
        assertEquals("Captain Asif", user?.name)
        assertEquals("33102-1234567-1", user?.cnic)

        // Verify active driver updated
        assertEquals("Captain Asif", repository.activeDriver.value.name)
    }

    @Test
    fun testMergedNavigationTabsAreExactlyFour() {
        val tabs = MainTab.values()
        assertEquals(4, tabs.size)
        assertEquals(MainTab.PASSENGER, tabs[0])
        assertEquals(MainTab.DRIVER, tabs[1])
        assertEquals(MainTab.WEDDING, tabs[2])
        assertEquals(MainTab.ADMIN, tabs[3])

        // Verify titles match user requirements
        assertEquals("Rider", tabs[0].title)
        assertEquals("Captain", tabs[1].title)
        assertEquals("Wedding", tabs[2].title)
        assertEquals("Admin", tabs[3].title)
    }

    @Test
    fun testAdminPasscodeSetupAndAuthentication() {
        // 1. Initial state: verify passcode is not configured
        repository.setAdminPasscode("7860")
        assertTrue(repository.isPasscodeConfigured())

        // 2. Test invalid passcode
        val invalidResult = repository.verifyAdminPin("9999")
        assertTrue(invalidResult is AdminAuthResult.InvalidCredentials)
        assertEquals(2, (invalidResult as AdminAuthResult.InvalidCredentials).attemptsLeft)

        // 3. Test correct passcode
        val validResult = repository.verifyAdminPin("7860")
        assertTrue(validResult is AdminAuthResult.Success)
    }

    @Test
    fun testOwnerDashboardRickshawAndCaptainFleetManagement() {
        val allDrivers = repository.drivers.value
        assertTrue(allDrivers.isNotEmpty())

        val rickshaws = allDrivers.filter { it.vehicleCategory == VehicleCategory.RICKSHAW }
        assertTrue(rickshaws.isNotEmpty())

        val adminConfig = repository.adminConfig.value
        assertEquals(120, adminConfig.rickshawBaseFare)
        assertEquals(35, adminConfig.rickshawPerKm)

        // Test driver approval toggle
        val targetDriver = allDrivers.first()
        val originalApproval = targetDriver.isApproved
        repository.setDriverApproval(targetDriver.id, !originalApproval)
        val updatedDriver = repository.drivers.value.first { it.id == targetDriver.id }
        assertEquals(!originalApproval, updatedDriver.isApproved)
    }

    @Test
    fun testLoginRegistrationWithAvatarAndLogout() {
        val phone = "0312-3456789"
        val otp = repository.requestPhoneOtp(phone)
        val success = repository.verifyPhoneOtpAndLogin(
            phone = phone,
            otp = otp,
            role = com.example.data.UserRole.PASSENGER,
            fullName = "Zainab Bibi",
            photoUrl = "avatar_lady"
        )
        assertTrue(success)

        val user = repository.currentUser.value
        assertNotNull(user)
        assertEquals("Zainab Bibi", user?.name)
        assertEquals(phone, user?.phoneNumber)
        assertEquals("avatar_lady", user?.photoUrl)
        assertEquals(com.example.data.UserRole.PASSENGER, user?.role)

        // Test logout
        repository.signOut()
        assertNull(repository.currentUser.value)
    }

    @Test
    fun testOwnerAdminLogin() {
        repository.setAdminPasscode("7860")

        // 1. Invalid passcode attempt
        val failedResult = repository.loginAsAdmin("1111")
        assertTrue(failedResult is AdminAuthResult.InvalidCredentials)

        // 2. Passcode alone is NOT sufficient (Enforces Requirement: 2FA required)
        val passcodeOnlyResult = repository.loginAsAdmin("7860")
        assertTrue(passcodeOnlyResult is AdminAuthResult.RequiresSecondFactor)

        // 3. Valid 2FA authentication (Passcode + Biometric)
        val successResult = repository.loginAsAdmin("7860", biometricVerified = true)
        assertTrue(successResult is AdminAuthResult.Success)

        val adminUser = repository.currentUser.value
        assertNotNull(adminUser)
        assertEquals(UserRole.ADMIN, adminUser?.role)
        assertEquals(JaranwalaRepository.IMMUTABLE_OWNER_UID, adminUser?.uid)
        assertTrue(adminUser?.name?.contains("MAHI") == true)
        assertEquals("avatar_vip", adminUser?.photoUrl)
    }

    @Test
    fun testPasswordResetNeverGrantsAdminAccess() {
        repository.configureOwnerCredentials("7860", "TEST-OWNER-KEY-99")

        // Sign in as passenger
        repository.switchUserRole(UserRole.PASSENGER)
        val initialUser = repository.currentUser.value
        assertEquals(UserRole.PASSENGER, initialUser?.role)

        // Authorized Owner resets passcode
        val resetResult = repository.resetAdminPasscodeSecurely(
            ownerEmail = JaranwalaRepository.IMMUTABLE_OWNER_EMAIL,
            ownerMasterKey = "TEST-OWNER-KEY-99",
            newPasscode = "9988"
        )
        assertTrue(resetResult is PasswordResetResult.Success)

        // CRITICAL REQUIREMENT 1: Password reset MUST NEVER grant admin access
        // The current session MUST still be Passenger, never Admin!
        assertEquals(UserRole.PASSENGER, repository.currentUser.value?.role)

        // Old passcode fails
        val oldLogin = repository.authenticateOwnerAdmin("7860", biometricVerified = true)
        assertTrue(oldLogin is OwnerAdminLoginResult.InvalidCredentials)

        // New passcode requires 2FA to authenticate
        val newLoginRequires2FA = repository.authenticateOwnerAdmin("9988", biometricVerified = false)
        assertTrue(newLoginRequires2FA is OwnerAdminLoginResult.RequiresSecondFactor)

        // New passcode with 2FA succeeds
        val newLoginSuccess = repository.authenticateOwnerAdmin("9988", biometricVerified = true)
        assertTrue(newLoginSuccess is OwnerAdminLoginResult.Success)
        assertEquals(UserRole.ADMIN, repository.currentUser.value?.role)
    }

    @Test
    fun testUnauthorizedPasswordResetBlockedAndLogged() {
        repository.configureOwnerCredentials("7860", "TEST-OWNER-KEY-99")
        val initialLogsCount = repository.securityAuditLogs.value.size

        // Attempt 1: Unauthorized email
        val unauthorizedEmailResult = repository.resetAdminPasscodeSecurely(
            ownerEmail = "hacker@intruder.com",
            ownerMasterKey = "TEST-OWNER-KEY-99",
            newPasscode = "0000"
        )
        assertTrue(unauthorizedEmailResult is PasswordResetResult.RejectedUnauthorized)

        // Attempt 2: Valid email but wrong recovery key
        val wrongKeyResult = repository.resetAdminPasscodeSecurely(
            ownerEmail = JaranwalaRepository.IMMUTABLE_OWNER_EMAIL,
            ownerMasterKey = "WRONG-KEY",
            newPasscode = "0000"
        )
        assertTrue(wrongKeyResult is PasswordResetResult.InvalidMasterKey)

        // Attempt 3: Another wrong key triggers security lockout
        repository.resetAdminPasscodeSecurely(
            ownerEmail = JaranwalaRepository.IMMUTABLE_OWNER_EMAIL,
            ownerMasterKey = "WRONG-KEY-2",
            newPasscode = "0000"
        )
        val lockedOutResult = repository.resetAdminPasscodeSecurely(
            ownerEmail = JaranwalaRepository.IMMUTABLE_OWNER_EMAIL,
            ownerMasterKey = "WRONG-KEY-3",
            newPasscode = "0000"
        )
        assertTrue(lockedOutResult is PasswordResetResult.LockedOut)

        // Verify audit log captured suspicious attempts and lockout
        val currentLogs = repository.securityAuditLogs.value
        assertTrue(currentLogs.size > initialLogsCount)
        assertTrue(currentLogs.any { it.eventType == SecurityEventType.SUSPICIOUS_UNAUTHORIZED_RESET })
        assertTrue(currentLogs.any { it.eventType == SecurityEventType.SECURITY_LOCKOUT_TRIGGERED })

        // Verify owner alerts were populated
        assertTrue(repository.ownerSecurityAlerts.value.isNotEmpty())
    }

    @Test
    fun testPasswordAloneInsufficientRequires2FA() {
        repository.configureOwnerCredentials("7860", "TEST-OWNER-KEY-99")

        // Passcode alone returns RequiresSecondFactor
        val resultNo2FA = repository.authenticateOwnerAdmin("7860", biometricVerified = false, masterKey = null)
        assertTrue(resultNo2FA is OwnerAdminLoginResult.RequiresSecondFactor)

        // Passcode with valid Master Key succeeds
        val resultMasterKey = repository.authenticateOwnerAdmin(
            passcode = "7860",
            biometricVerified = false,
            masterKey = "TEST-OWNER-KEY-99"
        )
        assertTrue(resultMasterKey is OwnerAdminLoginResult.Success)

        // Passcode with invalid Master Key fails
        val resultInvalidKey = repository.authenticateOwnerAdmin(
            passcode = "7860",
            biometricVerified = false,
            masterKey = "INVALID-KEY-XYZ"
        )
        assertTrue(resultInvalidKey is OwnerAdminLoginResult.SecondFactorFailed)
    }

    @Test
    fun testNonOwnerCannotBeElevatedToAdmin() {
        // Direct switchUserRole(UserRole.ADMIN) is rejected
        repository.switchUserRole(UserRole.PASSENGER)
        repository.switchUserRole(UserRole.ADMIN)
        assertEquals(UserRole.PASSENGER, repository.currentUser.value?.role)

        // Public phone OTP login with ADMIN role is sanitized to PASSENGER
        val generatedOtp = repository.requestPhoneOtp("03001234567")
        val verified = repository.verifyPhoneOtpAndLogin(
            phone = "03001234567",
            otp = generatedOtp,
            role = UserRole.ADMIN,
            fullName = "Attacker"
        )
        assertTrue(verified)
        assertEquals(UserRole.PASSENGER, repository.currentUser.value?.role)

        // Google Sign-In with ADMIN role is sanitized to PASSENGER
        repository.loginWithGoogle(
            email = "someuser@gmail.com",
            displayName = "Attacker",
            role = UserRole.ADMIN
        )
        assertEquals(UserRole.PASSENGER, repository.currentUser.value?.role)

        // Audit log records blocked elevation attempts
        assertTrue(repository.securityAuditLogs.value.any { it.eventType == SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT })
    }

    @Test
    fun testBookingHomeScreenFourServicesAndSearch() {
        // Verify all 4 vehicle categories / services operate correctly
        val dist = 3.5
        val fareBike = repository.calculateFare(VehicleCategory.BIKE, dist)
        val fareRickshaw = repository.calculateFare(VehicleCategory.RICKSHAW, dist)
        val fareCar = repository.calculateFare(VehicleCategory.CAR, dist)

        assertTrue(fareBike in 70..300)
        assertTrue(fareRickshaw in 120..400)
        assertTrue(fareCar in 250..800)

        // Verify distance estimation for surrounding areas
        val distBuchiana = repository.estimateDistanceKm("Committee Chowk", "Buchiana")
        assertTrue(distBuchiana > 0)
        val distLahore = repository.estimateDistanceKm("Committee Chowk", "Lahore")
        assertTrue(distLahore > 0.0)
    }

    @Test
    fun testRideHistoryFilterSearchAndPaymentStatus() {
        // 1. Create a completed ride
        repository.requestRide(
            passengerName = "Ahmed Raza",
            passengerPhone = "0301-8765432",
            pickup = "Committee Chowk",
            destination = "DHQ Hospital Jaranwala",
            category = VehicleCategory.BIKE
        )
        repository.startTrip()
        repository.completeTrip()

        // 2. Create a cancelled ride
        repository.requestRide(
            passengerName = "Ahmed Raza",
            passengerPhone = "0301-8765432",
            pickup = "Cinema Chowk",
            destination = "Buchiana Road",
            category = VehicleCategory.RICKSHAW
        )
        repository.cancelRide()

        val history = repository.passengerHistory.value
        val completedRides = history.filter { it.status == RideStatus.COMPLETED }
        val cancelledRides = history.filter { it.status == RideStatus.CANCELLED }

        assertTrue(completedRides.isNotEmpty())
        assertTrue(cancelledRides.isNotEmpty())

        // 3. Search history
        val searchBuchiana = history.filter {
            it.pickup.contains("Buchiana", ignoreCase = true) || it.destination.contains("Buchiana", ignoreCase = true)
        }
        assertTrue(searchBuchiana.isNotEmpty())
    }

    @Test
    fun testDashboardRidesSegregation() {
        // Create an active ride
        repository.requestRide(
            passengerName = "Zainab Bibi",
            passengerPhone = "0302-9988776",
            pickup = "Cinema Chowk Jaranwala",
            destination = "Awami Colony",
            category = VehicleCategory.RICKSHAW
        )
        val activeRide = repository.currentPassengerRide.value
        assertNotNull(activeRide)

        // Request dashboard rides with latch for coroutine completion
        val latch = java.util.concurrent.CountDownLatch(1)
        var receivedActive = false
        var receivedPast = false
        repository.fetchAllDashboardRidesFromFirestore { activeList, pastList ->
            receivedActive = true
            receivedPast = true
            // Active list should contain active ride
            assertTrue(activeList.any { it.passengerPhone == "0302-9988776" || it.id == activeRide?.id })
            // All items in activeList must have active statuses
            assertTrue(activeList.all {
                it.status == RideStatus.SEARCHING ||
                it.status == RideStatus.ACCEPTED ||
                it.status == RideStatus.DRIVER_ARRIVED ||
                it.status == RideStatus.IN_PROGRESS
            })
            // All items in pastList must have past statuses
            assertTrue(pastList.all {
                it.status == RideStatus.COMPLETED ||
                it.status == RideStatus.CANCELLED ||
                it.status == RideStatus.IDLE
            })
            latch.countDown()
        }
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(receivedActive)
        assertTrue(receivedPast)
    }

    @Test
    fun testTripSummaryDetailsAndCompletion() {
        // 1. Request ride
        repository.requestRide(
            passengerName = "Usman Ghani",
            passengerPhone = "0300-9876543",
            pickup = "Committee Chowk, Jaranwala",
            destination = "DHQ Hospital Jaranwala",
            category = VehicleCategory.CAR
        )

        val ride = repository.currentPassengerRide.value
        assertNotNull(ride)
        assertEquals("Committee Chowk, Jaranwala", ride?.pickup)
        assertEquals("DHQ Hospital Jaranwala", ride?.destination)
        assertEquals(VehicleCategory.CAR, ride?.vehicleCategory)
        assertTrue((ride?.estimatedDistanceKm ?: 0.0) > 0.0)
        assertTrue((ride?.estimatedFarePKR ?: 0) >= 150)

        // 2. Start and Complete Trip
        repository.startTrip()
        assertEquals(RideStatus.IN_PROGRESS, repository.currentPassengerRide.value?.status)

        repository.completeTrip()
        val completedRide = repository.currentPassengerRide.value
        assertNotNull(completedRide)
        assertEquals(RideStatus.COMPLETED, completedRide?.status)

        // Verify summary fields
        assertNotNull(completedRide?.driverName)
        assertNotNull(completedRide?.vehiclePlate)
        assertTrue((completedRide?.estimatedFarePKR ?: 0) > 0)
        assertTrue((completedRide?.estimatedDistanceKm ?: 0.0) > 0.0)

        // 3. Rate and submit feedback from Trip Summary
        repository.submitDriverRating(
            rideId = completedRide!!.id,
            driverId = completedRide.driverId,
            stars = 5,
            review = "Punctual, polite captain and smooth AC drive in Jaranwala"
        )

        // After rating, the active ride is archived/cleared from currentPassengerRide
        assertNull(repository.currentPassengerRide.value)
        val historyItem = repository.passengerHistory.value.find { it.id == completedRide.id }
        assertNotNull(historyItem)
        assertEquals(5, historyItem?.ratingGiven)
        assertEquals("Punctual, polite captain and smooth AC drive in Jaranwala", historyItem?.reviewGiven)
    }

    @Test
    fun testUserRoleSecurityManagerAdminLock() {
        val ownerContext = UserAuthContext(
            uid = JaranwalaRepository.IMMUTABLE_OWNER_UID,
            phoneNumber = JaranwalaRepository.IMMUTABLE_OWNER_PHONE,
            name = JaranwalaRepository.IMMUTABLE_OWNER_NAME,
            role = UserRole.ADMIN,
            email = JaranwalaRepository.IMMUTABLE_OWNER_EMAIL,
            isMasterKeyVerified = true
        )
        val ownerResult = UserRoleSecurityManager.validateAdminAccess(ownerContext)
        assertTrue(ownerResult is OperationPermissionResult.Granted)

        val attackerContext = UserAuthContext(
            uid = "ATTACKER-999",
            phoneNumber = "0300-9999999",
            name = "Malicious Actor",
            role = UserRole.ADMIN,
            email = "attacker@gmail.com"
        )
        val attackerResult = UserRoleSecurityManager.validateAdminAccess(attackerContext)
        assertTrue(attackerResult is OperationPermissionResult.Denied)
        val denied = attackerResult as OperationPermissionResult.Denied
        assertEquals(SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT, denied.violationType)
    }

    @Test
    fun testUserRoleSecurityManagerPassengerIsolation() {
        val passengerContext = UserAuthContext(
            uid = "PASSENGER-101",
            phoneNumber = "0300-1111111",
            name = "Test Passenger",
            role = UserRole.PASSENGER
        )

        // Valid: modify own profile
        val ownResult = UserRoleSecurityManager.validateUserProfileWrite(
            authContext = passengerContext,
            targetUserId = "PASSENGER-101",
            targetRole = UserRole.PASSENGER
        )
        assertTrue(ownResult is OperationPermissionResult.Granted)

        // Denied: modify other user's profile
        val crossResult = UserRoleSecurityManager.validateUserProfileWrite(
            authContext = passengerContext,
            targetUserId = "PASSENGER-202",
            targetRole = UserRole.PASSENGER
        )
        assertTrue(crossResult is OperationPermissionResult.Denied)

        // Denied: self-elevation to ADMIN
        val elevationResult = UserRoleSecurityManager.validateUserProfileWrite(
            authContext = passengerContext,
            targetUserId = "PASSENGER-101",
            targetRole = UserRole.ADMIN
        )
        assertTrue(elevationResult is OperationPermissionResult.Denied)
        assertEquals(SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT, (elevationResult as OperationPermissionResult.Denied).violationType)
    }

    @Test
    fun testUserRoleSecurityManagerDriverSelfApprovalProtection() {
        val driverContext = UserAuthContext(
            uid = "DRIVER-501",
            phoneNumber = "0302-3333333",
            name = "Captain Saleem",
            role = UserRole.DRIVER,
            isApprovedDriver = false
        )

        // Denied: self-approval on creation
        val selfApprovalResult = UserRoleSecurityManager.validateDriverProfileWrite(
            authContext = driverContext,
            targetDriverId = "DRIVER-501",
            isCreatingProfile = true,
            requestedApproval = true,
            modifiedFields = setOf("isApproved")
        )
        assertTrue(selfApprovalResult is OperationPermissionResult.Denied)

        // Granted: standard creation with isApproved = false
        val validCreateResult = UserRoleSecurityManager.validateDriverProfileWrite(
            authContext = driverContext,
            targetDriverId = "DRIVER-501",
            isCreatingProfile = true,
            requestedApproval = false,
            modifiedFields = setOf("name", "phone", "vehicleType")
        )
        assertTrue(validCreateResult is OperationPermissionResult.Granted)

        // Denied: driver attempting to alter earnings or rating
        val tamperEarningsResult = UserRoleSecurityManager.validateDriverProfileWrite(
            authContext = driverContext,
            targetDriverId = "DRIVER-501",
            isCreatingProfile = false,
            requestedApproval = false,
            modifiedFields = setOf("earningsPKR", "rating")
        )
        assertTrue(tamperEarningsResult is OperationPermissionResult.Denied)
    }

    @Test
    fun testPakistanPhoneValidatorE164Normalization() {
        // Jazz
        val jazz = PakistanPhoneValidator.validate("0300-7860123")
        assertTrue(jazz.isValid)
        assertEquals("+923007860123", jazz.normalizedE164)
        assertEquals("0300-7860123", jazz.localFormatted)
        assertEquals("Jazz / Mobilink", jazz.operatorName)

        // Zong
        val zong = PakistanPhoneValidator.validate("+923121234567")
        assertTrue(zong.isValid)
        assertEquals("+923121234567", zong.normalizedE164)
        assertEquals("Zong 4G", zong.operatorName)

        // Telenor
        val telenor = PakistanPhoneValidator.validate("03459876543")
        assertTrue(telenor.isValid)
        assertEquals("+923459876543", telenor.normalizedE164)
        assertEquals("Telenor 4G", telenor.operatorName)

        // Ufone
        val ufone = PakistanPhoneValidator.validate("00923335555555")
        assertTrue(ufone.isValid)
        assertEquals("+923335555555", ufone.normalizedE164)
        assertEquals("Ufone 4G", ufone.operatorName)

        // Invalid
        val invalid = PakistanPhoneValidator.validate("0423123456")
        assertFalse(invalid.isValid)
    }

    @Test
    fun testGoogleMapsJaranwalaCoordinates() {
        val jaranwalaLat = com.example.util.PakistanGeoService.JARANWALA_LAT
        val jaranwalaLng = com.example.util.PakistanGeoService.JARANWALA_LNG

        assertEquals(31.3333, jaranwalaLat, 0.001)
        assertEquals(73.4333, jaranwalaLng, 0.001)
        assertTrue(com.example.util.PakistanGeoService.isWithinPakistan(jaranwalaLat, jaranwalaLng))
    }

    @Test
    fun testGoogleMapsLandmarkResolution() {
        // Find closest to Committee Chowk Jaranwala coordinates
        val closest = com.example.util.PakistanGeoService.findClosestLocation(31.3342, 73.4194)
        assertEquals("Committee Chowk, Jaranwala", closest.name)
        assertEquals("کمیٹی چوک، جرانوالہ", closest.urduName)

        // Find closest to Railway Station
        val railway = com.example.util.PakistanGeoService.findClosestLocation(31.3381, 73.4278)
        assertEquals("Jaranwala Railway Station", railway.name)
    }

    @Test
    fun testOpenStreetMapDistanceAndEta() {
        val dist = com.example.util.PakistanGeoService.calculateDistanceKm(
            31.3342, 73.4194, // Committee Chowk
            31.3381, 73.4278  // Railway Station
        )
        assertTrue(dist in 0.5..2.5)

        // Ensure search returns results for Jaranwala
        val searchResults = com.example.util.PakistanGeoService.searchPakistan("Chowk")
        assertTrue(searchResults.isNotEmpty())
        assertTrue(searchResults.any { it.name.contains("Chowk") })
    }

    @Test
    fun testOpenStreetMapVillageCoverageAndSyedwalaCorridor() {
        val locations = com.example.util.PakistanGeoService.PAKISTAN_LOCATIONS

        // 1. Syedwala (Siyadwala) and Ravi river corridor villages
        val syedwala = locations.find { it.name.contains("Syedwala", ignoreCase = true) }
        assertNotNull("Syedwala must be present in map dataset", syedwala)
        assertEquals("سیدوالا (سیادوالا)، جرانوالہ", syedwala?.urduName)

        val mandiSyedwala = locations.find { it.name.contains("Mandi Syedwala", ignoreCase = true) }
        assertNotNull("Mandi Syedwala must be present", mandiSyedwala)

        val raviBridge = locations.find { it.name.contains("Ravi River Bridge", ignoreCase = true) }
        assertNotNull("Ravi River Bridge must be present", raviBridge)

        val jhokDitta = locations.find { it.name.contains("Jhok Ditta", ignoreCase = true) }
        assertNotNull("Jhok Ditta village must be present", jhokDitta)

        val mariPattan = locations.find { it.name.contains("Mari Pattan", ignoreCase = true) }
        assertNotNull("Mari Pattan village must be present", mariPattan)

        val wasavewala = locations.find { it.name.contains("Wasavewala", ignoreCase = true) }
        assertNotNull("Wasavewala village must be present", wasavewala)

        val headGugera = locations.find { it.name.contains("Head Gugera", ignoreCase = true) }
        assertNotNull("Head Gugera must be present", headGugera)

        // 2. Rural Chaks in Jaranwala
        val chak58 = locations.find { it.name.contains("Chak 58 GB", ignoreCase = true) }
        assertNotNull("Chak 58 GB must be present", chak58)

        val chak115 = locations.find { it.name.contains("Chak 115 GB", ignoreCase = true) }
        assertNotNull("Chak 115 GB must be present", chak115)

        val chak240 = locations.find { it.name.contains("Chak 240 GB", ignoreCase = true) }
        assertNotNull("Chak 240 GB must be present", chak240)

        // 3. Towns in Jaranwala sub-division
        val buchiana = locations.find { it.name.contains("Buchiana", ignoreCase = true) }
        assertNotNull("Buchiana must be present", buchiana)

        val lundianwala = locations.find { it.name.contains("Lundianwala", ignoreCase = true) }
        assertNotNull("Lundianwala must be present", lundianwala)

        val awagat = locations.find { it.name.contains("Awagat", ignoreCase = true) }
        assertNotNull("Awagat must be present", awagat)

        val satyana = locations.find { it.name.contains("Satyana", ignoreCase = true) }
        assertNotNull("Satyana must be present", satyana)
    }

    @Test
    fun testOpenStreetMapManualPinPlacementForMissingVillages() {
        // Passenger manually drops a pin on an unlisted village in rural Jaranwala
        val customVillageLat = 31.2954
        val customVillageLng = 73.5412
        val customName = "چک 128 جی بی ڈیرہ ملک اسلم"

        val pinnedLocation = com.example.util.PakistanGeoService.createCustomPinnedLocation(
            lat = customVillageLat,
            lng = customVillageLng,
            customName = customName
        )

        assertNotNull(pinnedLocation)
        assertEquals(customName, pinnedLocation.name)
        assertEquals(customName, pinnedLocation.urduName)
        assertEquals(customVillageLat, pinnedLocation.latitude, 0.0001)
        assertEquals(customVillageLng, pinnedLocation.longitude, 0.0001)
        assertEquals("Custom Pin / Village", pinnedLocation.category)
        assertTrue(pinnedLocation.description.contains("Manually selected pin"))
    }

    @Test
    fun testOpenStreetMapNationwideSearchCoverage() {
        // Search by chak
        val chakResults = com.example.util.PakistanGeoService.searchPakistan("Chak 11")
        assertTrue(chakResults.isNotEmpty())
        assertTrue(chakResults.any { it.name.contains("115 GB") || it.name.contains("116 GB") })

        // Search by village
        val syedwalaResults = com.example.util.PakistanGeoService.searchPakistan("Syedwala")
        assertTrue(syedwalaResults.isNotEmpty())

        // Search major Pakistan cities
        val lahore = com.example.util.PakistanGeoService.searchPakistan("Lahore")
        assertTrue(lahore.isNotEmpty())

        val karachi = com.example.util.PakistanGeoService.searchPakistan("Karachi")
        assertTrue(karachi.isNotEmpty())

        val quetta = com.example.util.PakistanGeoService.searchPakistan("Quetta")
        assertTrue(quetta.isNotEmpty())

        val peshawar = com.example.util.PakistanGeoService.searchPakistan("Peshawar")
        assertTrue(peshawar.isNotEmpty())

        val gilgit = com.example.util.PakistanGeoService.searchPakistan("Gilgit")
        assertTrue(gilgit.isNotEmpty())
    }

    @Test
    fun testRidePricingCalculatorBreakdownAndQuotes() {
        val config = com.example.data.AdminConfig(
            bikeBaseFare = 70,
            bikePerKm = 25,
            rickshawBaseFare = 120,
            rickshawPerKm = 35,
            carBaseFare = 220,
            carPerKm = 60
        )

        // 1. Exact calculation for 6.0 km Rickshaw with API duration & promo
        val breakdown = com.example.pricing.RidePricingCalculator.calculateFareBreakdown(
            category = VehicleCategory.RICKSHAW,
            distanceKm = 6.0,
            adminConfig = config,
            apiDurationMinutes = 14,
            promoCode = "FIRST",
            promoDiscountPKR = 50,
            distanceSource = com.example.pricing.DistanceSource.LOCATION_API_OSRM
        )

        assertEquals(120, breakdown.baseFarePKR)
        assertEquals(35, breakdown.perKmRatePKR)
        assertEquals(210, breakdown.distanceChargePKR) // 6.0 * 35 = 210
        assertEquals(330, breakdown.roundedSubtotalPKR) // 120 + 210 = 330
        assertEquals(50, breakdown.promoDiscountPKR)
        assertEquals(280, breakdown.finalEstimatedFarePKR) // 330 - 50 = 280
        assertEquals(com.example.pricing.DistanceSource.LOCATION_API_OSRM, breakdown.distanceSource)
        assertTrue(breakdown.estimatedDurationMinutes >= 14)

        // 2. All category quotes for comparison
        val quotes = com.example.pricing.RidePricingCalculator.calculateAllCategoryQuotes(
            distanceKm = 10.0,
            adminConfig = config,
            distanceSource = com.example.pricing.DistanceSource.LOCATION_API_OSRM
        )
        assertEquals(3, quotes.size)
        val bikeQuote = quotes[VehicleCategory.BIKE]!!.finalEstimatedFarePKR
        val rickshawQuote = quotes[VehicleCategory.RICKSHAW]!!.finalEstimatedFarePKR
        val carQuote = quotes[VehicleCategory.CAR]!!.finalEstimatedFarePKR
        assertEquals(320, bikeQuote) // 70 + 250 = 320
        assertEquals(470, rickshawQuote) // 120 + 350 = 470
        assertEquals(820, carQuote) // 220 + 600 = 820
        assertTrue(carQuote > rickshawQuote && rickshawQuote > bikeQuote)
    }

    @Test
    fun testLocationDistanceServiceWithLiveApiProviderAndFallback() = kotlinx.coroutines.test.runTest {
        // Fake Location API provider returning exact road distance (22,400m = 22.4 km, 28 mins)
        var apiCalls = 0
        val fakeProvider = object : com.example.pricing.LocationDistanceProvider {
            override suspend fun fetchDrivingRouteMetersAndSeconds(
                pickupLat: Double,
                pickupLng: Double,
                destLat: Double,
                destLng: Double
            ): Pair<Double, Double> {
                apiCalls++
                return 22400.0 to 1680.0
            }

            override suspend fun geocodePakistanAddress(address: String): Pair<Double, Double>? {
                return if (address.contains("Custom Village", true)) 31.3100 to 73.5100 else null
            }
        }

        val service = com.example.pricing.LocationDistanceService(fakeProvider)

        // 1. Known Jaranwala -> Syedwala route fetched from Location API
        val syedwalaResult = service.fetchRideDistance("Committee Chowk, Jaranwala", "Syedwala, Jaranwala")
        assertEquals(22.4, syedwalaResult.distanceKm, 0.01)
        assertEquals(28, syedwalaResult.estimatedDurationMinutes)
        assertEquals(com.example.pricing.DistanceSource.LOCATION_API_OSRM, syedwalaResult.source)
        assertEquals(1, apiCalls)

        // 2. Subsequent identical query uses cache (no duplicate network hit)
        val cachedResult = service.fetchRideDistance("Committee Chowk, Jaranwala", "Syedwala, Jaranwala")
        assertEquals(22.4, cachedResult.distanceKm, 0.01)
        assertEquals(1, apiCalls)

        // 3. Pricing calculation directly from Location API result
        val fareFromApi = com.example.pricing.RidePricingCalculator.calculateFromLocationDistance(
            distanceResult = syedwalaResult,
            category = VehicleCategory.CAR
        )
        // Car: 220 + (22.4 * 60 = 1344) = 1564 -> rounded up to nearest 10 = 1570
        assertEquals(1570, fareFromApi.finalEstimatedFarePKR)

        // 4. Offline / error provider triggers deterministic Coordinate Haversine fallback
        val failingProvider = object : com.example.pricing.LocationDistanceProvider {
            override suspend fun fetchDrivingRouteMetersAndSeconds(
                pickupLat: Double,
                pickupLng: Double,
                destLat: Double,
                destLng: Double
            ): Pair<Double, Double>? = null

            override suspend fun geocodePakistanAddress(address: String): Pair<Double, Double>? = null
        }
        val offlineService = com.example.pricing.LocationDistanceService(failingProvider)
        val fallbackResult = offlineService.fetchRideDistance("Committee Chowk, Jaranwala", "Syedwala, Jaranwala")
        assertEquals(com.example.pricing.DistanceSource.COORDINATE_HAVERSINE, fallbackResult.source)
        assertTrue(fallbackResult.distanceKm > 15.0)
    }

    @Test
    fun testHardcoded123456OtpBypassIsRejected() {
        repository.signOut()
        // Without matching generated OTP, 123456 must fail
        val bypassResult = repository.verifyPhoneOtpAndLogin(
            phone = "0300-9876543",
            otp = "123456",
            role = UserRole.PASSENGER,
            fullName = "Test User"
        )
        assertFalse(bypassResult)
        assertNull(repository.currentUser.value)

        // Even after requesting OTP, unless random code happened to be 123456, wrong code fails
        val realOtp = repository.requestPhoneOtp("0300-9876543")
        val wrongOtp = if (realOtp == "123456") "654321" else "123456"
        assertFalse(
            repository.verifyPhoneOtpAndLogin(
                phone = "0300-9876543",
                otp = wrongOtp,
                role = UserRole.PASSENGER,
                fullName = "Test User"
            )
        )
        assertTrue(
            repository.verifyPhoneOtpAndLogin(
                phone = "0300-9876543",
                otp = realOtp,
                role = UserRole.PASSENGER,
                fullName = "Test User"
            )
        )
        assertEquals(UserRole.PASSENGER, repository.currentUser.value?.role)
    }

    @Test
    fun testUnapprovedDriverBlockedFromGoingOnline() {
        val otp = repository.requestPhoneOtp("0321-5544332")
        val loggedIn = repository.verifyPhoneOtpAndLogin(
            phone = "0321-5544332",
            otp = otp,
            role = UserRole.DRIVER,
            fullName = "Captain Bilal",
            driverDetails = DriverRegDetails(
                cnic = "33102-1234567-1",
                licenseNo = "PB-JRW-9911",
                vehicleCategory = VehicleCategory.RICKSHAW,
                vehicleModel = "Sazgar 2024",
                plateNo = "FS-24-8821"
            )
        )
        assertTrue(loggedIn)
        assertFalse(repository.activeDriver.value.isApproved)
        assertFalse(repository.activeDriver.value.isOnline)

        // Attempting to go online while unapproved must be blocked
        repository.setDriverOnline(true)
        assertFalse(repository.activeDriver.value.isOnline)

        // After Admin approves, driver can toggle online
        repository.setDriverApproval(repository.activeDriver.value.id, true)
        assertTrue(repository.activeDriver.value.isApproved)
        repository.setDriverOnline(true)
        assertTrue(repository.activeDriver.value.isOnline)
    }

    @Test
    fun testDriverIncomingRequestsQueueAcceptDeclineAndOfflineClear() {
        repository.setDriverOnline(true)
        val cat = repository.activeDriver.value.vehicleCategory
        repository.requestRide(
            passengerName = "Rider One",
            passengerPhone = "0301-1111111",
            pickup = "Committee Chowk",
            destination = "Railway Station",
            category = cat
        )
        val firstId = repository.incomingDriverRequest.value?.id
        assertNotNull(firstId)
        assertTrue(repository.incomingDriverRequests.value.any { it.id == firstId })

        repository.rejectDriverRequest(firstId)
        assertFalse(repository.incomingDriverRequests.value.any { it.id == firstId })

        repository.requestRide(
            passengerName = "Rider Two",
            passengerPhone = "0302-2222222",
            pickup = "Cinema Chowk",
            destination = "Bus Stand",
            category = cat
        )
        val secondId = repository.incomingDriverRequests.value.firstOrNull()?.id
        assertNotNull(secondId)

        // Accepting moves it out of queue into currentDriverRide
        repository.acceptDriverRequest(secondId)
        assertEquals(secondId, repository.currentDriverRide.value?.id)
        assertEquals(RideStatus.ACCEPTED, repository.currentDriverRide.value?.status)
        assertFalse(repository.incomingDriverRequests.value.any { it.id == secondId })

        // Going offline clears queue
        repository.setDriverOnline(false)
        assertTrue(repository.incomingDriverRequests.value.isEmpty())
    }

    @Test
    fun testRideHistoryUiStateRequiresNonGuestAuthenticatedUser() {
        repository.signOut()
        repository.fetchRideHistoryFromFirestore("")
        assertTrue(repository.rideHistoryUiState.value is RideHistoryUiState.Unauthenticated)

        repository.continueAsGuest(UserRole.PASSENGER)
        repository.fetchRideHistoryFromFirestore("")
        assertTrue(repository.rideHistoryUiState.value is RideHistoryUiState.Unauthenticated)
    }

    @Test
    fun testUnconfiguredOwnerAdminRejectsAllLegacyDefaults() {
        repository.resetForTesting()
        assertFalse(repository.isPasscodeConfigured())

        val pin7860 = repository.verifyAdminPin("7860")
        assertTrue(pin7860 is AdminAuthResult.InvalidCredentials)

        repository.resetForTesting()
        val pin1234 = repository.verifyAdminPin("1234")
        assertTrue(pin1234 is AdminAuthResult.InvalidCredentials)

        repository.resetForTesting()
        val ownerAuth = repository.authenticateOwnerAdmin("7860", biometricVerified = true)
        assertTrue(ownerAuth is OwnerAdminLoginResult.InvalidCredentials)
    }

    @Test
    fun testRequestRideRemainsSearchingUntilDriverAccepts() {
        repository.requestRide(
            passengerName = "Ahmed Raza",
            passengerPhone = "0301-8765432",
            pickup = "Committee Chowk",
            destination = "Railway Station Jaranwala",
            category = VehicleCategory.RICKSHAW
        )
        val ride = repository.currentPassengerRide.value
        assertNotNull(ride)
        assertEquals(RideStatus.SEARCHING, ride?.status)
        assertNull(ride?.driverId)
        assertEquals("CASH", ride?.paymentMethod)
        assertEquals("PENDING", ride?.paymentStatus)
    }

    @Test
    fun testPaymentGatewayRouterCashJazzCashAndEasypaisaStubs() = kotlinx.coroutines.runBlocking {
        // 1. Cash settlement
        val cashRes = repository.settleRideFare(
            rideId = "JRW-PAY-101",
            amountPKR = 250,
            method = PaymentMethod.CASH,
            walletAccount = ""
        )
        assertTrue(cashRes is com.example.payment.PaymentSettlementResult.Success)
        val cashSuccess = cashRes as com.example.payment.PaymentSettlementResult.Success
        assertEquals("COLLECTED_CASH", cashSuccess.paymentStatus)
        assertFalse(cashSuccess.isStubGateway)

        // 2. JazzCash Stub valid mobile account
        val jcRes = repository.settleRideFare(
            rideId = "JRW-PAY-102",
            amountPKR = 420,
            method = PaymentMethod.JAZZCASH,
            walletAccount = "0301-1234567"
        )
        assertTrue(jcRes is com.example.payment.PaymentSettlementResult.Success)
        val jcSuccess = jcRes as com.example.payment.PaymentSettlementResult.Success
        assertEquals("PAID", jcSuccess.paymentStatus)
        assertTrue(jcSuccess.isStubGateway)
        assertTrue(jcSuccess.transactionRef.startsWith("JC-STUB-"))

        // 3. Easypaisa Stub valid mobile account
        val epRes = repository.settleRideFare(
            rideId = "JRW-PAY-103",
            amountPKR = 310,
            method = PaymentMethod.EASYPAISA,
            walletAccount = "0345-9876543"
        )
        assertTrue(epRes is com.example.payment.PaymentSettlementResult.Success)
        val epSuccess = epRes as com.example.payment.PaymentSettlementResult.Success
        assertEquals("PAID", epSuccess.paymentStatus)
        assertTrue(epSuccess.isStubGateway)
        assertTrue(epSuccess.transactionRef.startsWith("EP-STUB-"))

        // 4. Invalid mobile wallet account rejected cleanly
        val invalidRes = repository.settleRideFare(
            rideId = "JRW-PAY-104",
            amountPKR = 200,
            method = PaymentMethod.JAZZCASH,
            walletAccount = "123"
        )
        assertTrue(invalidRes is com.example.payment.PaymentSettlementResult.ValidationError)
    }

    @Test
    fun testSyncFromFcmPayloadRideRequestsAndTripStatusUpdates() {
        // 1. Captain receives incoming ride request via FCM data payload
        repository.syncFromFcmPayload(
            mapOf(
                "type" to "NEW_RIDE_REQUEST",
                "rideId" to "JRW-FCM-88",
                "passengerId" to "PASS-1",
                "passengerName" to "Bilal Tariq",
                "passengerPhone" to "0301-1112233",
                "pickup" to "Committee Chowk",
                "destination" to "Railway Station Jaranwala",
                "vehicleCategory" to "CAR",
                "farePKR" to "390",
                "distanceKm" to "3.4"
            )
        )
        assertTrue(repository.incomingDriverRequests.value.any { it.id == "JRW-FCM-88" && it.estimatedFarePKR == 390 })

        // 2. Passenger receives TRIP_STARTED status + live driver coordinates via FCM
        repository.requestRide(
            passengerName = "Ahmed Raza",
            passengerPhone = "0301-8765432",
            pickup = "Committee Chowk",
            destination = "Railway Station Jaranwala",
            category = VehicleCategory.RICKSHAW
        )
        val activeId = repository.currentPassengerRide.value!!.id
        repository.syncFromFcmPayload(
            mapOf(
                "type" to "TRIP_STARTED",
                "rideId" to activeId,
                "driverName" to "Captain Tariq",
                "vehiclePlate" to "FSD-7788",
                "driverLat" to "31.3401",
                "driverLng" to "73.4265"
            )
        )
        val updated = repository.currentPassengerRide.value
        assertNotNull(updated)
        assertEquals(RideStatus.IN_PROGRESS, updated!!.status)
        assertEquals("Captain Tariq", updated.driverName)
        assertEquals(31.3401, updated.driverLat ?: 0.0, 0.0001)
        assertEquals(73.4265, updated.driverLng ?: 0.0, 0.0001)
    }

    @Test
    fun testBuildCurvedRoutePointsDynamicStartCoordinates() {
        val pts = com.example.ui.components.buildCurvedRoutePoints(
            startLat = 31.3401,
            startLng = 73.4265,
            endLat = 31.3452,
            endLng = 73.4189
        )
        assertEquals(9, pts.size)
        assertEquals(31.3401, pts.first().latitude, 0.0001)
        assertEquals(73.4265, pts.first().longitude, 0.0001)
        assertEquals(31.3452, pts.last().latitude, 0.0001)
        assertEquals(73.4189, pts.last().longitude, 0.0001)
    }

    @Test
    fun testPakistanPhoneValidatorSupportsAllPakistanOperatorsAndFormats() {
        val validNumbers = listOf(
            "03001234567" to "+923001234567",
            "0319-5544332" to "+923195544332",
            "+92 328 9876543" to "+923289876543",
            "0092 339 1122334" to "+923391122334",
            "+92 0345 7788990" to "+923457788990",
            "0355-4455667" to "+923554455667",
            "03701122334" to "+923701122334"
        )
        validNumbers.forEach { (raw, expectedE164) ->
            val res = PakistanPhoneValidator.validate(raw)
            assertTrue("Expected $raw to be valid, got error: ${res.errorMessage}", res.isValid)
            assertEquals(expectedE164, res.normalizedE164)
        }
    }

    @Test
    fun testCancelSpecificRideByIdUpdatesHistoryAndActiveState() {
        repository.requestRide(
            passengerName = "Tariq Mehmood",
            passengerPhone = "0300-1112233",
            pickup = "Committee Chowk",
            destination = "Buchiana",
            category = VehicleCategory.BIKE
        )
        val firstRideId = repository.currentPassengerRide.value!!.id

        repository.requestRide(
            passengerName = "Tariq Mehmood",
            passengerPhone = "0300-1112233",
            pickup = "Cinema Chowk",
            destination = "Syedwala",
            category = VehicleCategory.CAR
        )
        val secondRideId = repository.currentPassengerRide.value!!.id

        // Cancel only the first ride via specific rideId (e.g., from RidesDashboardScreen)
        repository.cancelRide(firstRideId)

        val cancelledFirst = repository.passengerHistory.value.find { it.id == firstRideId }
        assertEquals(RideStatus.CANCELLED, cancelledFirst?.status)
        // Active second ride remains intact
        assertEquals(secondRideId, repository.currentPassengerRide.value?.id)
    }

    @Test
    fun testNarowalSpellingAndOsrmPolylineProvider() = kotlinx.coroutines.test.runTest {
        val narowal = com.example.util.PakistanGeoService.PAKISTAN_LOCATIONS.find { it.name == "Narowal" }
        assertNotNull(narowal)
        assertEquals("نارووال", narowal?.urduName)

        var polylineCalls = 0
        val fakeProvider = object : com.example.pricing.LocationDistanceProvider {
            override suspend fun fetchDrivingRouteMetersAndSeconds(
                pickupLat: Double,
                pickupLng: Double,
                destLat: Double,
                destLng: Double
            ): Pair<Double, Double>? = null

            override suspend fun geocodePakistanAddress(address: String): Pair<Double, Double>? = null

            override suspend fun fetchDrivingRoutePolyline(
                pickupLat: Double,
                pickupLng: Double,
                destLat: Double,
                destLng: Double
            ): List<Pair<Double, Double>> {
                polylineCalls++
                return listOf(
                    pickupLat to pickupLng,
                    31.3350 to 73.4240,
                    destLat to destLng
                )
            }
        }

        val service = com.example.pricing.LocationDistanceService(fakeProvider)
        val pts1 = service.fetchDrivingRoutePoints(31.3333, 73.4333, 31.3452, 73.4189)
        assertEquals(3, pts1.size)
        assertEquals(1, polylineCalls)

        // Cached second call does not trigger another provider lookup
        val pts2 = service.fetchDrivingRoutePoints(31.3333, 73.4333, 31.3452, 73.4189)
        assertEquals(3, pts2.size)
        assertEquals(1, polylineCalls)
    }
}
