package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.AppLanguage
import com.example.data.JaranwalaRepository
import com.example.data.PaymentMethod
import com.example.data.PromoValidationResult
import com.example.data.RideBooking
import com.example.data.RideStatus
import com.example.data.VehicleCategory
import com.example.pricing.RidePricingCalculator
import com.example.ui.components.FareEstimateCard
import com.example.ui.components.JaranwalaOpenStreetMapView
import com.example.ui.components.PinSelectionMode
import com.example.ui.components.RealTimeRideStatusCard
import com.example.ui.components.RideBookingConfirmationDialog
import com.example.ui.components.TripFeedbackCard
import com.example.util.PakistanGeoService
import com.example.util.PakistanLocationPoint
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * RideHailingRequestScreen:
 * A dedicated composable screen for ride-hailing requests featuring:
 * 1. Interactive Map Placeholder with route polyline, live radar pulses, nearby captains, and zoom controls.
 * 2. Prominent Current Location Display with live GPS detection, accuracy status, and 1-tap GPS refresh.
 * 3. Destination Input Field for Ride-Hailing with auto-suggestions, saved places, and recent stops.
 * 4. Vehicle category selector with live PKR fares, promo discount support, and ride booking workflow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideHailingRequestScreen(
    repository: JaranwalaRepository = JaranwalaRepository.getInstance(),
    modifier: Modifier = Modifier,
    initialPickup: String = "Committee Chowk, Jaranwala",
    initialDestination: String = "Jaranwala Railway Station",
    onNavigateBack: (() -> Unit)? = null,
    onRideRequested: ((RideBooking) -> Unit)? = null
) {
    val context = LocalContext.current
    val appLang by repository.appLanguage.collectAsState()
    val adminConfig by repository.adminConfig.collectAsState()
    val savedPlaces by repository.savedPlaces.collectAsState()
    val currentUser by repository.currentUser.collectAsState()
    val currentRide by repository.currentPassengerRide.collectAsState()
    val selectedPaymentMethod by repository.selectedPaymentMethod.collectAsState()

    var pickupAddress by remember { mutableStateOf(initialPickup) }
    var destinationAddress by remember { mutableStateOf(initialDestination) }
    var selectedCategory by remember { mutableStateOf(VehicleCategory.RICKSHAW) }
    var passengerName by remember { mutableStateOf(currentUser?.name ?: "Ahmed Raza") }
    var passengerPhone by remember { mutableStateOf(currentUser?.phoneNumber ?: "0301-8765432") }

    LaunchedEffect(currentUser?.uid, currentUser?.name, currentUser?.phoneNumber) {
        val user = currentUser ?: return@LaunchedEffect
        val validName = user.name.takeIf { it.isNotBlank() && !it.startsWith("Guest", ignoreCase = true) }
        val validPhone = user.phoneNumber.takeIf { it.isNotBlank() && it != "0300-0000000" }
        if (validName != null && (passengerName.isBlank() || passengerName == "Ahmed Raza")) {
            passengerName = validName
        }
        if (validPhone != null && (passengerPhone.isBlank() || passengerPhone == "0301-8765432")) {
            passengerPhone = validPhone
        }
    }

    // GPS & Location Status
    var isFetchingGps by remember { mutableStateOf(false) }
    var gpsCoordinatesText by remember { mutableStateOf("31.3342° N, 73.4194° E") }
    var gpsAccuracyLabel by remember { mutableStateOf("High (±3m) • Jaranwala Urban") }
    var isGpsActive by remember { mutableStateOf(true) }

    // Destination search auto-suggestions
    var destinationSearchQuery by remember { mutableStateOf(initialDestination) }
    var isDestinationFocused by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }

    // Promo Code
    var promoCodeInput by remember { mutableStateOf("") }
    var appliedPromoCode by remember { mutableStateOf<String?>(null) }
    var discountAmountPKR by remember { mutableIntStateOf(0) }
    var promoFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var isPromoValid by remember { mutableStateOf(false) }

    // Map Viewport & Pin Selection Mode Controls
    var useOpenStreetMap by remember { mutableStateOf(true) }
    var activeMapPinMode by remember { mutableStateOf(PinSelectionMode.DESTINATION) }
    var mapZoomLevel by remember { mutableFloatStateOf(1.0f) }
    var mapPanOffset by remember { mutableStateOf(Offset.Zero) }
    var mapLayerMode by remember { mutableStateOf(0) } // 0: Standard Road, 1: Traffic, 2: Satellite

    // Request State
    var isSubmittingRequest by remember { mutableStateOf(false) }
    var showBookingConfirmDialog by remember { mutableStateOf(false) }
    var requestSuccessDialog by remember { mutableStateOf(false) }
    var showPaymentSelectionScreen by remember { mutableStateOf(false) }

    // Subscribe to real-time Firestore snapshot listener for active ride progress & GPS coordinates
    DisposableEffect(currentRide?.id) {
        val rideId = currentRide?.id
        val reg = if (!rideId.isNullOrBlank()) {
            repository.listenToActivePassengerRide(rideId)
        } else {
            null
        }
        onDispose {
            reg?.remove()
        }
    }

    // Handle Back Press
    if (onNavigateBack != null && !showPaymentSelectionScreen) {
        BackHandler { onNavigateBack() }
    }

    // Dynamic Location API distance & itemized fare calculation
    var distanceResult by remember(pickupAddress, destinationAddress) {
        mutableStateOf(repository.getInstantDistanceEstimate(pickupAddress, destinationAddress))
    }
    var isCalculatingDistance by remember { mutableStateOf(false) }

    LaunchedEffect(pickupAddress, destinationAddress) {
        if (pickupAddress.isNotBlank() && destinationAddress.isNotBlank()) {
            isCalculatingDistance = true
            delay(350)
            distanceResult = repository.fetchLocationDistanceEstimate(pickupAddress, destinationAddress)
            isCalculatingDistance = false
        }
    }

    val distanceKm = distanceResult.distanceKm.takeIf { it > 0.0 } ?: 3.2
    val priceBreakdown = remember(selectedCategory, distanceResult, adminConfig, appliedPromoCode, discountAmountPKR) {
        repository.calculatePriceBreakdown(
            category = selectedCategory,
            distanceResult = distanceResult,
            promoCode = appliedPromoCode,
            promoDiscountPKR = discountAmountPKR
        )
    }
    val rawFare = priceBreakdown.roundedSubtotalPKR
    val finalFare = priceBreakdown.finalEstimatedFarePKR
    val estimatedMinutes = priceBreakdown.estimatedDurationMinutes

    if (showPaymentSelectionScreen) {
        PaymentSelectionScreen(
            repository = repository,
            fareAmountPKR = finalFare,
            onBack = { showPaymentSelectionScreen = false },
            onPaymentConfirmed = { showPaymentSelectionScreen = false }
        )
        return
    }

    // Suggested destination points from PakistanGeoService
    val destinationSuggestions = remember(destinationSearchQuery) {
        if (destinationSearchQuery.length >= 2) {
            PakistanGeoService.searchPakistan(destinationSearchQuery).take(5)
        } else {
            PakistanGeoService.PAKISTAN_LOCATIONS.filter { it.category == "Jaranwala Hub" }.take(5)
        }
    }

    // Location Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            isFetchingGps = true
            PakistanGeoService.fetchCurrentGpsLocation(
                context = context,
                onSuccess = { point ->
                    isFetchingGps = false
                    isGpsActive = true
                    gpsCoordinatesText = String.format(Locale.US, "%.4f° N, %.4f° E", point.latitude, point.longitude)
                    gpsAccuracyLabel = "Live GPS • Jaranwala"
                    pickupAddress = point.name
                    Toast.makeText(context, "Current GPS location updated!", Toast.LENGTH_SHORT).show()
                },
                onError = { _ ->
                    isFetchingGps = false
                    gpsCoordinatesText = "31.3342° N, 73.4194° E"
                    pickupAddress = "Committee Chowk, Jaranwala"
                }
            )
        } else {
            Toast.makeText(context, "Location permission denied. Using Jaranwala default.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("ride_hailing_request_screen"),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (appLang == AppLanguage.URDU) "سواری حاصل کریں" else "Request a Ride",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF10B981),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "LIVE",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = if (appLang == AppLanguage.URDU) "جرانوالہ و پاکستان • محفوظ اور فوری سفر" else "Jaranwala & Pakistan • Safe & Fast Rides",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("btn_back_ride_request")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    } else {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "Ride",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    // Language Switcher Badge
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val next = if (appLang == AppLanguage.URDU) AppLanguage.ENGLISH else AppLanguage.URDU
                                repository.setLanguage(next)
                            }
                            .testTag("btn_toggle_lang_ride_request"),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (appLang == AppLanguage.URDU) "English" else "اردو",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("ride_hailing_scrollable_column"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // 1. INTEGRATED OSMDROID MAP FOR ORIGIN & DESTINATION PIN SELECTION + REAL-TIME RIDE TRACKING
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Origin vs Destination Pin Mode Selector synchronized with OSMDroid tap mode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ride_hailing_pin_mode_selector"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = activeMapPinMode == PinSelectionMode.PICKUP,
                                onClick = { activeMapPinMode = PinSelectionMode.PICKUP },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "Select Origin on Map",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (appLang == AppLanguage.URDU) "نقشے پر پک اپ چنیں" else "Set Origin on Map",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_select_origin_mode"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF065F46),
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color(0xFF34D399)
                                )
                            )

                            FilterChip(
                                selected = activeMapPinMode == PinSelectionMode.DESTINATION,
                                onClick = { activeMapPinMode = PinSelectionMode.DESTINATION },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "Select Destination on Map",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (appLang == AppLanguage.URDU) "نقشے پر منزل چنیں" else "Set Dest on Map",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_select_dest_mode"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFB45309),
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color(0xFFFDE68A)
                                )
                            )
                        }
                    }

                    if (useOpenStreetMap) {
                        JaranwalaOpenStreetMapView(
                            pickupText = pickupAddress,
                            destinationText = destinationAddress,
                            rideStatus = currentRide?.status ?: RideStatus.IDLE,
                            category = currentRide?.vehicleCategory ?: selectedCategory,
                            activeRide = currentRide,
                            pinSelectionMode = activeMapPinMode,
                            onPinSelectionModeChange = { activeMapPinMode = it },
                            onPickupChange = { selected ->
                                pickupAddress = selected
                                val resolved = com.example.pricing.LocationDistanceService.getInstance()
                                    .resolveLocalCoordinates(selected)
                                if (resolved != null) {
                                    gpsCoordinatesText = String.format(
                                        Locale.US,
                                        "%.4f° N, %.4f° E",
                                        resolved.first,
                                        resolved.second
                                    )
                                    gpsAccuracyLabel = "Map Pin • ${selected.substringBefore(",")}"
                                }
                            },
                            onDestinationChange = { selected ->
                                destinationAddress = selected
                                destinationSearchQuery = selected
                                showSuggestions = false
                            },
                            onSwitchToCanvas = { useOpenStreetMap = false }
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RideHailingMapPlaceholder(
                                pickup = pickupAddress,
                                destination = destinationAddress,
                                selectedCategory = selectedCategory,
                                distanceKm = distanceKm,
                                estimatedMinutes = estimatedMinutes,
                                zoomLevel = mapZoomLevel,
                                panOffset = mapPanOffset,
                                layerMode = mapLayerMode,
                                onZoomIn = { mapZoomLevel = (mapZoomLevel * 1.25f).coerceAtMost(2.5f) },
                                onZoomOut = { mapZoomLevel = (mapZoomLevel / 1.25f).coerceAtLeast(0.7f) },
                                onRecenter = {
                                    mapZoomLevel = 1.0f
                                    mapPanOffset = Offset.Zero
                                },
                                onToggleLayer = { mapLayerMode = (mapLayerMode + 1) % 3 },
                                onPanChange = { delta -> mapPanOffset += delta }
                            )
                            OutlinedButton(
                                onClick = { useOpenStreetMap = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_switch_request_to_osm"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Switch to Live OpenStreetMap", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 1b. REAL-TIME RIDE TRACKING STATUS CARD (Driven by Firestore Snapshot Listeners)
            if (currentRide != null) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("ride_tracking_live_section")
                    ) {
                        Surface(
                            color = Color(0xFF064E3B),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ride_tracking_live_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF34D399))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (currentRide!!.driverLat != null && currentRide!!.driverLng != null) {
                                            "Firestore Live GPS: ${String.format(Locale.US, "%.4f°N, %.4f°E", currentRide!!.driverLat, currentRide!!.driverLng)}"
                                        } else {
                                            "Firestore Real-Time Tracking Active (${currentRide!!.status.name})"
                                        },
                                        color = Color(0xFFD1FAE5),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("ride_tracking_firestore_coords")
                                    )
                                }
                                Text(
                                    text = currentRide!!.id,
                                    color = Color(0xFFFDE68A),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        RealTimeRideStatusCard(
                            ride = currentRide!!,
                            repository = repository,
                            appLang = appLang,
                            onCancel = { repository.cancelRide() },
                            onStartTrip = { repository.startTrip() },
                            onCompleteTrip = { repository.completeTrip() },
                            onDismissCompleted = { repository.dismissCompletedRide() },
                            onCallCaptain = { phone ->
                                try {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                                } catch (_: Exception) {}
                            },
                            onWhatsAppCaptain = { phone ->
                                try {
                                    val clean = phone.replace("-", "").replace(" ", "").replace("+", "")
                                    val intl = if (clean.startsWith("0")) "92" + clean.drop(1) else clean
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$intl")))
                                } catch (_: Exception) {}
                            }
                        )

                        if (currentRide?.status == RideStatus.COMPLETED) {
                            val finishedRide = currentRide!!
                            TripFeedbackCard(
                                ride = finishedRide,
                                appLang = appLang,
                                onSubmitFeedback = { stars, review ->
                                    repository.submitDriverRating(
                                        rideId = finishedRide.id,
                                        driverId = finishedRide.driverId,
                                        stars = stars,
                                        review = review
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 2. CURRENT LOCATION DISPLAY (Prominent GPS card with live detection & refresh)
            item {
                CurrentLocationDisplayCard(
                    currentAddress = pickupAddress,
                    coordinatesText = gpsCoordinatesText,
                    accuracyText = gpsAccuracyLabel,
                    isGpsActive = isGpsActive,
                    isFetching = isFetchingGps,
                    appLang = appLang,
                    onRefreshGps = {
                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasFine || hasCoarse) {
                            isFetchingGps = true
                            PakistanGeoService.fetchCurrentGpsLocation(
                                context = context,
                                onSuccess = { point ->
                                    isFetchingGps = false
                                    isGpsActive = true
                                    gpsCoordinatesText = String.format(Locale.US, "%.4f° N, %.4f° E", point.latitude, point.longitude)
                                    gpsAccuracyLabel = "Live GPS • Jaranwala"
                                    pickupAddress = point.name
                                },
                                onError = { _ ->
                                    isFetchingGps = false
                                }
                            )
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    onAddressChange = { pickupAddress = it }
                )
            }

            // 3. DESTINATION INPUT FIELD FOR RIDE-HAILING
            item {
                DestinationInputFieldCard(
                    destination = destinationAddress,
                    searchQuery = destinationSearchQuery,
                    suggestions = destinationSuggestions,
                    showSuggestions = showSuggestions,
                    savedPlaces = savedPlaces,
                    appLang = appLang,
                    onDestinationChange = { newDest ->
                        destinationAddress = newDest
                        destinationSearchQuery = newDest
                    },
                    onQueryChange = { newQuery ->
                        destinationSearchQuery = newQuery
                        destinationAddress = newQuery
                        showSuggestions = newQuery.isNotBlank()
                    },
                    onSelectSuggestion = { suggestion ->
                        destinationAddress = suggestion.name
                        destinationSearchQuery = suggestion.name
                        showSuggestions = false
                    },
                    onClearDestination = {
                        destinationAddress = ""
                        destinationSearchQuery = ""
                        showSuggestions = false
                    },
                    onToggleSuggestions = { showSuggestions = !showSuggestions }
                )
            }

            // 4. VEHICLE CATEGORY SELECTOR & FARE BREAKDOWN
            item {
                RideVehicleSelectionSection(
                    selectedCategory = selectedCategory,
                    distanceKm = distanceKm,
                    estimatedMinutes = estimatedMinutes,
                    adminConfig = adminConfig,
                    appLang = appLang,
                    onCategorySelect = { selectedCategory = it }
                )
            }

            // 4b. TRANSPARENT LOCATION API PRICE BREAKDOWN CARD
            item {
                FareEstimateCard(
                    distanceKm = distanceKm,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    adminConfig = adminConfig,
                    appLang = appLang,
                    pickup = pickupAddress,
                    destination = destinationAddress,
                    promoDiscountPKR = discountAmountPKR,
                    appliedPromoCode = appliedPromoCode,
                    distanceSource = distanceResult.source,
                    apiDurationMinutes = distanceResult.estimatedDurationMinutes,
                    isCalculatingDistance = isCalculatingDistance
                )
            }

            // 5. PROMO CODE FIELD
            item {
                PromoCodeInputCard(
                    promoCode = promoCodeInput,
                    appliedCode = appliedPromoCode,
                    discountAmount = discountAmountPKR,
                    feedbackMessage = promoFeedbackMessage,
                    isSuccess = isPromoValid,
                    appLang = appLang,
                    onCodeChange = { promoCodeInput = it },
                    onApplyPromo = {
                        val result = repository.validatePromoCode(promoCodeInput, rawFare)
                        when (result) {
                            is PromoValidationResult.Success -> {
                                appliedPromoCode = result.promo.code
                                discountAmountPKR = result.discountAmountPKR
                                promoFeedbackMessage = if (appLang == AppLanguage.URDU) result.urduMessage else result.message
                                isPromoValid = true
                            }
                            is PromoValidationResult.Error -> {
                                appliedPromoCode = null
                                discountAmountPKR = 0
                                promoFeedbackMessage = if (appLang == AppLanguage.URDU) result.urduMessage else result.message
                                isPromoValid = false
                            }
                        }
                    },
                    onClearPromo = {
                        promoCodeInput = ""
                        appliedPromoCode = null
                        discountAmountPKR = 0
                        promoFeedbackMessage = null
                        isPromoValid = false
                    }
                )
            }

            // 6. PASSENGER CONTACT DETAILS
            item {
                PassengerDetailsCard(
                    name = passengerName,
                    phone = passengerPhone,
                    appLang = appLang,
                    onNameChange = { passengerName = it },
                    onPhoneChange = { passengerPhone = it }
                )
            }

            // 7. CONFIRM & REQUEST RIDE BUTTON
            item {
                RideRequestActionCard(
                    category = selectedCategory,
                    pickup = pickupAddress,
                    destination = destinationAddress,
                    distanceKm = distanceKm,
                    finalFarePKR = finalFare,
                    discountPKR = discountAmountPKR,
                    isSubmitting = isSubmittingRequest,
                    appLang = appLang,
                    paymentMethod = selectedPaymentMethod,
                    onSelectPaymentClick = { showPaymentSelectionScreen = true },
                    onRequestClick = {
                        if (destinationAddress.isBlank()) {
                            Toast.makeText(context, "Please enter a destination", Toast.LENGTH_SHORT).show()
                            return@RideRequestActionCard
                        }
                        showBookingConfirmDialog = true
                    }
                )
            }

            // Bottom Spacer
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Pre-Booking Fare & Route Confirmation Dialog
    if (showBookingConfirmDialog) {
        RideBookingConfirmationDialog(
            pickup = pickupAddress,
            destination = destinationAddress,
            breakdown = priceBreakdown,
            appLang = appLang,
            paymentMethod = selectedPaymentMethod,
            onConfirmBooking = {
                showBookingConfirmDialog = false
                isSubmittingRequest = true
                repository.requestRide(
                    passengerName = passengerName,
                    passengerPhone = passengerPhone,
                    pickup = pickupAddress,
                    destination = destinationAddress,
                    category = selectedCategory,
                    promoCode = appliedPromoCode,
                    discountPKR = discountAmountPKR,
                    overrideDistanceKm = distanceKm,
                    overrideFarePKR = finalFare,
                    paymentMethod = selectedPaymentMethod.firestoreValue
                )
                isSubmittingRequest = false
                requestSuccessDialog = true
                repository.currentPassengerRide.value?.let { booking ->
                    onRideRequested?.invoke(booking)
                }
            },
            onDismiss = { showBookingConfirmDialog = false }
        )
    }

    // Success Confirmation Dialog
    if (requestSuccessDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { requestSuccessDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (appLang == AppLanguage.URDU) "سواری کی درخواست ارسال کر دی گئی" else "Ride Request Sent!",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (appLang == AppLanguage.URDU)
                            "قریبی کیپٹن کو اطلاع پہنچا دی گئی ہے۔ آپ کا کرایہ: ₨ $finalFare (${selectedCategory.title})"
                        else
                            "Nearby Captain is being assigned. Estimated Fare: ₨ $finalFare (${selectedCategory.title})."
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = "📍 From: $pickupAddress", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "🏁 To: $destinationAddress", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "📏 Distance: $distanceKm km • ~${estimatedMinutes} mins", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        requestSuccessDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("btn_close_request_dialog")
                ) {
                    Text(if (appLang == AppLanguage.URDU) "لائیو ٹریکنگ دیکھیں" else "Track Live on Map")
                }
            }
        )
    }
}

/**
 * 1. MAP PLACEHOLDER COMPOSABLE
 * An interactive, visually polished map placeholder with road grid, route polyline,
 * GPS pulse rings, nearby moving captain glyphs, and viewport controls.
 */
@Composable
fun RideHailingMapPlaceholder(
    pickup: String,
    destination: String,
    selectedCategory: VehicleCategory,
    distanceKm: Double,
    estimatedMinutes: Int,
    zoomLevel: Float,
    panOffset: Offset,
    layerMode: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    onToggleLayer: () -> Unit,
    onPanChange: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "map_animations")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 38f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(20.dp))
            .testTag("ride_map_placeholder"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E19)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Canvas drawing styled map representation
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onPanChange(dragAmount)
                        }
                    }
                    .testTag("map_canvas_placeholder")
            ) {
                val w = size.width
                val h = size.height

                // Background tint based on layer mode
                val bgColor = when (layerMode) {
                    1 -> Color(0xFF13231D) // Traffic
                    2 -> Color(0xFF0A1410) // Satellite dark
                    else -> Color(0xFF0D1B16) // Standard
                }
                drawRect(color = bgColor)

                // Grid roads (Jaranwala street grid pattern)
                val roadColor = if (layerMode == 2) Color(0x334B5E55) else Color(0x2E2E624B)
                val mainRoadColor = if (layerMode == 1) Color(0x66F59E0B) else Color(0x444E836A)

                // Horizontal streets
                for (i in 1..6) {
                    val y = (h / 7f) * i + panOffset.y * 0.3f
                    drawLine(
                        color = if (i % 2 == 0) mainRoadColor else roadColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = if (i % 2 == 0) 5f * zoomLevel else 2.5f * zoomLevel
                    )
                }

                // Vertical avenues (Mandi Road / Railway Road corridor)
                for (j in 1..8) {
                    val x = (w / 9f) * j + panOffset.x * 0.3f
                    drawLine(
                        color = if (j == 3 || j == 6) mainRoadColor else roadColor,
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = if (j == 3 || j == 6) 6f * zoomLevel else 2.5f * zoomLevel
                    )
                }

                // Diagonal Canal road (Gugera Canal feeder)
                val canalPath = Path().apply {
                    moveTo(0f, h * 0.85f + panOffset.y)
                    cubicTo(
                        w * 0.3f + panOffset.x, h * 0.7f + panOffset.y,
                        w * 0.7f + panOffset.x, h * 0.3f + panOffset.y,
                        w, h * 0.15f + panOffset.y
                    )
                }
                drawPath(
                    path = canalPath,
                    color = Color(0x330284C7),
                    style = Stroke(width = 8f * zoomLevel, cap = StrokeCap.Round)
                )

                // Coordinates for Pickup & Destination
                val pickupX = (w * 0.28f + panOffset.x).coerceIn(40f, w - 40f)
                val pickupY = (h * 0.65f + panOffset.y).coerceIn(40f, h - 40f)

                val destX = (w * 0.76f + panOffset.x).coerceIn(40f, w - 40f)
                val destY = (h * 0.32f + panOffset.y).coerceIn(40f, h - 40f)

                // Route Polyline
                val routePath = Path().apply {
                    moveTo(pickupX, pickupY)
                    lineTo(pickupX + (destX - pickupX) * 0.45f, pickupY)
                    lineTo(pickupX + (destX - pickupX) * 0.45f, destY)
                    lineTo(destX, destY)
                }

                // Polyline glow casing
                drawPath(
                    path = routePath,
                    color = Color(0x4410B981),
                    style = Stroke(width = 10f * zoomLevel, cap = StrokeCap.Round)
                )
                // Active polyline dashes
                drawPath(
                    path = routePath,
                    color = Color(0xFF10B981),
                    style = Stroke(
                        width = 4.5f * zoomLevel,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f), 0f)
                    )
                )

                // Nearby Captains on the map (Bikes / Rickshaws)
                val captain1 = Offset(pickupX + 50f, pickupY - 30f)
                val captain2 = Offset(pickupX - 45f, pickupY + 25f)
                drawCircle(color = Color(0xFFF59E0B), radius = 6f * zoomLevel, center = captain1)
                drawCircle(color = Color.White, radius = 2.5f * zoomLevel, center = captain1)
                drawCircle(color = Color(0xFFF59E0B), radius = 6f * zoomLevel, center = captain2)
                drawCircle(color = Color.White, radius = 2.5f * zoomLevel, center = captain2)

                // Pickup location pulsing radar
                drawCircle(
                    color = Color(0xFF10B981).copy(alpha = pulseAlpha),
                    radius = pulseRadius * zoomLevel,
                    center = Offset(pickupX, pickupY)
                )
                drawCircle(
                    color = Color(0xFF064E3B),
                    radius = 12f * zoomLevel,
                    center = Offset(pickupX, pickupY)
                )
                drawCircle(
                    color = Color(0xFF10B981),
                    radius = 6.5f * zoomLevel,
                    center = Offset(pickupX, pickupY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5f * zoomLevel,
                    center = Offset(pickupX, pickupY)
                )

                // Destination marker (Amber drop-pin)
                drawCircle(
                    color = Color(0x66EF4444),
                    radius = 12f * zoomLevel,
                    center = Offset(destX, destY)
                )
                drawCircle(
                    color = Color(0xFFEF4444),
                    radius = 7.5f * zoomLevel,
                    center = Offset(destX, destY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3f * zoomLevel,
                    center = Offset(destX, destY)
                )
            }

            // Top-Left Map Mode & Region Info
            Surface(
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp)
                    .align(Alignment.TopStart),
                color = Color(0xCC06251A),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Jaranwala Hub (31.33° N, 73.43° E)",
                        color = Color(0xFFD1FAE5),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Top-Right ETA & Distance Chip
            Surface(
                modifier = Modifier
                    .padding(end = 12.dp, top = 12.dp)
                    .align(Alignment.TopEnd),
                color = Color(0xCC043825),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "~$estimatedMinutes min • $distanceKm km",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom-Right Floating Controls (Zoom in, Zoom out, Recenter, Layer toggle)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Layer mode toggle
                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onToggleLayer() }
                        .testTag("map_toggle_layer"),
                    color = Color(0xDD0F2E23),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Toggle Layer",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // Recenter GPS
                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onRecenter() }
                        .testTag("map_recenter_gps"),
                    color = Color(0xDD064E3B),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Recenter GPS",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // Zoom In
                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onZoomIn() }
                        .testTag("map_zoom_in"),
                    color = Color(0xDD1E293B),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }

                // Zoom Out
                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onZoomOut() }
                        .testTag("map_zoom_out"),
                    color = Color(0xDD1E293B),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "−", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }

            // Bottom-Left Hint Chip
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 12.dp),
                color = Color(0xB3000000),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Drag to pan • 2 Available Captains",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * 2. CURRENT LOCATION DISPLAY COMPOSABLE
 * Dedicated card displaying current location with GPS pulse, accuracy meter,
 * and 1-tap live GPS fetching.
 */
@Composable
fun CurrentLocationDisplayCard(
    currentAddress: String,
    coordinatesText: String,
    accuracyText: String,
    isGpsActive: Boolean,
    isFetching: Boolean,
    appLang: AppLanguage,
    onRefreshGps: () -> Unit,
    onAddressChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("current_location_display"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Status & Live GPS Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isGpsActive) Color(0xFF10B981) else Color(0xFF94A3B8))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (appLang == AppLanguage.URDU) "آپ کا موجودہ مقام (Pickup)" else "Your Current Location (Pickup)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 1-Tap Live GPS Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isFetching) { onRefreshGps() }
                        .testTag("btn_fetch_live_gps"),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isFetching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Refresh GPS",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isFetching) "Locating..." else (if (appLang == AppLanguage.URDU) "لائیو GPS حاصل کریں" else "Use Live GPS"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Editable Address Input Field
            OutlinedTextField(
                value = currentAddress,
                onValueChange = onAddressChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pickup_location_input"),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Pickup Location",
                        tint = Color(0xFF10B981)
                    )
                },
                trailingIcon = {
                    if (currentAddress.isNotBlank()) {
                        IconButton(
                            onClick = { onAddressChange("") },
                            modifier = Modifier.testTag("btn_clear_pickup")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                label = {
                    Text(
                        text = if (appLang == AppLanguage.URDU) "پک اپ پوائنٹ درج کریں" else "Pickup Location / Landmark",
                        fontSize = 12.sp
                    )
                },
                placeholder = {
                    Text("e.g. Committee Chowk, Cinema Chowk")
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Sub-metrics: Accuracy & Coordinates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = accuracyText,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = coordinatesText,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 3. DESTINATION INPUT FIELD CARD FOR RIDE-HAILING
 * High-fidelity destination search box with clear button, suggestions list,
 * and 1-tap saved places chips.
 */
@Composable
fun DestinationInputFieldCard(
    destination: String,
    searchQuery: String,
    suggestions: List<PakistanLocationPoint>,
    showSuggestions: Boolean,
    savedPlaces: List<com.example.data.SavedPlace>,
    appLang: AppLanguage,
    onDestinationChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectSuggestion: (PakistanLocationPoint) -> Unit,
    onClearDestination: () -> Unit,
    onToggleSuggestions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("destination_input_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (appLang == AppLanguage.URDU) "منزل کا انتخاب (Destination)" else "Where Are You Going? (Destination)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Main Destination Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("destination_location_input"),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Destination",
                        tint = Color(0xFFF59E0B)
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = onClearDestination,
                                modifier = Modifier.testTag("btn_clear_destination")
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(
                            onClick = onToggleSuggestions,
                            modifier = Modifier.testTag("btn_toggle_suggestions")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                label = {
                    Text(if (appLang == AppLanguage.URDU) "منزل کا پتہ یا مشہور جگہ" else "Destination Address or Landmark")
                },
                placeholder = {
                    Text("e.g. Railway Station, Grain Market, Syedwala...")
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF59E0B),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Auto-complete suggestion dropdown list
            AnimatedVisibility(
                visible = showSuggestions && suggestions.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text(
                            text = if (appLang == AppLanguage.URDU) "تجویز کردہ مقامات (Suggestions):" else "Suggested Landmarks in Jaranwala:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        suggestions.forEach { point ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onSelectSuggestion(point) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (appLang == AppLanguage.URDU) "${point.name} (${point.urduName})" else point.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (point.description.isNotBlank()) {
                                        Text(
                                            text = point.description,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick 1-Tap Saved Places Chips
            if (savedPlaces.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (appLang == AppLanguage.URDU) "محفوظ مقامات (1-Tap Fast Pick):" else "Saved Places (1-Tap Pick):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("saved_places_row")
                    ) {
                        itemsIndexed(savedPlaces) { index, place ->
                            val isSelected = destination == place.address
                            FilterChip(
                                selected = isSelected,
                                onClick = { onDestinationChange(place.address) },
                                leadingIcon = {
                                    val icon = when (place.iconType.uppercase()) {
                                        "HOME" -> Icons.Default.Home
                                        "WORK" -> Icons.Default.Work
                                        "COLLEGE" -> Icons.Default.School
                                        else -> Icons.Default.Place
                                    }
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                label = {
                                    Text(
                                        text = place.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.testTag("saved_place_chip_$index"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4. VEHICLE CATEGORY SELECTION & METRICS SECTION
 */
@Composable
fun RideVehicleSelectionSection(
    selectedCategory: VehicleCategory,
    distanceKm: Double,
    estimatedMinutes: Int,
    adminConfig: com.example.data.AdminConfig,
    appLang: AppLanguage,
    onCategorySelect: (VehicleCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("vehicle_selection_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (appLang == AppLanguage.URDU) "سواری کی قسم منتخب کریں" else "Select Vehicle Category",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Distance: $distanceKm km",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // 3 Vehicle Cards: Bike, Rickshaw, AC Car
            val categories = listOf(VehicleCategory.BIKE, VehicleCategory.RICKSHAW, VehicleCategory.CAR)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    val (base, perKm) = RidePricingCalculator.rateForCategory(cat, adminConfig)
                    val estFare = RidePricingCalculator.calculateFareBreakdown(
                        category = cat,
                        distanceKm = distanceKm,
                        adminConfig = adminConfig
                    ).finalEstimatedFarePKR

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onCategorySelect(cat) }
                            .testTag("option_vehicle_${cat.name.lowercase()}"),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val icon = when (cat) {
                                VehicleCategory.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
                                VehicleCategory.RICKSHAW -> Icons.Default.ElectricRickshaw
                                VehicleCategory.CAR -> Icons.Default.DirectionsCar
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = cat.title,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = cat.title.substringBefore(" ("),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "₨ $estFare",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFF59E0B)
                            )
                            Text(
                                text = "Base ₨$base",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5. PROMO CODE INPUT CARD
 */
@Composable
fun PromoCodeInputCard(
    promoCode: String,
    appliedCode: String?,
    discountAmount: Int,
    feedbackMessage: String?,
    isSuccess: Boolean,
    appLang: AppLanguage,
    onCodeChange: (String) -> Unit,
    onApplyPromo: () -> Unit,
    onClearPromo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("promo_code_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ConfirmationNumber,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (appLang == AppLanguage.URDU) "پرومو کوڈ (ڈسکاؤنٹ)" else "Promo Code (Save PKR ₨)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (appliedCode != null) {
                    Surface(
                        color = Color(0xFFD1FAE5),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "APPLIED: $appliedCode (-₨ $discountAmount)",
                            color = Color(0xFF065F46),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = promoCode,
                    onValueChange = onCodeChange,
                    placeholder = { Text("e.g. MAHI, FIRST, JARANWALA", fontSize = 12.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("promo_code_input_field"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                if (appliedCode != null) {
                    OutlinedButton(
                        onClick = onClearPromo,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("btn_clear_promo")
                    ) {
                        Text("Remove", fontSize = 11.sp)
                    }
                } else {
                    Button(
                        onClick = onApplyPromo,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("btn_apply_promo_code")
                    ) {
                        Text("Apply", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (feedbackMessage != null) {
                Text(
                    text = feedbackMessage,
                    fontSize = 11.sp,
                    color = if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 6. PASSENGER DETAILS CARD
 */
@Composable
fun PassengerDetailsCard(
    name: String,
    phone: String,
    appLang: AppLanguage,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("passenger_details_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (appLang == AppLanguage.URDU) "مسافر کی معلومات (رابطہ برائے کیپٹن)" else "Passenger Contact Details",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("passenger_name_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = { Text("Phone", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("passenger_phone_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
            }
        }
    }
}

/**
 * 7. CONFIRM & REQUEST RIDE ACTION CARD
 */
@Composable
fun RideRequestActionCard(
    category: VehicleCategory,
    pickup: String,
    destination: String,
    distanceKm: Double,
    finalFarePKR: Int,
    discountPKR: Int,
    isSubmitting: Boolean,
    appLang: AppLanguage,
    paymentMethod: PaymentMethod = PaymentMethod.CASH,
    onSelectPaymentClick: () -> Unit = {},
    onRequestClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Fare Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (appLang == AppLanguage.URDU) "کل تخمینہ کرایہ:" else "Total Estimated Fare:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "₨ $finalFarePKR",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (discountPKR > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "₨ ${finalFarePKR + discountPKR}",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                style = androidx.compose.ui.text.TextStyle(
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                                )
                            )
                        }
                    }
                }

                Surface(
                    color = when (paymentMethod) {
                        PaymentMethod.CASH -> Color(0xFFF59E0B)
                        PaymentMethod.JAZZCASH -> Color(0xFFDC2626)
                        PaymentMethod.EASYPAISA -> Color(0xFF16A34A)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectPaymentClick() }
                        .testTag("btn_select_payment_method_hailing")
                ) {
                    Text(
                        text = when (paymentMethod) {
                            PaymentMethod.CASH -> "Cash on Drop • تبدیل"
                            PaymentMethod.JAZZCASH -> "JazzCash • تبدیل"
                            PaymentMethod.EASYPAISA -> "Easypaisa • تبدیل"
                        },
                        color = if (paymentMethod == PaymentMethod.CASH) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            // Primary Booking Button
            Button(
                onClick = onRequestClick,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_request_ride_hailing"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Contacting Nearby Captains...", fontSize = 14.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (appLang == AppLanguage.URDU)
                            "${category.title} بُک کریں • ₨ $finalFarePKR"
                        else
                            "Confirm & Request ${category.title.substringBefore(" (")} • ₨ $finalFarePKR",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
