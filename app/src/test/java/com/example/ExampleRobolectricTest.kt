package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.JaranwalaRepository
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        val repo = JaranwalaRepository.getInstance()
        repo.resetForTesting()
        repo.loginWithGoogle("passenger@jaranwala.pk", "Ahmed Raza", com.example.data.UserRole.PASSENGER)
    }

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Jaranwala Ride", appName)
    }

    @Test
    fun testAllBottomNavigationButtons() {
        val repository = JaranwalaRepository.getInstance()

        composeTestRule.setContent {
            MyApplicationTheme {
                MainAppScreen(repository = repository)
            }
        }

        // 1. Initial screen is Passenger (Rider)
        composeTestRule.onNodeWithTag("nav_item_passenger").assertExists()
        composeTestRule.onNodeWithTag("passenger_header_banner").assertExists()
        composeTestRule.onNodeWithTag("passenger_lazy_column").performScrollToNode(hasTestTag("jaranwala_osm_map_card"))
        composeTestRule.onNodeWithTag("jaranwala_osm_map_card").assertExists()

        // 2. Click Captain (Driver)
        composeTestRule.onNodeWithTag("nav_item_driver").performClick()
        composeTestRule.onNodeWithTag("driver_status_card").assertExists()
        composeTestRule.onNodeWithTag("driver_online_toggle").assertExists()

        // 3. Click Wedding & Barat
        composeTestRule.onNodeWithTag("nav_item_wedding").performClick()
        composeTestRule.onNodeWithTag("wedding_barat_tabs").assertExists()

        // 4. Click Admin (Merged with MAHI Owner Portal)
        composeTestRule.onNodeWithTag("nav_item_admin").performClick()
        composeTestRule.onNodeWithTag("admin_pin_input").assertExists()
        composeTestRule.onNodeWithTag("admin_login_btn").assertExists()

        // 5. Verify separate MAHI profile nav item is removed from bottom bar
        composeTestRule.onNodeWithTag("nav_item_profile").assertDoesNotExist()

        // 6. Return back to Passenger
        composeTestRule.onNodeWithTag("nav_item_passenger").performClick()
        composeTestRule.onNodeWithTag("passenger_header_banner").assertExists()
        composeTestRule.onNodeWithTag("passenger_lazy_column").performScrollToNode(hasTestTag("jaranwala_osm_map_card"))
        composeTestRule.onNodeWithTag("jaranwala_osm_map_card").assertExists()
    }

    @Test
    fun testAdminAuthenticatedOwnerDashboardShowsMahiProfile() {
        val repository = JaranwalaRepository.getInstance()

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.AdminPanelScreen(repository = repository)
            }
        }

        // Configure owner passcode & recovery key for test and authenticate
        val passcode = "7860"
        repository.configureOwnerCredentials(passcode, "TEST-OWNER-KEY-99")
        composeTestRule.onNodeWithTag("admin_pin_input").performTextInput(passcode)
        composeTestRule.onNodeWithTag("admin_login_btn").performClick()
        composeTestRule.waitForIdle()

        // Complete 2FA with Owner Recovery Key
        composeTestRule.onNodeWithTag("owner_master_key_input").performScrollTo().performTextInput("TEST-OWNER-KEY-99")
        composeTestRule.onNodeWithTag("admin_login_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Verify Owner Dashboard and MAHI profile card
        composeTestRule.onNodeWithTag("admin_dashboard_content").performScrollToNode(hasTestTag("founder_profile_card"))
        composeTestRule.onNodeWithTag("founder_profile_card").assertExists()
        composeTestRule.onNodeWithTag("admin_dashboard_title").assertExists()
        composeTestRule.onNodeWithTag("admin_lock_btn").assertExists()
    }

    @Test
    fun testOfficialSplashScreenDisplays() {
        var timedOut = false
        composeTestRule.setContent {
            MyApplicationTheme {
                OfficialSplashScreen(onTimeout = { timedOut = true })
            }
        }
        composeTestRule.onNodeWithTag("official_splash_screen").assertExists()
    }

    @Test
    fun testAuthScreenAndPhoneVerification() {
        val repository = JaranwalaRepository.getInstance()
        var completed = false
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.AuthScreen(
                    repository = repository,
                    onAuthComplete = { completed = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("auth_screen").assertExists()
        composeTestRule.onNodeWithTag("phone_input").assertExists()
        composeTestRule.onNodeWithTag("toggle_mode_login").assertExists()
        composeTestRule.onNodeWithTag("toggle_mode_register").assertExists()
        composeTestRule.onNodeWithTag("toggle_role_passenger").assertExists()
        composeTestRule.onNodeWithTag("toggle_role_driver").assertExists()
        composeTestRule.onNodeWithTag("btn_send_otp").assertExists()
        composeTestRule.onNodeWithTag("btn_google_signin").assertExists()
        composeTestRule.onNodeWithTag("btn_skip_auth").assertExists()

        composeTestRule.onNodeWithTag("btn_skip_auth").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(true, completed)
    }

    @Test
    fun testFareEstimateCardComponent() {
        val adminConfig = com.example.data.AdminConfig(
            bikeBaseFare = 70,
            bikePerKm = 25,
            rickshawBaseFare = 120,
            rickshawPerKm = 35,
            carBaseFare = 220,
            carPerKm = 60
        )
        var selectedCat = com.example.data.VehicleCategory.RICKSHAW

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.components.FareEstimateCard(
                    distanceKm = 4.0,
                    selectedCategory = selectedCat,
                    onCategorySelected = { selectedCat = it },
                    adminConfig = adminConfig,
                    pickup = "Committee Chowk",
                    destination = "Jaranwala Railway Station"
                )
            }
        }

        // Check essential elements exist
        composeTestRule.onNodeWithTag("fare_estimate_card").assertExists()
        composeTestRule.onNodeWithTag("fare_vehicle_selector").assertExists()
        composeTestRule.onNodeWithTag("fare_base_amount").assertExists()
        composeTestRule.onNodeWithTag("fare_distance_amount").assertExists()
        composeTestRule.onNodeWithTag("fare_total_amount").assertExists()

        // Total for 4.0 km with Rickshaw: 120 + (4 * 35) = 260 PKR
        composeTestRule.onNodeWithTag("fare_total_amount").assertExists()

        // Switch to Bike
        composeTestRule.onNodeWithTag("fare_option_bike").performClick()
        assertEquals(com.example.data.VehicleCategory.BIKE, selectedCat)
    }

    @Test
    fun testRideRequestFirestoreModel() {
        val model = com.example.data.RideRequestFirestore(
            id = "REQ-101",
            passengerId = "USER-789",
            passengerName = "Ahmed Raza",
            passengerPhone = "0301-8765432",
            pickupLocation = "Committee Chowk",
            destinationLocation = "Grain Market",
            rideType = "CAR",
            vehicleCategoryTitle = "Car Ride (AC)",
            estimatedFarePKR = 350L,
            distanceKm = 3.2,
            status = "SEARCHING",
            city = "Jaranwala"
        )

        val map = model.toMap()
        assertEquals("USER-789", map["passengerId"])
        assertEquals("Committee Chowk", map["pickupLocation"])
        assertEquals("Grain Market", map["destinationLocation"])
        assertEquals("CAR", map["rideType"])
        assertEquals(350L, map["estimatedFarePKR"])
        assertEquals("Jaranwala", map["city"])

        val domain = model.toDomainBooking()
        assertEquals("REQ-101", domain.id)
        assertEquals(com.example.data.VehicleCategory.CAR, domain.vehicleCategory)
        assertEquals(350, domain.estimatedFarePKR)
        assertEquals(com.example.data.RideStatus.SEARCHING, domain.status)
    }

    @Test
    fun testGoogleSignInButtonTriggersAuth() {
        val repository = JaranwalaRepository.getInstance()
        var completed = false
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.AuthScreen(
                    repository = repository,
                    onAuthComplete = { completed = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("btn_google_signin").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) { completed }
        assertEquals(true, completed)
    }

    @Test
    fun testRealTimeRideStatusCardComponent() {
        val testBooking = com.example.data.RideBooking(
            id = "JRW-5521",
            passengerId = "USER-101",
            passengerName = "Ahmed Raza",
            passengerPhone = "0301-8765432",
            pickup = "Committee Chowk",
            destination = "Jaranwala Railway Station",
            vehicleCategory = com.example.data.VehicleCategory.RICKSHAW,
            estimatedDistanceKm = 2.4,
            estimatedFarePKR = 210,
            status = com.example.data.RideStatus.ACCEPTED,
            driverId = "DRV-1",
            driverName = "Captain Tariq Mahmood",
            driverPhone = "0300-1122334",
            vehiclePlate = "FS-23-4412",
            driverRating = 4.9
        )

        var cancelled = false
        var tripStarted = false

        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.lazy.LazyColumn {
                    item {
                        com.example.ui.components.RealTimeRideStatusCard(
                            ride = testBooking,
                            onCancel = { cancelled = true },
                            onStartTrip = { tripStarted = true }
                        )
                    }
                }
            }
        }

        // Verify status card & details
        composeTestRule.onNodeWithTag("realtime_ride_status_card").assertExists()
        composeTestRule.onNodeWithTag("ride_status_chip").assertExists()
        composeTestRule.onNodeWithTag("realtime_ride_fare").assertExists()
        composeTestRule.onNodeWithTag("ride_status_step_tracker").assertExists()
        composeTestRule.onNodeWithTag("driver_assigned_info").assertExists()
        composeTestRule.onNodeWithTag("btn_call_captain").assertExists()
        composeTestRule.onNodeWithTag("btn_whatsapp_captain").assertExists()
        composeTestRule.onNodeWithTag("btn_start_trip").assertExists()
        composeTestRule.onNodeWithTag("btn_cancel_ride").assertExists()

        // Test start trip action
        composeTestRule.onNodeWithTag("btn_start_trip").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(true, tripStarted)
    }

    @Test
    fun testWeddingConvoyBookingFormSubmission() {
        val repository = JaranwalaRepository.getInstance()
        var submittedBooking: com.example.data.WeddingBooking? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.lazy.LazyColumn {
                    item {
                        com.example.ui.components.WeddingConvoyBookingForm(
                            repository = repository,
                            onBookingSuccess = { submittedBooking = it }
                        )
                    }
                }
            }
        }

        // Verify specialized form elements
        composeTestRule.onNodeWithTag("wedding_convoy_form").assertExists()
        composeTestRule.onNodeWithTag("fleet_size_selector").assertExists()
        composeTestRule.onNodeWithTag("fleet_size_count_text").assertExists()
        composeTestRule.onNodeWithTag("duration_selector").assertExists()
        composeTestRule.onNodeWithTag("decoration_preference_selector").assertExists()
        composeTestRule.onNodeWithTag("convoy_total_cost").assertExists()

        // Test increasing fleet size
        composeTestRule.onNodeWithTag("btn_fleet_increase").performClick()

        // Submit convoy booking
        composeTestRule.onNodeWithTag("btn_submit_convoy_booking").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        org.junit.Assert.assertNotNull(submittedBooking)
        assertEquals(true, submittedBooking?.isBaratConvoy)
        assertEquals(6, submittedBooking?.carCount)
    }

    @Test
    fun testRideHistorySectionDisplayAndFilter() {
        val repository = JaranwalaRepository.getInstance()
        var rebookedPickup = ""

        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.lazy.LazyColumn {
                    item {
                        com.example.ui.components.RideHistorySection(
                            repository = repository,
                            onRebookRide = { p, _, _ -> rebookedPickup = p }
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("ride_history_section").assertExists()
        composeTestRule.onNodeWithTag("btn_refresh_history").assertExists()
        composeTestRule.onNodeWithTag("filter_all_history").assertExists()
        composeTestRule.onNodeWithTag("filter_completed_history").assertExists()
        composeTestRule.onNodeWithTag("ride_history_list").assertExists()

        // Check if re-book button exists on first item and triggers callback
        composeTestRule.onNodeWithTag("btn_rebook_RIDE-9481").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("Committee Chowk", rebookedPickup)
    }

    @Test
    fun testDriverRatingSubmissionToFirestore() {
        val repository = JaranwalaRepository.getInstance()
        val testRide = com.example.data.RideBooking(
            id = "JRW-7890",
            passengerId = "USER-101",
            passengerName = "Ahmed Raza",
            passengerPhone = "0301-8765432",
            pickup = "Committee Chowk",
            destination = "DHQ Hospital Jaranwala",
            vehicleCategory = com.example.data.VehicleCategory.CAR,
            estimatedDistanceKm = 3.5,
            estimatedFarePKR = 380,
            status = com.example.data.RideStatus.COMPLETED,
            driverId = "DRV-3",
            driverName = "Captain Bilal Aslam",
            driverPhone = "0304-4455667",
            vehiclePlate = "LEA-21-450"
        )

        var submittedStars = 0
        var submittedComment = ""

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.components.DriverRatingDialog(
                    ride = testRide,
                    repository = repository,
                    onDismiss = {},
                    onRatingSubmitted = { stars, comment ->
                        submittedStars = stars
                        submittedComment = comment
                    }
                )
            }
        }

        // Verify rating dialog elements
        composeTestRule.onNodeWithTag("driver_rating_dialog").assertExists()
        composeTestRule.onNodeWithTag("star_rating_1").assertExists()
        composeTestRule.onNodeWithTag("star_rating_5").assertExists()
        composeTestRule.onNodeWithTag("feedback_tag_0").assertExists()
        composeTestRule.onNodeWithTag("rating_comment_input").assertExists()
        composeTestRule.onNodeWithTag("btn_submit_rating").assertExists()
        composeTestRule.onNodeWithTag("btn_skip_rating").assertExists()

        // Select 4 stars
        composeTestRule.onNodeWithTag("star_rating_4").performClick()

        // Enter review feedback
        composeTestRule.onNodeWithTag("rating_comment_input").performTextInput("Excellent AC car ride in Jaranwala")

        // Submit rating
        composeTestRule.onNodeWithTag("btn_submit_rating").performClick()
        composeTestRule.waitForIdle()

        assertEquals(4, submittedStars)
        assertEquals("Excellent AC car ride in Jaranwala", submittedComment)
    }

    @Test
    fun testEmergencySosDialogAndContacts() {
        val repository = JaranwalaRepository.getInstance()
        val testBooking = com.example.data.RideBooking(
            id = "JRW-8821",
            passengerId = "USER-101",
            passengerName = "Ahmed Raza",
            passengerPhone = "0301-8765432",
            pickup = "Katchery Bazar",
            destination = "Lahore Road By-Pass",
            vehicleCategory = com.example.data.VehicleCategory.RICKSHAW,
            estimatedDistanceKm = 3.0,
            estimatedFarePKR = 230,
            status = com.example.data.RideStatus.ACCEPTED,
            driverId = "DRV-1",
            driverName = "Captain Tariq Mahmood",
            driverPhone = "0300-1122334",
            vehiclePlate = "FSD-23-4412"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.lazy.LazyColumn {
                    item {
                        com.example.ui.components.RealTimeRideStatusCard(
                            ride = testBooking,
                            repository = repository,
                            onCancel = {}
                        )
                    }
                }
            }
        }

        // Verify SOS button exists in status card
        composeTestRule.onNodeWithTag("btn_emergency_sos").assertExists().performClick()
        composeTestRule.waitForIdle()

        // Verify SOS dialog opens with local Jaranwala emergency services
        composeTestRule.onNodeWithTag("emergency_sos_dialog").assertExists()
        composeTestRule.onNodeWithTag("btn_call_rescue_15").assertExists()
        composeTestRule.onNodeWithTag("btn_call_rescue_1122").assertExists()
        composeTestRule.onNodeWithTag("btn_call_motorway_130").assertExists()
        composeTestRule.onNodeWithTag("btn_call_sadr_police").assertExists()
        composeTestRule.onNodeWithTag("btn_call_safety_desk").assertExists()
        composeTestRule.onNodeWithTag("btn_trigger_emergency_alert").assertExists()

        // Trigger instant emergency alert
        composeTestRule.onNodeWithTag("btn_trigger_emergency_alert").performClick()
        composeTestRule.waitForIdle()

        // Verify alert confirmation banner is displayed
        composeTestRule.onNodeWithTag("sos_alert_dispatched_banner").assertExists()

        // Close dialog
        composeTestRule.onNodeWithTag("btn_close_sos_dialog").performClick()
    }

    @Test
    fun testLiveArrivalCountdownTimer() {
        val repository = JaranwalaRepository.getInstance()
        val testBooking = com.example.data.RideBooking(
            id = "JRW-3344",
            passengerId = "USER-101",
            passengerName = "Ahmed Raza",
            passengerPhone = "0301-8765432",
            pickup = "Cinema Chowk",
            destination = "Government Degree College",
            vehicleCategory = com.example.data.VehicleCategory.BIKE,
            estimatedDistanceKm = 2.0,
            estimatedFarePKR = 120,
            status = com.example.data.RideStatus.ACCEPTED,
            driverId = "DRV-2",
            driverName = "Captain Hamza Ali",
            driverPhone = "0312-9988776",
            vehiclePlate = "JRW-24-1199"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.lazy.LazyColumn {
                    item {
                        com.example.ui.components.RealTimeRideStatusCard(
                            ride = testBooking,
                            repository = repository,
                            onCancel = {}
                        )
                    }
                }
            }
        }

        // Verify live arrival countdown timer and progress
        composeTestRule.onNodeWithTag("arrival_countdown_timer").assertExists()
        composeTestRule.onNodeWithTag("countdown_time_digits").assertExists()
        composeTestRule.onNodeWithTag("arrival_countdown_progress").assertExists()
    }

    @Test
    fun testPromoCodeInputAndValidationUI() {
        val repository = JaranwalaRepository.getInstance()

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.PassengerScreen(
                    repository = repository
                )
            }
        }

        // 1. Scroll LazyColumn to Promo code card and verify input exists
        composeTestRule.onNodeWithTag("passenger_lazy_column").performScrollToNode(hasTestTag("promo_code_card"))
        composeTestRule.onNodeWithTag("promo_code_card").assertExists()
        composeTestRule.onNodeWithTag("promo_code_input").assertExists()
        composeTestRule.onNodeWithTag("btn_apply_promo").assertExists()

        // 2. Apply valid coupon "MAHI"
        composeTestRule.onNodeWithTag("promo_code_input").performTextInput("MAHI")
        composeTestRule.onNodeWithTag("btn_apply_promo").performClick()
        composeTestRule.waitForIdle()

        // 3. Status message and discount amount displayed
        composeTestRule.onNodeWithTag("promo_status_message").assertExists()
        composeTestRule.onNodeWithTag("promo_discount_amount").assertExists()
        composeTestRule.onNodeWithTag("btn_remove_promo").assertExists()

        // 4. Remove promo
        composeTestRule.onNodeWithTag("btn_remove_promo").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_apply_promo").assertExists()
    }

    @Test
    fun testProfileSavedPlacesSectionUI() {
        val repository = JaranwalaRepository.getInstance()

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.ProfileScreen(
                    repository = repository
                )
            }
        }

        // 1. Scroll LazyColumn to Saved Places Card in Profile
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasTestTag("saved_places_card"))
        composeTestRule.onNodeWithTag("saved_places_card").assertExists()
        composeTestRule.onNodeWithTag("btn_add_saved_place").assertExists()

        // 2. Open Add Saved Place Dialog
        composeTestRule.onNodeWithTag("btn_add_saved_place").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("input_saved_place_label").assertExists()
        composeTestRule.onNodeWithTag("input_saved_place_address").assertExists()
        composeTestRule.onNodeWithTag("btn_confirm_add_place").assertExists()

        // 3. Confirm and save
        composeTestRule.onNodeWithTag("btn_confirm_add_place").performClick()
        composeTestRule.waitForIdle()

        // Verify place card now exists
        composeTestRule.onNodeWithTag("saved_places_card").assertExists()
    }

    @Test
    fun testRideStatusPushNotifications() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.util.RideNotificationManager.resetForTesting()

        val sampleRide = com.example.data.RideBooking(
            id = "JRW-NOTIF-TEST",
            passengerId = "USER-101",
            passengerName = "Ahmed Raza",
            passengerPhone = "0301-8765432",
            pickup = "Committee Chowk",
            destination = "Railway Station",
            vehicleCategory = com.example.data.VehicleCategory.RICKSHAW,
            estimatedDistanceKm = 2.0,
            estimatedFarePKR = 190,
            status = com.example.data.RideStatus.ACCEPTED,
            driverName = "Tariq Mahmood",
            vehiclePlate = "FSD-23-4412"
        )

        // 1. Driver Assigned transition
        com.example.util.RideNotificationManager.notifyRideStatusChange(context, sampleRide)
        assertEquals(com.example.data.RideStatus.ACCEPTED, com.example.util.RideNotificationManager.lastNotifiedRideStatus)
        org.junit.Assert.assertTrue(com.example.util.RideNotificationManager.lastNotificationTitle?.contains("Captain Assigned") == true)
        assertEquals(1, com.example.util.RideNotificationManager.notificationCount)

        // 2. Driver Arrived transition
        val arrivedRide = sampleRide.copy(status = com.example.data.RideStatus.DRIVER_ARRIVED)
        com.example.util.RideNotificationManager.notifyRideStatusChange(context, arrivedRide)
        assertEquals(com.example.data.RideStatus.DRIVER_ARRIVED, com.example.util.RideNotificationManager.lastNotifiedRideStatus)
        org.junit.Assert.assertTrue(com.example.util.RideNotificationManager.lastNotificationTitle?.contains("Captain Arrived") == true)
        // 3. Completed transition
        val completedRide = sampleRide.copy(status = com.example.data.RideStatus.COMPLETED)
        com.example.util.RideNotificationManager.notifyRideStatusChange(context, completedRide)
        assertEquals(com.example.data.RideStatus.COMPLETED, com.example.util.RideNotificationManager.lastNotifiedRideStatus)
        org.junit.Assert.assertTrue(com.example.util.RideNotificationManager.lastNotificationTitle?.contains("Ride Completed") == true)
        assertEquals(3, com.example.util.RideNotificationManager.notificationCount)
    }

    @Test
    fun testInteractivePakistanMapControls() {
        val repository = JaranwalaRepository.getInstance()

        composeTestRule.setContent {
            MyApplicationTheme {
                MainAppScreen(repository = repository)
            }
        }

        // Verify default OpenStreetMap component and controls exist
        composeTestRule.onNodeWithTag("passenger_lazy_column").performScrollToNode(hasTestTag("jaranwala_osm_map_card"))
        composeTestRule.onNodeWithTag("jaranwala_osm_map_card").assertExists()
        composeTestRule.onNodeWithTag("osm_map_viewport").assertExists()
        composeTestRule.onNodeWithTag("osm_map_zoom_in_btn").assertExists()
        composeTestRule.onNodeWithTag("osm_map_zoom_out_btn").assertExists()
        composeTestRule.onNodeWithTag("osm_map_recenter_jaranwala_btn").assertExists()
        composeTestRule.onNodeWithTag("osm_manual_pin_btn").assertExists()
        composeTestRule.onNodeWithTag("switch_to_canvas_btn").assertExists()

        // Click zoom in and recenter on OSM map
        composeTestRule.onNodeWithTag("osm_map_zoom_in_btn").performClick()
        composeTestRule.onNodeWithTag("osm_map_recenter_jaranwala_btn").performClick()

        // Switch to Vector Canvas view and verify canvas controls
        composeTestRule.onNodeWithTag("switch_to_canvas_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("jaranwala_map_canvas").assertExists()
        composeTestRule.onNodeWithTag("canvas_switch_to_osm_btn").assertExists()

        // Switch back to OpenStreetMap
        composeTestRule.onNodeWithTag("canvas_switch_to_osm_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("jaranwala_osm_map_card").assertExists()
    }

    @Test
    fun testAdminPasscodeResetFlowUI() {
        val repository = JaranwalaRepository.getInstance()
        repository.setOwnerRecoveryKey("TEST-OWNER-KEY-99")

        composeTestRule.setContent {
            MyApplicationTheme {
                MainAppScreen(repository = repository)
            }
        }

        // Navigate to Admin tab
        composeTestRule.onNodeWithTag("nav_item_admin").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_admin_passcode_reset_btn").assertExists()

        // Open reset setup card
        composeTestRule.onNodeWithTag("open_admin_passcode_reset_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_passcode_setup_card").assertExists()
        composeTestRule.onNodeWithTag("owner_email_input").performTextReplacement("khokharbilal665@gmail.com")
        composeTestRule.onNodeWithTag("owner_recovery_key_input").performTextReplacement("TEST-OWNER-KEY-99")
        composeTestRule.onNodeWithTag("new_admin_pin_input").performTextReplacement("9988")
        composeTestRule.onNodeWithTag("confirm_admin_pin_input").performTextReplacement("9988")
        composeTestRule.onNodeWithTag("save_new_admin_passcode_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Login using newly set passcode (already auto-populated into enteredPin)
        composeTestRule.onNodeWithTag("admin_login_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Complete 2FA with Recovery Key
        composeTestRule.onNodeWithTag("owner_master_key_input").performScrollTo().performTextInput("TEST-OWNER-KEY-99")
        composeTestRule.onNodeWithTag("admin_login_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Verify Admin Dashboard is now authenticated
        composeTestRule.onNodeWithTag("admin_dashboard_content").performScrollToNode(hasTestTag("admin_dashboard_title"))
        composeTestRule.onNodeWithTag("admin_dashboard_title").assertExists()
    }

    @Test
    fun testPreBookingConfirmationDisplaysPriceBeforeBooking() {
        val repository = JaranwalaRepository.getInstance()
        repository.resetForTesting()

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.PassengerScreen(
                    repository = repository
                )
            }
        }

        // Scroll to FareEstimateCard and verify distance & fare elements are visible
        composeTestRule.onNodeWithTag("passenger_lazy_column").performScrollToNode(hasTestTag("fare_estimate_card"))
        composeTestRule.onNodeWithTag("fare_distance_km").assertExists()
        composeTestRule.onNodeWithTag("fare_distance_source").assertExists()
        composeTestRule.onNodeWithTag("fare_total_amount").assertExists()

        // Scroll to Book Ride button and click it
        composeTestRule.onNodeWithTag("passenger_lazy_column").performScrollToNode(hasTestTag("book_ride_btn"))
        composeTestRule.onNodeWithTag("book_ride_btn").performClick()
        composeTestRule.waitForIdle()

        // Verify Pre-Booking Confirmation Dialog displays distance & estimated price BEFORE ride is dispatched
        assertNull(repository.currentPassengerRide.value)
        composeTestRule.onNodeWithTag("ride_booking_confirmation_dialog").assertExists()
        composeTestRule.onNodeWithTag("confirm_dialog_distance").assertExists()
        composeTestRule.onNodeWithTag("confirm_dialog_total_fare").assertExists()
        composeTestRule.onNodeWithTag("confirm_booking_dialog_btn").assertExists()

        // Confirm booking -> Ride should now be created with calculated fare
        composeTestRule.onNodeWithTag("confirm_booking_dialog_btn").performClick()
        composeTestRule.waitForIdle()

        assertNotNull(repository.currentPassengerRide.value)
        assertTrue((repository.currentPassengerRide.value?.estimatedFarePKR ?: 0) >= 120)
    }

    @Test
    fun testDriverDashboardAvailabilityToggleAndIncomingRequestActions() {
        val repository = JaranwalaRepository.getInstance()
        repository.resetForTesting()
        repository.loginWithGoogle("captain@jaranwala.pk", "Captain Tariq", com.example.data.UserRole.DRIVER)
        repository.setDriverOnline(true)
        repository.requestRide(
            passengerName = "Sanaullah Khan",
            passengerPhone = "0300-7788990",
            pickup = "Committee Chowk",
            destination = "Grain Market",
            category = repository.activeDriver.value.vehicleCategory
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.DriverScreen(repository = repository)
            }
        }

        // Verify availability card, toggle, incoming queue section & accept/decline actions
        composeTestRule.onNodeWithTag("driver_status_card").assertExists()
        composeTestRule.onNodeWithTag("driver_online_toggle").assertExists()
        composeTestRule.onNodeWithTag("driver_dashboard_list").performScrollToNode(hasTestTag("incoming_request_card"))
        composeTestRule.onNodeWithTag("driver_incoming_requests_section").assertExists()
        composeTestRule.onNodeWithTag("incoming_request_card").assertExists()
        composeTestRule.onNodeWithTag("driver_accept_btn").performScrollTo().assertExists()
        composeTestRule.onNodeWithTag("driver_decline_btn").performScrollTo().assertExists()

        // Accept incoming ride request -> active trip card appears
        composeTestRule.onNodeWithTag("driver_accept_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("driver_dashboard_list").performScrollToNode(hasTestTag("driver_active_trip_card"))
        composeTestRule.onNodeWithTag("driver_active_trip_card").assertExists()

        // Toggle driver offline -> offline card appears
        composeTestRule.onNodeWithTag("driver_dashboard_list").performScrollToNode(hasTestTag("driver_status_card"))
        composeTestRule.onNodeWithTag("driver_online_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("driver_dashboard_list").performScrollToNode(hasTestTag("driver_offline_empty_state"))
        composeTestRule.onNodeWithTag("driver_offline_empty_state").assertExists()
    }

    @Test
    fun testRideHistoryTripDetailsModalAndUnauthenticatedBanner() {
        val repository = JaranwalaRepository.getInstance()
        repository.resetForTesting()
        repository.loginWithGoogle("passenger@jaranwala.pk", "Ahmed Raza", com.example.data.UserRole.PASSENGER)

        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.lazy.LazyColumn {
                    item {
                        com.example.ui.components.RideHistorySection(repository = repository)
                    }
                }
            }
        }

        // Tap trip item card to open Past Trip Details modal
        composeTestRule.onNodeWithTag("ride_history_item_RIDE-9481").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("trip_details_dialog").assertExists()
        composeTestRule.onNodeWithTag("btn_close_trip_details").performClick()
        composeTestRule.waitForIdle()

        // Sign out -> Unauthenticated cloud history banner renders
        repository.signOut()
        repository.fetchRideHistoryFromFirestore("")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_unauthenticated_banner").assertExists()
    }

    @Test
    fun testFcmNotificationPayloadRouting() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.util.RideNotificationManager.resetForTesting()

        com.example.util.RideNotificationManager.handleFcmDataPayload(
            context = context,
            data = mapOf(
                "type" to "CAPTAIN_ASSIGNED",
                "rideId" to "JRW-8812",
                "title" to "Captain Assigned!",
                "body" to "Captain Tariq is on the way.",
                "targetTab" to "0"
            )
        )
        assertEquals(com.example.data.RideStatus.ACCEPTED, com.example.util.RideNotificationManager.lastNotifiedRideStatus)
        assertEquals(0, com.example.util.RideNotificationManager.lastTargetTab)
        assertEquals("CAPTAIN_ASSIGNED", com.example.util.RideNotificationManager.lastNotificationType)

        com.example.util.RideNotificationManager.handleFcmDataPayload(
            context = context,
            data = mapOf(
                "type" to "NEW_RIDE_REQUEST",
                "rideId" to "JRW-8813",
                "pickup" to "Committee Chowk",
                "destination" to "Railway Station Jaranwala",
                "farePKR" to "210",
                "vehicleCategory" to "Rickshaw"
            )
        )
        assertEquals(1, com.example.util.RideNotificationManager.lastTargetTab)
        assertEquals("NEW_RIDE_REQUEST", com.example.util.RideNotificationManager.lastNotificationType)
        assertTrue(com.example.util.RideNotificationManager.lastNotificationBody?.contains("210") == true)

        com.example.util.RideNotificationManager.handleFcmDataPayload(
            context = context,
            data = mapOf(
                "type" to "DRIVER_APPROVAL",
                "approved" to "true"
            )
        )
        assertEquals("DRIVER_APPROVAL", com.example.util.RideNotificationManager.lastNotificationType)
        assertEquals(1, com.example.util.RideNotificationManager.lastTargetTab)

        com.example.util.RideNotificationManager.handleFcmDataPayload(
            context = context,
            data = mapOf(
                "type" to "OWNER_SECURITY_ALERT",
                "title" to "Unauthorized Attempt",
                "body" to "Locked out after 3 failed PIN tries."
            )
        )
        assertEquals("OWNER_SECURITY_ALERT", com.example.util.RideNotificationManager.lastNotificationType)
        assertEquals(3, com.example.util.RideNotificationManager.lastTargetTab)
    }

    @Test
    fun testScheduleForLaterBookingAndCancelUI() {
        val repository = JaranwalaRepository.getInstance()
        repository.resetForTesting()
        repository.loginWithGoogle("passenger@jaranwala.pk", "Ahmed Raza", com.example.data.UserRole.PASSENGER)

        // Verify past timestamp rejection in repository
        val pastResult = repository.scheduleRideForLater(
            passengerName = "Ahmed Raza",
            passengerPhone = "0301-8765432",
            pickup = "Committee Chowk",
            destination = "Railway Station Jaranwala",
            category = com.example.data.VehicleCategory.CAR,
            scheduledForMillis = System.currentTimeMillis() - 60_000L,
            scheduledDateDisplay = "14 Nov 2026",
            scheduledTimeDisplay = "08:00 AM"
        )
        assertTrue(pastResult is com.example.data.ScheduleRideResult.Error)

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.PassengerScreen(repository = repository)
            }
        }

        // Switch to Schedule Later mode
        composeTestRule.onNodeWithTag("passenger_lazy_column").performScrollToNode(hasTestTag("schedule_for_later_card"))
        composeTestRule.onNodeWithTag("booking_mode_schedule").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("schedule_date_btn").assertExists()
        composeTestRule.onNodeWithTag("schedule_time_btn").assertExists()
        composeTestRule.onNodeWithTag("schedule_preset_1hr").assertExists()
        composeTestRule.onNodeWithTag("schedule_preset_tomorrow_morning").performClick()
        composeTestRule.onNodeWithTag("schedule_notes_input").performTextInput("Call when near gate")

        // Open pre-booking confirmation dialog -> verify schedule badge before confirming
        composeTestRule.onNodeWithTag("passenger_lazy_column").performScrollToNode(hasTestTag("book_ride_btn"))
        composeTestRule.onNodeWithTag("book_ride_btn").performClick()
        composeTestRule.waitForIdle()

        assertTrue(repository.scheduledRides.value.isEmpty())
        composeTestRule.onNodeWithTag("ride_booking_confirmation_dialog").assertExists()
        composeTestRule.onNodeWithTag("schedule_confirm_badge").assertExists()
        composeTestRule.onNodeWithTag("confirm_booking_dialog_btn").performClick()
        composeTestRule.waitForIdle()

        // Verify scheduled ride created & rendered in Scheduled Rides section
        assertTrue(repository.scheduledRides.value.isNotEmpty())
        val createdId = repository.scheduledRides.value.first().id

        composeTestRule.onNodeWithTag("passenger_lazy_column").performScrollToNode(hasTestTag("scheduled_ride_success_banner"))
        composeTestRule.onNodeWithTag("scheduled_ride_success_banner").assertExists()

        composeTestRule.onNodeWithTag("passenger_lazy_column").performScrollToNode(hasTestTag("scheduled_rides_section"))
        composeTestRule.onNodeWithTag("scheduled_rides_section").assertExists()
        composeTestRule.onNodeWithTag("scheduled_ride_item_$createdId").assertExists()

        // Cancel the scheduled ride
        composeTestRule.onNodeWithTag("cancel_schedule_$createdId").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            com.example.data.ScheduledRideStatus.CANCELLED,
            repository.scheduledRides.value.first { it.id == createdId }.status
        )
    }

    @Test
    fun testOsmLiveDriverTrackingHudFromFirestoreCoords() {
        val activeBooking = com.example.data.RideBooking(
            id = "JRW-LIVE-OSM-1",
            passengerId = "USER-101",
            passengerName = "Ahmed Raza",
            passengerPhone = "0301-8765432",
            pickup = "Committee Chowk",
            destination = "Jaranwala Railway Station",
            vehicleCategory = com.example.data.VehicleCategory.RICKSHAW,
            estimatedDistanceKm = 2.4,
            estimatedFarePKR = 210,
            status = com.example.data.RideStatus.ACCEPTED,
            driverId = "DRV-1",
            driverName = "Captain Tariq",
            driverPhone = "0300-1122334",
            vehiclePlate = "FSD-23-4412",
            driverLat = 31.3412,
            driverLng = 73.4250,
            pickupLat = 31.3344,
            pickupLng = 73.4316,
            destLat = 31.3452,
            destLng = 73.4189
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.components.JaranwalaOpenStreetMapView(
                    pickupText = activeBooking.pickup,
                    destinationText = activeBooking.destination,
                    rideStatus = activeBooking.status,
                    category = activeBooking.vehicleCategory,
                    activeRide = activeBooking
                )
            }
        }

        composeTestRule.onNodeWithTag("jaranwala_osm_map_card").assertExists()
        composeTestRule.onNodeWithTag("osm_driver_tracking_hud").assertExists()
        composeTestRule.onNodeWithTag("osm_gps_sync_badge").assertExists()
        composeTestRule.onNodeWithTag("osm_gps_sync_badge").assertTextContains("GPS LIVE")
        composeTestRule.onNodeWithTag("osm_driver_eta_distance_text").assertExists()
        composeTestRule.onNodeWithTag("osm_driver_coords_text").assertTextContains("31.3412°N, 73.4250°E")
        composeTestRule.onNodeWithTag("osm_center_captain_btn").assertExists()

        val configuredUserAgent = org.osmdroid.config.Configuration.getInstance().userAgentValue
        assertTrue(configuredUserAgent != "osmdroid" && configuredUserAgent.startsWith("JaranwalaRide/1.0"))
    }

    @Test
    fun testRideHistoryAndPaymentsScreenCompletedTripsAndAuth() {
        val repository = JaranwalaRepository.getInstance()
        repository.resetForTesting()
        repository.loginWithGoogle("passenger@jaranwala.pk", "Ahmed Raza", com.example.data.UserRole.PASSENGER)

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.RideHistoryAndPaymentsScreen(
                    repository = repository,
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ride_history_payments_screen").assertExists()
        composeTestRule.onNodeWithTag("payments_summary_card").assertExists()
        composeTestRule.onNodeWithTag("payments_total_spent_text").assertTextContains("385", substring = true)
        composeTestRule.onNodeWithTag("completed_rides_count_text").assertTextContains("2 Trips", substring = true)

        // Scroll to completed ride card and open receipt modal
        composeTestRule.onNodeWithTag("ride_history_payments_list").performScrollToNode(hasTestTag("completed_ride_card_RIDE-9481"))
        composeTestRule.onNodeWithTag("completed_ride_card_RIDE-9481").assertExists()
        composeTestRule.onNodeWithTag("btn_receipt_RIDE-9481").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("completed_ride_receipt_dialog").assertExists()
        composeTestRule.onNodeWithTag("btn_close_receipt_dialog").performClick()
        composeTestRule.waitForIdle()

        // Sign out -> Unauthenticated banner must appear
        repository.signOut()
        repository.fetchCompletedRidesAndPaymentsFromFirestore()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("payments_unauthenticated_banner").assertExists()
    }

    @Test
    fun testMainAppNavigationToRideHistoryAndPaymentsPreservesFourTabs() {
        val repository = JaranwalaRepository.getInstance()

        composeTestRule.setContent {
            MyApplicationTheme {
                MainAppScreen(repository = repository)
            }
        }

        composeTestRule.onNodeWithTag("btn_top_history_payments").assertExists().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ride_history_payments_screen").assertExists()

        composeTestRule.onNodeWithTag("btn_back_history_payments").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_item_passenger").assertExists()
        composeTestRule.onNodeWithTag("nav_item_driver").assertExists()
        composeTestRule.onNodeWithTag("nav_item_wedding").assertExists()
        composeTestRule.onNodeWithTag("nav_item_admin").assertExists()
        composeTestRule.onNodeWithTag("nav_item_profile").assertDoesNotExist()
    }

    @Test
    fun testPickupAndDeliveryParcelBookingAndCodFlow() {
        val repository = JaranwalaRepository.getInstance()
        repository.resetForTesting()
        repository.loginWithGoogle("passenger@jaranwala.pk", "Ahmed Raza", com.example.data.UserRole.PASSENGER)

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.PassengerScreen(repository = repository)
            }
        }

        // Toggle Pickup & Delivery (Parcel & COD) option inside services_grid
        composeTestRule.onNodeWithTag("passenger_lazy_column").performScrollToNode(hasTestTag("service_option_parcel"))
        composeTestRule.onNodeWithTag("service_option_parcel").performClick()
        composeTestRule.waitForIdle()

        // Fill Parcel & COD details
        composeTestRule.onNodeWithTag("passenger_lazy_column").performScrollToNode(hasTestTag("parcel_details_card"))
        composeTestRule.onNodeWithTag("parcel_recipient_name_input").performTextInput("Usman Tariq")
        composeTestRule.onNodeWithTag("parcel_recipient_phone_input").performTextInput("0301-5566778")
        composeTestRule.onNodeWithTag("parcel_description_input").performTextInput("Medical Kit")
        composeTestRule.onNodeWithTag("parcel_cod_input").performTextInput("1800")

        // Open pre-booking confirmation dialog -> verify parcel badge before confirming
        composeTestRule.onNodeWithTag("passenger_lazy_column").performScrollToNode(hasTestTag("book_ride_btn"))
        composeTestRule.onNodeWithTag("book_ride_btn").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ride_booking_confirmation_dialog").assertExists()
        composeTestRule.onNodeWithTag("parcel_confirm_badge").assertExists()
        composeTestRule.onNodeWithTag("confirm_booking_dialog_btn").performClick()
        composeTestRule.waitForIdle()

        val activeParcel = repository.currentPassengerRide.value
        assertNotNull(activeParcel)
        assertTrue(activeParcel!!.isParcelDelivery)
        assertEquals("PARCEL", activeParcel.serviceMode)
        assertEquals("Usman Tariq", activeParcel.recipientName)
        assertEquals("0301-5566778", activeParcel.recipientPhone)
        assertEquals("Medical Kit", activeParcel.parcelDescription)
        assertEquals(1800, activeParcel.codAmountPKR)
    }

    @Test
    fun testPaymentSelectionScreenCashJazzCashEasypaisaAndBookingFlow() {
        val repository = JaranwalaRepository.getInstance()
        repository.resetForTesting()
        repository.loginWithGoogle("passenger@jaranwala.pk", "Ahmed Raza", com.example.data.UserRole.PASSENGER)

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.PassengerScreen(repository = repository)
            }
        }

        // Scroll to Payment Method selector card and open PaymentSelectionScreen
        composeTestRule.onNodeWithTag("passenger_lazy_column")
            .performScrollToNode(hasTestTag("payment_method_selector_card"))
        composeTestRule.onNodeWithTag("selected_payment_method_text", useUnmergedTree = true)
            .assertTextContains("Cash on Drop", substring = true)
        composeTestRule.onNodeWithTag("payment_method_selector_card").performClick()
        composeTestRule.waitForIdle()

        // Verify PaymentSelectionScreen renders Cash, JazzCash, and Easypaisa options
        composeTestRule.onNodeWithTag("payment_selection_screen").assertExists()
        composeTestRule.onNodeWithTag("payment_selection_lazy_column")
            .performScrollToNode(hasTestTag("payment_option_cash"))
        composeTestRule.onNodeWithTag("payment_option_cash").assertExists()
        composeTestRule.onNodeWithTag("payment_selection_lazy_column")
            .performScrollToNode(hasTestTag("payment_option_jazzcash"))
        composeTestRule.onNodeWithTag("payment_option_jazzcash").assertExists()
        composeTestRule.onNodeWithTag("payment_selection_lazy_column")
            .performScrollToNode(hasTestTag("payment_option_easypaisa"))
        composeTestRule.onNodeWithTag("payment_option_easypaisa").assertExists()

        // Select JazzCash -> enter invalid phone -> verify validation error -> enter valid phone -> confirm
        composeTestRule.onNodeWithTag("payment_selection_lazy_column")
            .performScrollToNode(hasTestTag("payment_option_jazzcash"))
        composeTestRule.onNodeWithTag("payment_option_jazzcash").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("payment_selection_lazy_column")
            .performScrollToNode(hasTestTag("wallet_account_input"))
        composeTestRule.onNodeWithTag("wallet_account_input").performTextReplacement("123")
        composeTestRule.onNodeWithTag("payment_selection_lazy_column")
            .performScrollToNode(hasTestTag("btn_confirm_payment_method"))
        composeTestRule.onNodeWithTag("btn_confirm_payment_method").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("payment_selection_lazy_column")
            .performScrollToNode(hasTestTag("wallet_validation_error"))
        composeTestRule.onNodeWithTag("wallet_validation_error").assertExists()

        // Switch to Easypaisa with valid Pakistani number
        composeTestRule.onNodeWithTag("payment_selection_lazy_column")
            .performScrollToNode(hasTestTag("payment_option_easypaisa"))
        composeTestRule.onNodeWithTag("payment_option_easypaisa").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("payment_selection_lazy_column")
            .performScrollToNode(hasTestTag("wallet_account_input"))
        composeTestRule.onNodeWithTag("wallet_account_input").performTextReplacement("0345-9876543")
        composeTestRule.onNodeWithTag("payment_selection_lazy_column")
            .performScrollToNode(hasTestTag("btn_confirm_payment_method"))
        composeTestRule.onNodeWithTag("btn_confirm_payment_method").performClick()
        composeTestRule.waitForIdle()

        // Verify return to PassengerScreen with Easypaisa active
        composeTestRule.onNodeWithTag("passenger_lazy_column")
            .performScrollToNode(hasTestTag("payment_method_selector_card"))
        composeTestRule.onNodeWithTag("selected_payment_method_text", useUnmergedTree = true)
            .assertTextContains("Easypaisa", substring = true)

        // Open booking confirmation dialog -> confirm -> verify RideBooking persists EASYPAISA
        composeTestRule.onNodeWithTag("passenger_lazy_column")
            .performScrollToNode(hasTestTag("book_ride_btn"))
        composeTestRule.onNodeWithTag("book_ride_btn").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ride_booking_confirmation_dialog").assertExists()
        composeTestRule.onNodeWithTag("confirm_dialog_payment_method").assertTextContains("Easypaisa", substring = true)
        composeTestRule.onNodeWithTag("confirm_booking_dialog_btn").performClick()
        composeTestRule.waitForIdle()

        val activeBooking = repository.currentPassengerRide.value
        assertNotNull(activeBooking)
        assertEquals("EASYPAISA", activeBooking!!.paymentMethod)

        // Complete trip and verify payment status marks PAID while preserving EASYPAISA
        repository.completeTrip()
        val completed = repository.currentPassengerRide.value
        assertNotNull(completed)
        assertEquals("EASYPAISA", completed!!.paymentMethod)
        assertEquals("PAID", completed.paymentStatus)
    }

    @Test
    fun testNavigationComposeAuthAndRequestRideWithLiveTracking() {
        val repository = JaranwalaRepository.getInstance()
        repository.resetForTesting()
        repository.loginWithGoogle("passenger@jaranwala.pk", "Ahmed Raza", com.example.data.UserRole.PASSENGER)

        composeTestRule.setContent {
            MyApplicationTheme {
                MainAppScreen(repository = repository)
            }
        }

        // 1. Navigate to reusable AuthScreen via NavHost (AppRoutes.AUTH) and return via Skip/Dismiss
        composeTestRule.onNodeWithTag("btn_top_auth").assertExists().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_screen").assertExists()
        composeTestRule.onNodeWithTag("btn_google_signin").assertExists()
        composeTestRule.onNodeWithTag("btn_skip_auth").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_item_passenger").assertExists()

        // 2. Navigate to dedicated Request Ride screen via NavHost (AppRoutes.REQUEST_RIDE)
        composeTestRule.onNodeWithTag("passenger_lazy_column")
            .performScrollToNode(hasTestTag("btn_open_dedicated_request_screen"))
        composeTestRule.onNodeWithTag("btn_open_dedicated_request_screen").performClick()
        composeTestRule.waitForIdle()

        // 3. Verify integrated OSMDroid map + Origin & Destination pin mode selector
        composeTestRule.onNodeWithTag("ride_hailing_request_screen").assertExists()
        composeTestRule.onNodeWithTag("ride_hailing_pin_mode_selector").assertExists()
        composeTestRule.onNodeWithTag("btn_select_origin_mode").assertExists().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_select_dest_mode").assertExists().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("jaranwala_osm_map_card").assertExists()

        // 4. Request ride on Request Ride screen and stay on screen for live map tracking
        composeTestRule.onNodeWithTag("ride_hailing_scrollable_column")
            .performScrollToNode(hasTestTag("btn_request_ride_hailing"))
        composeTestRule.onNodeWithTag("btn_request_ride_hailing").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_booking_dialog_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_close_request_dialog").performClick()
        composeTestRule.waitForIdle()

        assertNotNull(repository.currentPassengerRide.value)

        // 5. Simulate Captain accepting ride & pushing live GPS coordinate updates
        repository.acceptDriverRequest()
        repository.updateDriverLiveLocation(31.3395, 73.4280)
        composeTestRule.waitForIdle()

        // Verify live tracking section & OSMDroid tracking HUD reflect updated GPS coordinates
        composeTestRule.onNodeWithTag("ride_hailing_scrollable_column")
            .performScrollToNode(hasTestTag("ride_tracking_live_section"))
        composeTestRule.onNodeWithTag("ride_tracking_live_banner").assertExists()
        composeTestRule.onNodeWithTag("ride_tracking_firestore_coords")
            .assertTextContains("31.3395°N, 73.4280°E", substring = true)

        composeTestRule.onNodeWithTag("ride_hailing_scrollable_column")
            .performScrollToNode(hasTestTag("jaranwala_osm_map_card"))
        composeTestRule.onNodeWithTag("osm_driver_tracking_hud").assertExists()
        composeTestRule.onNodeWithTag("osm_driver_coords_text")
            .assertTextContains("31.3395°N, 73.4280°E", substring = true)
    }

    @Test
    fun testWalletGatewayStubBadgeInPaymentSelection() {
        val repository = JaranwalaRepository.getInstance()
        repository.resetForTesting()

        // Verify PaymentSelectionScreen displays active JazzCash / Easypaisa gateway stub status
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.PaymentSelectionScreen(
                    repository = repository,
                    fareAmountPKR = 320,
                    onBack = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("payment_selection_lazy_column")
            .performScrollToNode(hasTestTag("payment_option_jazzcash"))
        composeTestRule.onNodeWithTag("payment_option_jazzcash").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("payment_selection_lazy_column")
            .performScrollToNode(hasTestTag("payment_gateway_stub_badge"))
        composeTestRule.onNodeWithTag("payment_gateway_stub_badge")
            .assertTextContains("JazzCash", substring = true)
    }

    @Test
    fun testOsmInProgressActiveTripTracking() {
        val repository = JaranwalaRepository.getInstance()
        repository.resetForTesting()

        // Verify OpenStreetMap component updates vehicle marker position during IN_PROGRESS trip
        val activeTrip = com.example.data.RideBooking(
            id = "JRW-INPROG-1",
            passengerId = "USER-101",
            passengerName = "Ahmed Raza",
            passengerPhone = "0301-8765432",
            pickup = "Committee Chowk",
            destination = "Jaranwala Railway Station",
            vehicleCategory = com.example.data.VehicleCategory.CAR,
            estimatedDistanceKm = 2.4,
            estimatedFarePKR = 320,
            status = com.example.data.RideStatus.IN_PROGRESS,
            driverId = "DRV-1",
            driverName = "Captain Tariq",
            driverPhone = "0300-1122334",
            vehiclePlate = "FSD-23-4412",
            driverLat = 31.3380,
            driverLng = 73.4240,
            pickupLat = 31.3344,
            pickupLng = 73.4316,
            destLat = 31.3452,
            destLng = 73.4189
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.components.JaranwalaOpenStreetMapView(
                    pickupText = activeTrip.pickup,
                    destinationText = activeTrip.destination,
                    rideStatus = activeTrip.status,
                    category = activeTrip.vehicleCategory,
                    activeRide = activeTrip
                )
            }
        }
        composeTestRule.onNodeWithTag("osm_driver_coords_text")
            .assertTextContains("31.3380°N, 73.4240°E", substring = true)
        composeTestRule.onNodeWithTag("osm_driver_eta_distance_text")
            .assertTextContains("منزل تک", substring = true)
    }

    @Test
    fun testTripFeedbackCardAppearsOnCompletedRideAndSubmitsRatingAndReview() {
        val repository = JaranwalaRepository.getInstance()
        repository.resetForTesting()
        repository.loginWithGoogle("passenger@jaranwala.pk", "Ahmed Raza", com.example.data.UserRole.PASSENGER)

        repository.requestRide(
            passengerName = "Ahmed Raza",
            passengerPhone = "0301-8765432",
            pickup = "Committee Chowk",
            destination = "Jaranwala Railway Station",
            category = com.example.data.VehicleCategory.RICKSHAW
        )
        val rideId = repository.currentPassengerRide.value!!.id
        repository.acceptDriverRequest()
        repository.startTrip()
        repository.completeTrip()

        val completedRide = repository.currentPassengerRide.value!!
        assertEquals(com.example.data.RideStatus.COMPLETED, completedRide.status)

        var submittedStars = 0
        var submittedReview = ""
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.components.TripFeedbackCard(
                    ride = completedRide,
                    onSubmitFeedback = { stars, review ->
                        submittedStars = stars
                        submittedReview = review
                        repository.submitDriverRating(
                            rideId = completedRide.id,
                            driverId = completedRide.driverId,
                            stars = stars,
                            review = review
                        )
                    }
                )
            }
        }

        // Verify star-rating bar and short review field exist, select 4 stars, type review, and submit
        composeTestRule.onNodeWithTag("trip_feedback_card").assertExists()
        composeTestRule.onNodeWithTag("trip_feedback_star_bar").assertExists()
        composeTestRule.onNodeWithTag("trip_feedback_star_4").performClick()
        composeTestRule.onNodeWithTag("trip_feedback_rating_label")
            .assertTextContains("4/5", substring = true)

        composeTestRule.onNodeWithTag("trip_feedback_review_input")
            .performTextInput("Clean rickshaw and punctual captain!")
        composeTestRule.onNodeWithTag("btn_submit_trip_feedback").performClick()
        composeTestRule.waitForIdle()

        assertEquals(4, submittedStars)
        assertEquals("Clean rickshaw and punctual captain!", submittedReview)
        composeTestRule.onNodeWithTag("trip_feedback_submitted_banner").assertExists()

        val archived = repository.passengerHistory.value.first { it.id == rideId }
        assertEquals(4, archived.ratingGiven)
        assertEquals("Clean rickshaw and punctual captain!", archived.reviewGiven)
    }

    @Test
    fun testProfileScreenNavigationViewAndUpdateNamePhoneEmergencyContact() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = JaranwalaRepository.getInstance()
        repository.resetForTesting()
        repository.loginWithGoogle("passenger@jaranwala.pk", "Ahmed Raza", com.example.data.UserRole.PASSENGER)

        composeTestRule.setContent {
            MyApplicationTheme {
                MainAppScreen(repository = repository)
            }
        }

        // 1. Navigate to ProfileScreen via top bar Profile icon button
        composeTestRule.onNodeWithTag("btn_top_profile").assertExists().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("profile_screen").assertExists()
        composeTestRule.onNodeWithTag("profile_sos_card").assertExists()
        composeTestRule.onNodeWithTag("btn_profile_sos_call").assertExists()

        // 1b. Clicking SOS before saving an emergency number warns cleanly without crashing
        composeTestRule.onNodeWithTag("btn_profile_sos_call").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_sos_warning").assertExists()

        composeTestRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("profile_edit_card"))
        composeTestRule.onNodeWithTag("profile_edit_card").assertExists()
        composeTestRule.onNodeWithTag("profile_saved_name_text")
            .assertTextContains("Ahmed Raza", substring = true)

        // 2. Reject invalid empty display name
        composeTestRule.onNodeWithTag("input_profile_display_name")
            .performScrollTo()
            .performTextReplacement("")
        composeTestRule.onNodeWithTag("btn_save_profile")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_save_feedback")
            .performScrollTo()
            .assertTextContains("Display name", substring = true)

        // 3. Update display name, phone number, and saved emergency contact number
        composeTestRule.onNodeWithTag("input_profile_display_name")
            .performScrollTo()
            .performTextReplacement("Bilal Khokhar")
        composeTestRule.onNodeWithTag("input_profile_phone_number")
            .performScrollTo()
            .performTextReplacement("0301-9988776")
        composeTestRule.onNodeWithTag("input_profile_emergency_contact")
            .performScrollTo()
            .performTextReplacement("0342-9348582")
        composeTestRule.onNodeWithTag("btn_save_profile")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        // 4. Verify saved banner, state update, emergency call button, and persisted session
        composeTestRule.onNodeWithTag("profile_save_feedback").performScrollTo().assertExists()
        composeTestRule.onNodeWithTag("profile_saved_name_text")
            .performScrollTo()
            .assertTextContains("Bilal Khokhar", substring = true)
        composeTestRule.onNodeWithTag("profile_saved_phone_text")
            .performScrollTo()
            .assertTextContains("0301-9988776", substring = true)
        composeTestRule.onNodeWithTag("profile_saved_emergency_text")
            .performScrollTo()
            .assertTextContains("0342-9348582", substring = true)
        composeTestRule.onNodeWithTag("btn_call_saved_emergency")
            .performScrollTo()
            .assertExists()

        val updatedUser = repository.currentUser.value!!
        assertEquals("Bilal Khokhar", updatedUser.name)
        assertEquals("0301-9988776", updatedUser.phoneNumber)
        assertEquals("0342-9348582", updatedUser.emergencyContactNumber)

        val reloadedSession = repository.loadSession(context)
        assertEquals("Bilal Khokhar", reloadedSession?.name)
        assertEquals("0301-9988776", reloadedSession?.phoneNumber)
        assertEquals("0342-9348582", reloadedSession?.emergencyContactNumber)

        // 4b. Scroll back to prominent top SOS button and verify immediate dial intent to saved emergency contact
        composeTestRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("profile_sos_card"))
        composeTestRule.onNodeWithTag("profile_sos_number_label")
            .performScrollTo()
            .assertTextContains("0342-9348582", substring = true)
        composeTestRule.onNodeWithTag("btn_profile_sos_call")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        val launchedDialIntent = org.robolectric.Shadows.shadowOf(context as android.app.Application).nextStartedActivity
        assertNotNull("Expected SOS button to launch ACTION_DIAL immediately", launchedDialIntent)
        assertEquals(android.content.Intent.ACTION_DIAL, launchedDialIntent.action)
        assertEquals("tel:03429348582", launchedDialIntent.dataString)

        // 5. Navigate back cleanly to home tabs
        composeTestRule.onNodeWithTag("btn_back_profile").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_item_passenger").assertExists()
    }

    @Test
    fun testAuthScreenCompletePakistanPhoneRegistrationAndLoginWithOtpValidation() {
        val repository = JaranwalaRepository.getInstance()
        repository.resetForTesting()
        var authCompletedCount = 0

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.AuthScreen(
                    repository = repository,
                    initialMode = com.example.ui.screens.AuthMode.REGISTER,
                    onAuthComplete = { authCompletedCount++ }
                )
            }
        }

        // 1. Invalid phone number shows clear validation banner
        composeTestRule.onNodeWithTag("name_input").performScrollTo().performTextInput("Tariq Mahmood")
        composeTestRule.onNodeWithTag("phone_input").performScrollTo().performTextInput("0423123456")
        composeTestRule.onNodeWithTag("btn_send_otp").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_validation_error_banner")
            .performScrollTo()
            .assertTextContains("03", substring = true)

        // 2. Enter valid Pakistan (+92) Zong 0319 number and send OTP
        composeTestRule.onNodeWithTag("phone_input").performScrollTo().performTextReplacement("0319-5544332")
        composeTestRule.onNodeWithTag("btn_send_otp").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 3. Wrong OTP triggers clear error inside OTP dialog and does not bypass verification
        composeTestRule.onNodeWithTag("otp_input").assertExists().performTextInput("000000")
        composeTestRule.onNodeWithTag("btn_verify_otp").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("otp_error_text").assertExists()
        assertEquals(0, authCompletedCount)

        // 4. Enter actual generated pending OTP for normalized +92 number
        val pendingCode = repository.getPendingOtp("+923195544332")
        assertNotNull("Expected pending OTP for normalized +923195544332", pendingCode)
        composeTestRule.onNodeWithTag("otp_input").performTextReplacement(pendingCode!!)
        composeTestRule.onNodeWithTag("btn_verify_otp").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, authCompletedCount)
        val registered = repository.currentUser.value!!
        assertEquals("Tariq Mahmood", registered.name)
        assertEquals("0319-5544332", registered.phoneNumber)
        assertTrue(registered.isVerified)
        assertEquals(false, registered.isGuest)

        // 5. Log out and verify Login flow with +92 E.164 format
        composeTestRule.onNodeWithTag("btn_logout").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertNull(repository.currentUser.value)

        composeTestRule.onNodeWithTag("toggle_mode_login").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("phone_input").performScrollTo().performTextReplacement("+923195544332")
        composeTestRule.onNodeWithTag("btn_send_otp").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        val loginOtp = repository.getPendingOtp("0319-5544332")
        assertNotNull(loginOtp)
        composeTestRule.onNodeWithTag("otp_input").assertExists().performTextInput(loginOtp!!)
        composeTestRule.onNodeWithTag("btn_verify_otp").performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, authCompletedCount)
        assertEquals("0319-5544332", repository.currentUser.value?.phoneNumber)
    }

    @Test
    fun testPhoneAuthErrorClassificationIncludesOperationNotAllowedAndRegionPolicy() {
        val opNotAllowed = com.google.firebase.auth.FirebaseAuthException(
            "ERROR_OPERATION_NOT_ALLOWED",
            "This operation is not allowed. This may be because the given sign-in provider is disabled for this Firebase project."
        )
        val (opEn, opUr) = com.example.util.PakistanFirebasePhoneAuthHelper.classifyPhoneAuthError(opNotAllowed)
        assertTrue(opEn.contains("ERROR_OPERATION_NOT_ALLOWED"))
        assertTrue(opEn.contains("Phone Sign-In"))
        assertTrue(opEn.contains("Pakistan (+92)"))
        assertTrue(opUr.contains("ERROR_OPERATION_NOT_ALLOWED"))

        val regionBlocked = com.google.firebase.FirebaseException(
            "SMS unable to be sent. Region not allowed for this country."
        )
        val (regEn, regUr) = com.example.util.PakistanFirebasePhoneAuthHelper.classifyPhoneAuthError(regionBlocked)
        assertTrue(regEn.contains("Pakistan (+92)"))
        assertTrue(regEn.contains("SMS Region Policy"))
        assertTrue(regUr.contains("+92"))

        val networkEx = com.google.firebase.FirebaseNetworkException("A network error has occurred")
        val (netEn, _) = com.example.util.PakistanFirebasePhoneAuthHelper.classifyPhoneAuthError(networkEx)
        assertTrue(netEn.contains("Network error"))

        val sessionExpired = com.google.firebase.auth.FirebaseAuthException(
            "ERROR_SESSION_EXPIRED",
            "The sms code has expired."
        )
        val (expEn, _) = com.example.util.PakistanFirebasePhoneAuthHelper.classifyPhoneAuthError(sessionExpired, isCodeVerificationPhase = true)
        assertTrue(expEn.contains("Resend Code"))

        val wrongOtp = com.google.firebase.auth.FirebaseAuthInvalidCredentialsException(
            "ERROR_INVALID_VERIFICATION_CODE",
            "The verification code is invalid"
        )
        val (otpEn, _) = com.example.util.PakistanFirebasePhoneAuthHelper.classifyPhoneAuthError(wrongOtp, isCodeVerificationPhase = true)
        assertTrue(otpEn.contains("Incorrect 6-digit SMS verification code"))

        val appNotAuthorized = com.google.firebase.auth.FirebaseAuthException(
            "ERROR_APP_NOT_AUTHORIZED",
            "This app is not authorized to use Firebase Authentication"
        )
        val (shaEn, _) = com.example.util.PakistanFirebasePhoneAuthHelper.classifyPhoneAuthError(appNotAuthorized)
        assertTrue(shaEn.contains("SHA-1"))
    }

    @Test
    fun testDriverLocationTrackingServiceForegroundLifecycleAndPermissionGuard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context as android.app.Application
        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        com.example.util.DriverLocationTrackingService.resetForTesting()

        // 1. Without location permission, startTracking safely refuses without starting foreground service
        shadowApp.denyPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        org.junit.Assert.assertFalse(com.example.util.DriverLocationTrackingService.hasLocationPermission(context))
        com.example.util.DriverLocationTrackingService.startTracking(context, "JRW-TRACK-10")
        assertNull(shadowApp.nextStartedService)

        // 2. Grant FINE location permission -> builds notification & runs foreground location service cleanly
        shadowApp.grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(com.example.util.DriverLocationTrackingService.hasLocationPermission(context))

        val startIntent = android.content.Intent(context, com.example.util.DriverLocationTrackingService::class.java).apply {
            action = com.example.util.DriverLocationTrackingService.ACTION_START_TRACKING
            putExtra(com.example.util.DriverLocationTrackingService.EXTRA_RIDE_ID, "JRW-TRACK-10")
        }
        val controller = org.robolectric.Robolectric.buildService(
            com.example.util.DriverLocationTrackingService::class.java,
            startIntent
        ).create()
        controller.startCommand(0, 1)

        assertTrue(com.example.util.DriverLocationTrackingService.isRunning)
        assertEquals("JRW-TRACK-10", com.example.util.DriverLocationTrackingService.lastTrackedRideId)

        val notification = com.example.util.RideNotificationManager.buildLocationTrackingNotification(context, "JRW-TRACK-10")
        assertEquals(com.example.util.RideNotificationManager.LOCATION_CHANNEL_ID, notification.channelId)

        // 3. Stop tracking clears active state
        val stopIntent = android.content.Intent(context, com.example.util.DriverLocationTrackingService::class.java).apply {
            action = com.example.util.DriverLocationTrackingService.ACTION_STOP_TRACKING
        }
        controller.withIntent(stopIntent).startCommand(0, 2)
        controller.destroy()
        org.junit.Assert.assertFalse(com.example.util.DriverLocationTrackingService.isRunning)
        assertNull(com.example.util.DriverLocationTrackingService.lastTrackedRideId)
    }
}


