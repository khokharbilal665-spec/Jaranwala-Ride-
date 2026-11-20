package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.PromoValidationResult
import com.example.data.ScheduleRideResult
import com.example.data.ScheduledRide
import com.example.data.ScheduledRideStatus
import com.example.util.RideNotificationManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppLanguage
import com.example.data.JaranwalaRepository
import com.example.data.PaymentMethod
import com.example.data.RideBooking
import com.example.data.RideStatus
import com.example.data.VehicleCategory
import com.example.ui.components.FareEstimateCard
import com.example.ui.components.JaranwalaMapCanvas
import com.example.ui.components.JaranwalaOpenStreetMapView
import com.example.ui.components.RideBookingConfirmationDialog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerScreen(
    repository: JaranwalaRepository,
    onNavigateToWedding: ((isBarat: Boolean) -> Unit)? = null,
    onOpenRidesDashboard: (() -> Unit)? = null,
    onOpenHistoryAndPayments: (() -> Unit)? = null,
    onOpenRequestRide: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentRide by repository.currentPassengerRide.collectAsState()
    val history by repository.passengerHistory.collectAsState()
    val scheduledRides by repository.scheduledRides.collectAsState()
    val currentUser by repository.currentUser.collectAsState()
    val appLang by repository.appLanguage.collectAsState()
    val adminConfig by repository.adminConfig.collectAsState()
    val selectedPaymentMethod by repository.selectedPaymentMethod.collectAsState()
    val selectedWalletAccount by repository.selectedWalletAccount.collectAsState()

    var pickup by remember { mutableStateOf("Committee Chowk") }
    var destination by remember { mutableStateOf("Jaranwala Railway Station") }
    var locationSearchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(VehicleCategory.RICKSHAW) }
    var passengerName by remember { mutableStateOf("Ahmed Raza") }
    var passengerPhone by remember { mutableStateOf("0301-8765432") }

    androidx.compose.runtime.LaunchedEffect(currentUser?.uid, currentUser?.name, currentUser?.phoneNumber) {
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

    val savedPlaces by repository.savedPlaces.collectAsState()

    var promoInput by remember { mutableStateOf("") }
    var appliedPromoCode by remember { mutableStateOf<String?>(null) }
    var promoDiscountAmount by remember { mutableIntStateOf(0) }
    var promoMessage by remember { mutableStateOf<String?>(null) }
    var isPromoSuccess by remember { mutableStateOf(false) }

    var showRatingDialog by remember { mutableStateOf(false) }
    var showComplaintDialog by remember { mutableStateOf(false) }
    var showBookingConfirmDialog by remember { mutableStateOf(false) }
    var complaintRideId by remember { mutableStateOf("") }
    var useOpenStreetMap by remember { mutableStateOf(true) }

    // Schedule for Later states (default to +2 hours in future)
    var isScheduledMode by remember { mutableStateOf(false) }
    var scheduledForMillis by remember { mutableLongStateOf(System.currentTimeMillis() + 2 * 3600_000L) }
    var scheduleNotes by remember { mutableStateOf("") }
    var scheduleErrorMessage by remember { mutableStateOf<String?>(null) }
    var scheduleSuccessBanner by remember { mutableStateOf<String?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    // Pickup & Delivery (Parcel & COD) states
    var isParcelMode by remember { mutableStateOf(false) }
    var parcelDescription by remember { mutableStateOf("") }
    var parcelWeightKg by remember { mutableStateOf("Up to 5 kg") }
    var recipientName by remember { mutableStateOf("") }
    var recipientPhone by remember { mutableStateOf("") }
    var parcelCodText by remember { mutableStateOf("") }
    var parcelErrorMessage by remember { mutableStateOf<String?>(null) }

    val scheduleDateFmt = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()) }
    val scheduleTimeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val scheduledDateDisplay = remember(scheduledForMillis) { scheduleDateFmt.format(Date(scheduledForMillis)) }
    val scheduledTimeDisplay = remember(scheduledForMillis) { scheduleTimeFmt.format(Date(scheduledForMillis)) }

    var distanceResult by remember(pickup, destination) {
        mutableStateOf(repository.getInstantDistanceEstimate(pickup, destination))
    }
    var isCalculatingDistance by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(pickup, destination) {
        if (pickup.isNotBlank() && destination.isNotBlank()) {
            isCalculatingDistance = true
            delay(350)
            distanceResult = repository.fetchLocationDistanceEstimate(pickup, destination)
            isCalculatingDistance = false
        }
    }

    val estimatedDist = distanceResult.distanceKm
    val priceBreakdown = remember(selectedCategory, distanceResult, adminConfig, appliedPromoCode, promoDiscountAmount) {
        repository.calculatePriceBreakdown(
            category = selectedCategory,
            distanceResult = distanceResult,
            promoCode = appliedPromoCode,
            promoDiscountPKR = promoDiscountAmount
        )
    }
    val rawFare = priceBreakdown.roundedSubtotalPKR
    val finalFare = priceBreakdown.finalEstimatedFarePKR

    var showDedicatedRequestScreen by remember { mutableStateOf(false) }
    var showPaymentSelectionScreen by remember { mutableStateOf(false) }

    if (showPaymentSelectionScreen) {
        PaymentSelectionScreen(
            repository = repository,
            fareAmountPKR = finalFare,
            onBack = { showPaymentSelectionScreen = false },
            onPaymentConfirmed = { showPaymentSelectionScreen = false }
        )
        return
    }

    if (showDedicatedRequestScreen) {
        RideHailingRequestScreen(
            repository = repository,
            initialPickup = pickup,
            initialDestination = destination,
            onNavigateBack = { showDedicatedRequestScreen = false },
            onRideRequested = { showDedicatedRequestScreen = false }
        )
        return
    }

    // Attach real-time Firestore listener for active ride progress & GPS coordinates
    androidx.compose.runtime.DisposableEffect(currentRide?.id) {
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

    // Attach real-time Firestore listener for passenger's scheduled_rides
    androidx.compose.runtime.DisposableEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: ""
        val reg = if (uid.isNotBlank() && currentUser?.isGuest != true) {
            repository.listenToScheduledRidesFromFirestore(uid)
        } else {
            null
        }
        onDispose {
            reg?.remove()
        }
    }

    // Trigger Android Push Notification when ride status updates
    androidx.compose.runtime.LaunchedEffect(currentRide?.id, currentRide?.status) {
        val ride = currentRide
        if (ride != null && ride.status != RideStatus.IDLE && ride.status != RideStatus.SEARCHING) {
            RideNotificationManager.notifyRideStatusChange(context, ride)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("passenger_lazy_column"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Banner with Official Jaranwala Ride Logo
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("passenger_header_banner")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF064E3B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_jaranwala_ride_logo),
                            contentDescription = "Jaranwala Ride Official Logo",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (appLang == AppLanguage.URDU) "جرانوالہ میں کہیں بھی سفر کریں" else "Book a Ride in Jaranwala",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = if (appLang == AppLanguage.URDU) "موٹر سائیکل، چنگ چی رکشہ اور اے سی کار (₨ ریٹس)" else "Bike, Auto Rickshaw & AC Car with PKR rates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Live Jaranwala",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // 4 Primary Services Grid: Bike Ride, Rickshaw Ride, Car Ride, Marriage / Wedding Transport Service
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("services_grid"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (appLang == AppLanguage.URDU) "ہماری سروسز (Select Service):" else "Available Ride Services:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Bike Ride
                        val isBike = selectedCategory == VehicleCategory.BIKE
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    if (isBike) 2.dp else 1.dp,
                                    if (isBike) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedCategory = VehicleCategory.BIKE }
                                .testTag("service_option_bike"),
                            color = if (isBike) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBike,
                                    contentDescription = "Bike Ride",
                                    tint = if (isBike) MaterialTheme.colorScheme.primary else Color(0xFF10B981),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Bike Ride", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("موٹر سائیکل", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // 2. Rickshaw Ride
                        val isRickshaw = selectedCategory == VehicleCategory.RICKSHAW
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    if (isRickshaw) 2.dp else 1.dp,
                                    if (isRickshaw) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedCategory = VehicleCategory.RICKSHAW }
                                .testTag("service_option_rickshaw"),
                            color = if (isRickshaw) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ElectricRickshaw,
                                    contentDescription = "Rickshaw Ride",
                                    tint = if (isRickshaw) MaterialTheme.colorScheme.primary else Color(0xFFF59E0B),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Rickshaw", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("چنگ چی / آٹو", fontSize = 9.sp, color = Color(0xFFF59E0B))
                            }
                        }

                        // 3. Car Ride
                        val isCar = selectedCategory == VehicleCategory.CAR
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    if (isCar) 2.dp else 1.dp,
                                    if (isCar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedCategory = VehicleCategory.CAR }
                                .testTag("service_option_car"),
                            color = if (isCar) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = "Car Ride",
                                    tint = if (isCar) MaterialTheme.colorScheme.primary else Color(0xFF3B82F6),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Car Ride", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("اے سی کار", fontSize = 9.sp, color = Color(0xFF3B82F6))
                            }
                        }

                        // 4. Marriage / Wedding Transport
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    Color(0xFFD97706).copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onNavigateToWedding?.invoke(false) }
                                .testTag("service_option_wedding"),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Wedding Transport",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Wedding", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                                Text("شادی سروس", fontSize = 9.sp, color = Color(0xFFB45309))
                            }
                        }
                    }

                    // 5. Pickup & Delivery (Parcel & Cash on Delivery)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                if (isParcelMode) 2.dp else 1.dp,
                                if (isParcelMode) Color(0xFF059669) else Color(0xFF10B981).copy(alpha = 0.45f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                isParcelMode = !isParcelMode
                                parcelErrorMessage = null
                                if (isParcelMode) {
                                    isScheduledMode = false
                                }
                            }
                            .testTag("service_option_parcel"),
                        color = if (isParcelMode) Color(0xFFD1FAE5) else Color(0xFFECFDF5)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.LocalShipping,
                                    contentDescription = "Pickup and Delivery Parcel",
                                    tint = Color(0xFF047857),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (appLang == AppLanguage.URDU) "پک اپ اور پارسل ڈلیوری (COD)" else "Pickup & Delivery (Parcel & COD)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF064E3B)
                                    )
                                    Text(
                                        text = if (appLang == AppLanguage.URDU) "دستاویزات، دوائیں و سامان جرانوالہ بھر میں بھجوائیں" else "Send parcels, groceries or medicines across Jaranwala",
                                        fontSize = 10.sp,
                                        color = Color(0xFF047857)
                                    )
                                }
                            }
                            Surface(
                                color = if (isParcelMode) Color(0xFF047857) else Color(0xFFA7F3D0),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isParcelMode) "Selected" else "Select",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isParcelMode) Color.White else Color(0xFF064E3B),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick button to open Dedicated Ride-Hailing Request Screen
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        if (onOpenRequestRide != null) {
                            onOpenRequestRide()
                        } else {
                            showDedicatedRequestScreen = true
                        }
                    }
                    .testTag("btn_open_dedicated_request_screen"),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (appLang == AppLanguage.URDU) "سواری طلب کریں (مقام و نقشہ اسکرین)" else "Ride-Hailing Request Screen",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = if (appLang == AppLanguage.URDU) "موجودہ GPS لوکیشن، نقشہ اور منزل منتخب کریں" else "Interactive Map, Live GPS & Fast Destination Picker",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.NearMe,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Quick Shortcut to Ride History & Payments Screen
        if (onOpenHistoryAndPayments != null) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenHistoryAndPayments() }
                        .testTag("btn_open_ride_history_payments"),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF064E3B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = "Ride History and Payments",
                                    tint = Color(0xFF6EE7B7),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "سفر کا ریکارڈ اور ادائیگیاں" else "Ride History & Payments",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "مکمل سواریاں، کرایہ اور رسیدیں دیکھیں" else "View completed trips, PKR fares & cash receipts",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = "Open Receipts",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Live Map of Jaranwala & Pakistan (OpenStreetMap osmdroid & Vector Canvas)
        item {
            if (useOpenStreetMap) {
                JaranwalaOpenStreetMapView(
                    pickupText = pickup,
                    destinationText = destination,
                    rideStatus = currentRide?.status ?: RideStatus.IDLE,
                    category = selectedCategory,
                    activeRide = currentRide,
                    onPickupChange = { pickup = it },
                    onDestinationChange = { destination = it },
                    onSwitchToCanvas = { useOpenStreetMap = false }
                )
            } else {
                JaranwalaMapCanvas(
                    pickupText = pickup,
                    destinationText = destination,
                    rideStatus = currentRide?.status ?: RideStatus.IDLE,
                    category = selectedCategory,
                    activeRide = currentRide,
                    onPickupChange = { pickup = it },
                    onDestinationChange = { destination = it },
                    onSwitchToOpenStreetMap = { useOpenStreetMap = true },
                    onSwitchToGoogleMaps = { useOpenStreetMap = true }
                )
            }
        }

        // Dedicated Trip Summary & Post-Ride Card (Visible when Ride is Finished)
        if (currentRide != null && currentRide!!.status == RideStatus.COMPLETED) {
            item {
                com.example.ui.components.TripSummaryCard(
                    ride = currentRide!!,
                    repository = repository,
                    appLang = appLang,
                    onDismiss = { repository.dismissCompletedRide() },
                    onViewHistory = onOpenRidesDashboard
                )
            }
        }

        // Real-Time Ride Status Card (Tracks 'Searching', 'Driver Assigned', 'Arrived', 'In Progress', 'Cancelled')
        if (currentRide != null && currentRide!!.status != RideStatus.COMPLETED) {
            item {
                com.example.ui.components.RealTimeRideStatusCard(
                    ride = currentRide!!,
                    appLang = appLang,
                    onCancel = { repository.cancelRide() },
                    onStartTrip = { repository.startTrip() },
                    onCompleteTrip = {
                        repository.completeTrip()
                        showRatingDialog = true
                    },
                    onDismissCompleted = { repository.dismissCompletedRide() },
                    onCallCaptain = { phone ->
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    onWhatsAppCaptain = { phone ->
                        try {
                            val cleanPhone = phone.replace("-", "").replace(" ", "").replace("+", "")
                            val intPhone = if (cleanPhone.startsWith("0")) "92" + cleanPhone.drop(1) else cleanPhone
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$intPhone"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )
            }
        }

        // Location Selector Card (visible when no ride is in progress)
        if (currentRide == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (appLang == AppLanguage.URDU) "کہاں جانا چاہتے ہیں؟" else "Where are you going?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Pakistan Location Search Box
                        OutlinedTextField(
                            value = locationSearchQuery,
                            onValueChange = {
                                locationSearchQuery = it
                                if (it.isNotBlank()) destination = it
                            },
                            label = { Text(if (appLang == AppLanguage.URDU) "پاکستان بھر میں مقام تلاش کریں" else "Search any location / city in Pakistan") },
                            placeholder = { Text("e.g. Faisalabad, Lahore, Buchiana, Syedwala...", color = Color.Gray, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search Locations", tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_locations_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Pickup Input
                        OutlinedTextField(
                            value = pickup,
                            onValueChange = { pickup = it },
                            label = { Text(if (appLang == AppLanguage.URDU) "پک اپ پوائنٹ (Pickup)" else "Pickup Location") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pickup_location_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Destination Input
                        OutlinedTextField(
                            value = destination,
                            onValueChange = { destination = it },
                            label = { Text(if (appLang == AppLanguage.URDU) "منزل کا پتہ (Destination)" else "Destination in Jaranwala") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("destination_location_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Quick Saved Places Chips for Faster Booking
                        if (savedPlaces.isNotEmpty()) {
                            Text(
                                text = if (appLang == AppLanguage.URDU) "محفوظ مقامات (Saved Places):" else "Saved Places (1-Tap Select):",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(savedPlaces) { index, place ->
                                    FilterChip(
                                        selected = destination == place.address,
                                        onClick = { destination = place.address },
                                        leadingIcon = {
                                            val icon = when (place.iconType.uppercase()) {
                                                "HOME" -> Icons.Default.Home
                                                "WORK" -> Icons.Default.Work
                                                "COLLEGE" -> Icons.Default.School
                                                else -> Icons.Default.Place
                                            }
                                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        label = {
                                            Text(
                                                text = if (appLang == AppLanguage.URDU) "${place.label} (${place.urduLabel})" else place.label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        modifier = Modifier.testTag("quick_saved_place_$index"),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        // Quick Chips for Popular Jaranwala Spots
                        Text(
                            text = if (appLang == AppLanguage.URDU) "مشہور مقامات (Quick Select):" else "Popular Jaranwala Landmarks:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(repository.jaranwalaLocations.take(7)) { loc ->
                                FilterChip(
                                    selected = destination == loc.name,
                                    onClick = { destination = loc.name },
                                    label = {
                                        Text(
                                            text = if (appLang == AppLanguage.URDU) loc.urduName else loc.name,
                                            fontSize = 12.sp
                                        )
                                    },
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

            // Transparent Upfront Fare Estimation & Itemized Breakdown Card
            item {
                FareEstimateCard(
                    distanceKm = estimatedDist,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    adminConfig = adminConfig,
                    appLang = appLang,
                    pickup = pickup,
                    destination = destination,
                    promoDiscountPKR = promoDiscountAmount,
                    appliedPromoCode = appliedPromoCode,
                    distanceSource = distanceResult.source,
                    apiDurationMinutes = distanceResult.estimatedDurationMinutes,
                    isCalculatingDistance = isCalculatingDistance
                )
            }

            // Vehicle Category Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (appLang == AppLanguage.URDU) "گاڑی کی کیٹیگری تبدیل کریں" else "Vehicle Category Selection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    VehicleCategory.values().forEach { category ->
                        val fare = repository.calculateFare(category, estimatedDist)
                        val isSelected = selectedCategory == category

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedCategory = category }
                                .testTag("ride_option_${category.name.lowercase()}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = category.icon,
                                        contentDescription = category.title,
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = category.title,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${category.etaMinutes} mins",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${category.urduTitle} • ${category.capacity} seats",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "₨ $fare",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "$estimatedDist km",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Promo Code Input & Validation Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("promo_code_card"),
                    shape = RoundedCornerShape(16.dp),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "پرومو کوڈ (ڈسکاؤنٹ حاصل کریں)" else "Promo Code / ڈسکاؤنٹ",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (appliedPromoCode != null) {
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "- ₨ $promoDiscountAmount",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF15803D),
                                        modifier = Modifier
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .testTag("promo_discount_amount")
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
                                value = promoInput,
                                onValueChange = {
                                    promoInput = it.uppercase()
                                    promoMessage = null
                                },
                                placeholder = { Text("e.g. MAHI, FIRST, JARANWALA", fontSize = 12.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("promo_code_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )

                            if (appliedPromoCode == null) {
                                Button(
                                    onClick = {
                                        val result = repository.validatePromoCode(promoInput, rawFare)
                                        when (result) {
                                            is PromoValidationResult.Success -> {
                                                appliedPromoCode = result.promo.code
                                                promoDiscountAmount = result.discountAmountPKR
                                                promoMessage = if (appLang == AppLanguage.URDU) result.urduMessage else result.message
                                                isPromoSuccess = true
                                            }
                                            is PromoValidationResult.Error -> {
                                                appliedPromoCode = null
                                                promoDiscountAmount = 0
                                                promoMessage = if (appLang == AppLanguage.URDU) result.urduMessage else result.message
                                                isPromoSuccess = false
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("btn_apply_promo")
                                ) {
                                    Text(if (appLang == AppLanguage.URDU) "لاگو کریں" else "Apply")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        appliedPromoCode = null
                                        promoDiscountAmount = 0
                                        promoInput = ""
                                        promoMessage = if (appLang == AppLanguage.URDU) "پرومو کوڈ ہٹا دیا گیا" else "Promo code removed"
                                        isPromoSuccess = false
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("btn_remove_promo")
                                ) {
                                    Text(if (appLang == AppLanguage.URDU) "ہٹائیں" else "Remove")
                                }
                            }
                        }

                        // Available Promo Quick Suggestion Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Coupons:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            repository.availablePromos.forEach { promo ->
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            promoInput = promo.code
                                            val result = repository.validatePromoCode(promo.code, rawFare)
                                            if (result is PromoValidationResult.Success) {
                                                appliedPromoCode = result.promo.code
                                                promoDiscountAmount = result.discountAmountPKR
                                                promoMessage = if (appLang == AppLanguage.URDU) result.urduMessage else result.message
                                                isPromoSuccess = true
                                            }
                                        }
                                        .testTag("promo_chip_${promo.code.lowercase()}"),
                                    color = if (appliedPromoCode == promo.code) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${promo.code} (${if (promo.flatDiscountPKR > 0) "₨${promo.flatDiscountPKR}" else "${promo.discountPercent}%"})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (appliedPromoCode == promo.code) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // Promo Status / Feedback Message
                        if (promoMessage != null) {
                            Surface(
                                color = if (isPromoSuccess) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("promo_status_message")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isPromoSuccess) "✓ " else "⚠ ",
                                        color = if (isPromoSuccess) Color(0xFF15803D) else Color(0xFFB91C1C),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = promoMessage!!,
                                        color = if (isPromoSuccess) Color(0xFF15803D) else Color(0xFFB91C1C),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Booking Mode Toggle: Ride Now vs Schedule for Later + Date/Time Picker
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("schedule_for_later_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isScheduledMode) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !isScheduledMode,
                                onClick = {
                                    isScheduledMode = false
                                    scheduleErrorMessage = null
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = {
                                    Text(
                                        text = if (appLang == AppLanguage.URDU) "فوری سواری (Now)" else "Ride Now",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("booking_mode_now")
                            )
                            FilterChip(
                                selected = isScheduledMode,
                                onClick = {
                                    isScheduledMode = true
                                    isParcelMode = false
                                    scheduleErrorMessage = null
                                    if (scheduledForMillis <= System.currentTimeMillis() + 60_000L) {
                                        scheduledForMillis = System.currentTimeMillis() + 2 * 3600_000L
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = {
                                    Text(
                                        text = if (appLang == AppLanguage.URDU) "بعد کیلئے شیڈول" else "Schedule Later",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("booking_mode_schedule"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFEF3C7),
                                    selectedLabelColor = Color(0xFF92400E)
                                )
                            )
                        }

                        if (isScheduledMode) {
                            Text(
                                text = if (appLang == AppLanguage.URDU)
                                    "مستقبل کی تاریخ اور وقت منتخب کریں (Firestore scheduled_rides):"
                                else
                                    "Pick Future Pickup Date & Time:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showDatePickerDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("schedule_date_btn")
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Select Date", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = scheduledDateDisplay,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("selected_schedule_date_text")
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showTimePickerDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(0.85f)
                                        .height(48.dp)
                                        .testTag("schedule_time_btn")
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = "Select Time", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = scheduledTimeDisplay,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("selected_schedule_time_text")
                                    )
                                }
                            }

                            // Quick Future Slot Chips
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            scheduledForMillis = System.currentTimeMillis() + 3600_000L
                                            scheduleErrorMessage = null
                                        },
                                        label = { Text("+1 Hour", fontSize = 11.sp) },
                                        modifier = Modifier.testTag("schedule_preset_1hr")
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            val cal = Calendar.getInstance().apply {
                                                add(Calendar.DAY_OF_YEAR, 1)
                                                set(Calendar.HOUR_OF_DAY, 9)
                                                set(Calendar.MINUTE, 0)
                                                set(Calendar.SECOND, 0)
                                            }
                                            scheduledForMillis = cal.timeInMillis
                                            scheduleErrorMessage = null
                                        },
                                        label = { Text("Tomorrow 9:00 AM", fontSize = 11.sp) },
                                        modifier = Modifier.testTag("schedule_preset_tomorrow_morning")
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            val cal = Calendar.getInstance().apply {
                                                add(Calendar.DAY_OF_YEAR, 1)
                                                set(Calendar.HOUR_OF_DAY, 17)
                                                set(Calendar.MINUTE, 0)
                                                set(Calendar.SECOND, 0)
                                            }
                                            scheduledForMillis = cal.timeInMillis
                                            scheduleErrorMessage = null
                                        },
                                        label = { Text("Tomorrow 5:00 PM", fontSize = 11.sp) },
                                        modifier = Modifier.testTag("schedule_preset_tomorrow_evening")
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = scheduleNotes,
                                onValueChange = { scheduleNotes = it },
                                label = {
                                    Text(
                                        if (appLang == AppLanguage.URDU) "کپتان کیلئے نوٹ (اختیاری)" else "Pickup Note (Optional)",
                                        fontSize = 12.sp
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("schedule_notes_input")
                            )

                            if (scheduleErrorMessage != null) {
                                Surface(
                                    color = Color(0xFFFEE2E2),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("schedule_validation_error")
                                ) {
                                    Text(
                                        text = scheduleErrorMessage!!,
                                        color = Color(0xFFB91C1C),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }

                        if (scheduleSuccessBanner != null) {
                            Surface(
                                color = Color(0xFFDCFCE7),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scheduled_ride_success_banner")
                            ) {
                                Text(
                                    text = scheduleSuccessBanner!!,
                                    color = Color(0xFF15803D),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Parcel & Cash on Delivery Details Form (Visible when Pickup & Delivery mode selected)
            if (isParcelMode) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parcel_details_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF10B981))
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocalShipping,
                                        contentDescription = null,
                                        tint = Color(0xFF047857),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (appLang == AppLanguage.URDU) "پارسل اور وصول کنندہ کی تفصیلات" else "Parcel & Recipient Details (COD)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(onClick = { isParcelMode = false }) {
                                    Text("Cancel Parcel", fontSize = 11.sp, color = Color(0xFFDC2626))
                                }
                            }

                            OutlinedTextField(
                                value = recipientName,
                                onValueChange = {
                                    recipientName = it
                                    parcelErrorMessage = null
                                },
                                label = { Text(if (appLang == AppLanguage.URDU) "وصول کنندہ کا نام" else "Recipient Full Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("parcel_recipient_name_input")
                            )

                            OutlinedTextField(
                                value = recipientPhone,
                                onValueChange = {
                                    recipientPhone = it
                                    parcelErrorMessage = null
                                },
                                label = { Text(if (appLang == AppLanguage.URDU) "وصول کنندہ کا موبائل نمبر" else "Recipient Mobile (e.g. 0300-1234567)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("parcel_recipient_phone_input")
                            )

                            OutlinedTextField(
                                value = parcelDescription,
                                onValueChange = {
                                    parcelDescription = it
                                    parcelErrorMessage = null
                                },
                                label = { Text(if (appLang == AppLanguage.URDU) "پارسل کی تفصیل" else "Parcel Contents (e.g. Documents, Food)") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("parcel_description_input")
                            )

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf("Up to 2 kg", "Up to 5 kg", "5 - 15 kg")) { weightOption ->
                                    FilterChip(
                                        selected = parcelWeightKg == weightOption,
                                        onClick = { parcelWeightKg = weightOption },
                                        label = { Text(weightOption, fontSize = 11.sp) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = parcelCodText,
                                onValueChange = { input ->
                                    parcelCodText = input.filter { ch -> ch.isDigit() }
                                },
                                label = { Text(if (appLang == AppLanguage.URDU) "کیش آن ڈلیوری رقم (اختیاری ₨)" else "Cash on Delivery Amount (Optional ₨)") },
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("parcel_cod_input")
                            )

                            if (parcelErrorMessage != null) {
                                Surface(
                                    color = Color(0xFFFEE2E2),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("parcel_validation_error")
                                ) {
                                    Text(
                                        text = parcelErrorMessage!!,
                                        color = Color(0xFFB91C1C),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Payment Method Selector Card (Cash on Drop / JazzCash / Easypaisa)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showPaymentSelectionScreen = true }
                        .testTag("payment_method_selector_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (selectedPaymentMethod) {
                            PaymentMethod.CASH -> Color(0xFF10B981).copy(alpha = 0.45f)
                            PaymentMethod.JAZZCASH -> Color(0xFFDC2626).copy(alpha = 0.55f)
                            PaymentMethod.EASYPAISA -> Color(0xFF16A34A).copy(alpha = 0.55f)
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when (selectedPaymentMethod) {
                                            PaymentMethod.CASH -> Color(0xFF064E3B)
                                            PaymentMethod.JAZZCASH -> Color(0xFFB91C1C)
                                            PaymentMethod.EASYPAISA -> Color(0xFF15803D)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = "Payment Method",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "ادائیگی کا طریقہ (Payment)" else "Payment Method",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (appLang == AppLanguage.URDU) selectedPaymentMethod.urduTitle else selectedPaymentMethod.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("selected_payment_method_text")
                                )
                                if (selectedPaymentMethod != PaymentMethod.CASH && selectedWalletAccount.isNotBlank()) {
                                    Text(
                                        text = "Account: $selectedWalletAccount",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_open_payment_selection")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Change Payment Method",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "تبدیل کریں" else "Select",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Book Ride / Schedule Ride / Parcel Button — Opens Pre-Booking Price & Route Confirmation before dispatching
            item {
                Button(
                    onClick = {
                        when {
                            isParcelMode && (recipientName.isBlank() || recipientPhone.trim().length < 7 || parcelDescription.isBlank()) -> {
                                parcelErrorMessage = if (appLang == AppLanguage.URDU)
                                    "براہِ کرم وصول کنندہ کا نام، موبائل نمبر اور پارسل کی تفصیل درج کریں۔"
                                else
                                    "Please enter recipient name, phone number, and parcel description."
                            }
                            isScheduledMode && scheduledForMillis <= System.currentTimeMillis() + 60_000L -> {
                                scheduleErrorMessage = if (appLang == AppLanguage.URDU)
                                    "براہِ کرم مستقبل کی درست تاریخ اور وقت منتخب کریں"
                                else
                                    "Please select a future date & time at least 1 minute ahead."
                            }
                            else -> {
                                parcelErrorMessage = null
                                scheduleErrorMessage = null
                                showBookingConfirmDialog = true
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("book_ride_btn"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isParcelMode -> Color(0xFF047857)
                            isScheduledMode -> Color(0xFFD97706)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Icon(
                        imageVector = when {
                            isParcelMode -> Icons.Default.LocalShipping
                            isScheduledMode -> Icons.Default.Schedule
                            else -> Icons.Default.DirectionsCar
                        },
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isParcelMode -> if (appLang == AppLanguage.URDU) "پارسل روانہ کریں (₨ $finalFare)" else "Dispatch Parcel (${selectedCategory.title}) • ₨ $finalFare"
                            isScheduledMode -> if (appLang == AppLanguage.URDU) "شیڈول سواری بک کریں (₨ $finalFare)" else "Schedule ${selectedCategory.title} • ₨ $finalFare"
                            else -> if (appLang == AppLanguage.URDU) "سواری بک کریں (₨ $finalFare)" else "Book ${selectedCategory.title} • ₨ $finalFare"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Upcoming Scheduled Rides List (from Firestore 'scheduled_rides' collection)
            if (scheduledRides.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scheduled_rides_section"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (appLang == AppLanguage.URDU) "آپ کی شیڈول سواریاں" else "My Scheduled Rides",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Surface(
                                    color = Color(0xFF065F46),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${scheduledRides.size} Cloud",
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            scheduledRides.forEach { sr ->
                                ScheduledRideItemCard(
                                    scheduledRide = sr,
                                    isUrdu = appLang == AppLanguage.URDU,
                                    onCancel = { repository.cancelScheduledRide(sr.id) }
                                )
                            }
                        }
                    }
                }
            }

            // 4. Marriage Transport & 5. Barat Transport Services Cards
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (appLang == AppLanguage.URDU) "خصوصی شادی و بارات سروسز (Specialized Fleet)" else "Specialized Wedding & Barat Fleet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // 4. Marriage Transport Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFD97706).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .clickable { onNavigateToWedding?.invoke(false) }
                            .testTag("service_card_marriage_transport"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFFFEF3C7), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Marriage Transport",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "شادی ٹرانسپورٹ (Marriage Transport)" else "Marriage Transport (Bridal Car)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "پراڈو، فارچونر، سیوک و گرینڈے مع تازہ پھول و ڈرائیور" else "Decorated Prado TX, Fortuner, Civic RS with chauffeur",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onNavigateToWedding?.invoke(false) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                modifier = Modifier.height(34.dp).testTag("btn_book_marriage_transport"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(if (appLang == AppLanguage.URDU) "بک کریں" else "Book", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 5. Barat Transport Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFF6D28D9).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .clickable { onNavigateToWedding?.invoke(true) }
                            .testTag("service_card_barat_transport"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFFEDE9FE), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Diversity3,
                                    contentDescription = "Barat Transport",
                                    tint = Color(0xFF6D28D9),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "بارات ٹرانسپورٹ (Barat Convoy Fleet)" else "Barat Transport (Convoy Fleet)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "2 تا 25 گاڑیوں کا باضابطہ بیڑا برائے باراتی و مہمانان" else "Multi-car convoy fleet (2 to 25 cars) for wedding processions",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onNavigateToWedding?.invoke(true) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D28D9)),
                                modifier = Modifier.height(34.dp).testTag("btn_book_barat_transport"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(if (appLang == AppLanguage.URDU) "قافلہ بک کریں" else "Book Fleet", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Ride History Section (Fetched from Firestore with Re-Book shortcut & Filters)
        item {
            com.example.ui.components.RideHistorySection(
                repository = repository,
                appLang = appLang,
                onRebookRide = { rPickup, rDest, rCat ->
                    pickup = rPickup
                    destination = rDest
                    selectedCategory = rCat
                },
                onFileComplaint = { rId ->
                    complaintRideId = rId
                    showComplaintDialog = true
                },
                onOpenRidesDashboard = onOpenRidesDashboard
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Rating Dialog
    if (showRatingDialog && currentRide != null) {
        com.example.ui.components.DriverRatingDialog(
            ride = currentRide!!,
            repository = repository,
            appLang = appLang,
            onDismiss = {
                showRatingDialog = false
                repository.dismissCompletedRide()
            },
            onRatingSubmitted = { _, _ ->
                showRatingDialog = false
                repository.dismissCompletedRide()
            }
        )
    } else if (showRatingDialog) {
        var stars by remember { mutableIntStateOf(5) }
        var reviewText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = {
                showRatingDialog = false
                repository.dismissCompletedRide()
            },
            title = {
                Text(
                    text = if (appLang == AppLanguage.URDU) "سواری کا تجربہ کیسا رہا؟" else "Rate Your Jaranwala Captain",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (appLang == AppLanguage.URDU) "کپتان کو ستارے دیں اور اپنی رائے لکھیں" else "Give stars and feedback for your driver",
                        fontSize = 13.sp
                    )
                    Row {
                        (1..5).forEach { index ->
                            IconButton(
                                onClick = { stars = index },
                                modifier = Modifier.testTag("star_rating_$index")
                            ) {
                                Icon(
                                    imageVector = if (index <= stars) Icons.Filled.Star else Icons.Outlined.Star,
                                    contentDescription = "$index Stars",
                                    tint = if (index <= stars) Color(0xFFF59E0B) else Color.Gray,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = reviewText,
                        onValueChange = { reviewText = it },
                        label = { Text("Comment (اختیاری رائے)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.submitRating(stars, reviewText)
                        showRatingDialog = false
                    },
                    modifier = Modifier.testTag("submit_rating_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Submit Feedback")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showRatingDialog = false
                        repository.dismissCompletedRide()
                    }
                ) {
                    Text("Skip")
                }
            }
        )
    }

    // Complaint Dialog
    if (showComplaintDialog) {
        var complaintCategory by remember { mutableStateOf("Overcharging / Fare Issue") }
        var complaintMsg by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showComplaintDialog = false },
            title = { Text("File a Complaint (شکایت درج کریں)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ride ID: $complaintRideId",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val categories = listOf("Overcharging", "Rash Driving", "Rude Behavior", "Route Delay", "Lost Item")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = complaintCategory == cat,
                                onClick = { complaintCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = complaintMsg,
                        onValueChange = { complaintMsg = it },
                        label = { Text("Describe what happened...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("complaint_message_input"),
                        minLines = 3,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.submitComplaint(
                            rideId = complaintRideId,
                            name = passengerName,
                            category = complaintCategory,
                            urduCat = "شکایت",
                            msg = complaintMsg
                        )
                        showComplaintDialog = false
                    },
                    modifier = Modifier.testTag("submit_complaint_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Submit Ticket")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showComplaintDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Pre-Booking Fare & Route Confirmation Dialog
    if (showBookingConfirmDialog) {
        val parsedCodPKR = parcelCodText.toIntOrNull() ?: 0
        RideBookingConfirmationDialog(
            pickup = pickup,
            destination = destination,
            breakdown = priceBreakdown,
            appLang = appLang,
            scheduledDateTimeDisplay = if (isScheduledMode) "$scheduledDateDisplay • $scheduledTimeDisplay" else null,
            isParcelDelivery = isParcelMode,
            parcelDescription = parcelDescription,
            recipientName = recipientName,
            recipientPhone = recipientPhone,
            codAmountPKR = parsedCodPKR,
            paymentMethod = selectedPaymentMethod,
            onConfirmBooking = {
                showBookingConfirmDialog = false
                if (isScheduledMode) {
                    val res = repository.scheduleRideForLater(
                        passengerName = passengerName,
                        passengerPhone = passengerPhone,
                        pickup = pickup,
                        destination = destination,
                        category = selectedCategory,
                        scheduledForMillis = scheduledForMillis,
                        scheduledDateDisplay = scheduledDateDisplay,
                        scheduledTimeDisplay = scheduledTimeDisplay,
                        notes = scheduleNotes,
                        promoCode = appliedPromoCode,
                        discountPKR = promoDiscountAmount,
                        overrideDistanceKm = estimatedDist,
                        overrideFarePKR = finalFare,
                        paymentMethod = selectedPaymentMethod.firestoreValue
                    )
                    when (res) {
                        is ScheduleRideResult.Success -> {
                            scheduleErrorMessage = null
                            scheduleSuccessBanner = if (appLang == AppLanguage.URDU)
                                "✓ سواری ${res.scheduledRide.scheduledDateDisplay} کو ${res.scheduledRide.scheduledTimeDisplay} کیلئے شیڈول ہو گئی! (₨ ${res.scheduledRide.estimatedFarePKR})"
                            else
                                "✓ Scheduled ${selectedCategory.title} for $scheduledDateDisplay at $scheduledTimeDisplay (₨ $finalFare)"
                        }
                        is ScheduleRideResult.Error -> {
                            scheduleErrorMessage = if (appLang == AppLanguage.URDU) res.urduMessage else res.message
                        }
                    }
                } else {
                    repository.requestRide(
                        passengerName = passengerName,
                        passengerPhone = passengerPhone,
                        pickup = pickup,
                        destination = destination,
                        category = selectedCategory,
                        promoCode = appliedPromoCode,
                        discountPKR = promoDiscountAmount,
                        overrideDistanceKm = estimatedDist,
                        overrideFarePKR = finalFare,
                        isParcelDelivery = isParcelMode,
                        parcelDescription = parcelDescription,
                        parcelWeightKg = parcelWeightKg,
                        recipientName = recipientName,
                        recipientPhone = recipientPhone,
                        codAmountPKR = parsedCodPKR,
                        paymentMethod = selectedPaymentMethod.firestoreValue
                    )
                }
            },
            onDismiss = { showBookingConfirmDialog = false }
        )
    }

    // Material 3 Future Date Picker Dialog
    if (showDatePickerDialog) {
        val todayUtcStart = remember {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = scheduledForMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= todayUtcStart
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pickedUtc = datePickerState.selectedDateMillis
                        if (pickedUtc != null) {
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = pickedUtc
                            }
                            val currentCal = Calendar.getInstance().apply {
                                timeInMillis = scheduledForMillis
                            }
                            currentCal.set(
                                utcCal.get(Calendar.YEAR),
                                utcCal.get(Calendar.MONTH),
                                utcCal.get(Calendar.DAY_OF_MONTH)
                            )
                            scheduledForMillis = currentCal.timeInMillis
                            scheduleErrorMessage = null
                        }
                        showDatePickerDialog = false
                    },
                    modifier = Modifier.testTag("confirm_schedule_date_btn")
                ) {
                    Text(if (appLang == AppLanguage.URDU) "منتخب کریں" else "Select Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text(if (appLang == AppLanguage.URDU) "منسوخ" else "Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Material 3 Time Picker Dialog
    if (showTimePickerDialog) {
        val initCal = remember(scheduledForMillis) {
            Calendar.getInstance().apply { timeInMillis = scheduledForMillis }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = initCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = initCal.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            title = {
                Text(
                    text = if (appLang == AppLanguage.URDU) "پک اپ کا وقت منتخب کریں" else "Select Pickup Time",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updatedCal = Calendar.getInstance().apply {
                            timeInMillis = scheduledForMillis
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                        }
                        scheduledForMillis = updatedCal.timeInMillis
                        scheduleErrorMessage = if (updatedCal.timeInMillis <= System.currentTimeMillis() + 60_000L) {
                            if (appLang == AppLanguage.URDU)
                                "منتخب کردہ وقت گزر چکا ہے، براہِ کرم آگے کا وقت چنیں"
                            else
                                "Selected time is in the past. Pick a future time."
                        } else null
                        showTimePickerDialog = false
                    },
                    modifier = Modifier.testTag("confirm_schedule_time_btn")
                ) {
                    Text(if (appLang == AppLanguage.URDU) "منتخب کریں" else "Set Time")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text(if (appLang == AppLanguage.URDU) "منسوخ" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun ScheduledRideItemCard(
    scheduledRide: ScheduledRide,
    isUrdu: Boolean,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scheduled_ride_item_${scheduledRide.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${scheduledRide.scheduledDateDisplay} • ${scheduledRide.scheduledTimeDisplay}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "₨ ${scheduledRide.estimatedFarePKR}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "${scheduledRide.pickup} ➔ ${scheduledRide.destination}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (scheduledRide.status == ScheduledRideStatus.SCHEDULED) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${scheduledRide.vehicleCategory.title} • ${if (isUrdu) scheduledRide.status.urduDisplay else scheduledRide.status.display}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (scheduledRide.status == ScheduledRideStatus.SCHEDULED) Color(0xFF15803D) else Color(0xFFB91C1C),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (scheduledRide.status == ScheduledRideStatus.SCHEDULED) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("cancel_schedule_${scheduledRide.id}")
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cancel Schedule", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isUrdu) "منسوخ" else "Cancel", fontSize = 11.sp, color = Color(0xFFEF4444))
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveRideCard(
    ride: RideBooking,
    appLang: AppLanguage,
    onCancel: () -> Unit,
    onStartTrip: () -> Unit,
    onCompleteTrip: () -> Unit,
    onDismissCompleted: () -> Unit,
    onCallDriver: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_ride_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = when (ride.status) {
                        RideStatus.SEARCHING -> Color(0xFFFEF3C7)
                        RideStatus.ACCEPTED, RideStatus.DRIVER_ARRIVED -> Color(0xFFE0E7FF)
                        RideStatus.IN_PROGRESS -> Color(0xFFD1FAE5)
                        RideStatus.COMPLETED -> Color(0xFFDCFCE7)
                        else -> Color(0xFFF1F5F9)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (appLang == AppLanguage.URDU) ride.status.urduDisplay else "${ride.status.display} • ${ride.status.urduDisplay}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (ride.status) {
                            RideStatus.SEARCHING -> Color(0xFFB45309)
                            RideStatus.ACCEPTED, RideStatus.DRIVER_ARRIVED -> Color(0xFF3730A3)
                            RideStatus.IN_PROGRESS -> Color(0xFF047857)
                            else -> Color(0xFF1E293B)
                        },
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("ride_status_text")
                    )
                }

                Text(
                    text = "₨ ${ride.estimatedFarePKR}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (ride.status == RideStatus.SEARCHING) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                )
                Text(
                    text = "Broadcasting live to nearby verified Captains in Jaranwala...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Driver details if assigned
            if (ride.driverName != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = ride.driverName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Verified Captain",
                                        fontSize = 9.sp,
                                        color = Color(0xFF065F46),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Plate: ${ride.vehiclePlate ?: "PB-JRW"} • ★ ${ride.driverRating}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        ride.driverPhone?.let { phone ->
                            IconButton(
                                onClick = { onCallDriver(phone) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .testTag("call_driver_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call Driver",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Live Trip Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (ride.status) {
                    RideStatus.SEARCHING -> {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cancel_ride_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel Request")
                        }
                    }
                    RideStatus.ACCEPTED, RideStatus.DRIVER_ARRIVED -> {
                        Button(
                            onClick = onStartTrip,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("simulate_start_trip_btn"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Start Trip")
                        }
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.testTag("cancel_ride_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel")
                        }
                    }
                    RideStatus.IN_PROGRESS -> {
                        Button(
                            onClick = onCompleteTrip,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("complete_ride_btn"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Complete Ride & Pay Cash")
                        }
                    }
                    RideStatus.COMPLETED -> {
                        Button(
                            onClick = onCompleteTrip,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rate_experience_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Rate Experience")
                        }
                        OutlinedButton(
                            onClick = onDismissCompleted,
                            modifier = Modifier.testTag("dismiss_ride_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Book New Ride")
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
