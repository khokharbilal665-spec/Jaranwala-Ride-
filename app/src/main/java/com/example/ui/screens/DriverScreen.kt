package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.AppLanguage
import com.example.data.DriverRegDetails
import com.example.data.JaranwalaRepository
import com.example.data.RideBooking
import com.example.data.RideStatus
import com.example.data.UserRole
import com.example.data.VehicleCategory
import com.example.util.PakistanFirebasePhoneAuthHelper
import com.example.util.PakistanGeoService
import com.example.util.PakistanPhoneValidator
import com.example.util.findActivity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

private data class PendingCaptainOnboarding(
    val name: String,
    val phoneLocal: String,
    val phoneE164: String,
    val cnic: String,
    val license: String,
    val category: VehicleCategory,
    val model: String,
    val plate: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverScreen(
    repository: JaranwalaRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val driver by repository.activeDriver.collectAsState()
    val currentUser by repository.currentUser.collectAsState()
    val incomingRequests by repository.incomingDriverRequests.collectAsState()
    val incomingReq by repository.incomingDriverRequest.collectAsState()
    val activeRide by repository.currentDriverRide.collectAsState()
    val appLang by repository.appLanguage.collectAsState()
    val isUrdu = appLang == AppLanguage.URDU

    val phoneAuthHelper = remember { PakistanFirebasePhoneAuthHelper(repository = repository) }

    var showRegisterDialog by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var pendingOnboarding by remember { mutableStateOf<PendingCaptainOnboarding?>(null) }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var enteredOtp by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }
    var filterMatchingVehicleOnly by remember { mutableStateOf(false) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var lastBroadcastCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Synchronize real-time Firestore driver approval/online profile & incoming ride requests
    DisposableEffect(driver.id, driver.isOnline, driver.isApproved) {
        val profileListener = repository.listenToDriverProfileFromFirestore(driver.id)
        val requestsListener = if (driver.isOnline && driver.isApproved) {
            repository.listenToIncomingRideRequestsFromFirestore()
        } else {
            null
        }
        onDispose {
            profileListener?.remove()
            requestsListener?.remove()
        }
    }

    // Stream live device GPS coordinates via foreground service & UI listener when Captain is Online & Approved
    DisposableEffect(driver.id, driver.isOnline, driver.isApproved, hasLocationPermission, activeRide?.id) {
        if (!driver.isOnline || !driver.isApproved || !hasLocationPermission) {
            com.example.util.DriverLocationTrackingService.stopTracking(context)
            return@DisposableEffect onDispose {}
        }
        com.example.util.DriverLocationTrackingService.startTracking(context, activeRide?.id)
        val fusedClient = try {
            LocationServices.getFusedLocationProviderClient(context)
        } catch (_: Throwable) {
            null
        }
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                if (PakistanGeoService.isWithinPakistan(loc.latitude, loc.longitude)) {
                    lastBroadcastCoords = loc.latitude to loc.longitude
                    repository.updateDriverLiveLocation(loc.latitude, loc.longitude)
                }
            }
        }
        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateDistanceMeters(10f)
                .build()
            fusedClient?.requestLocationUpdates(req, callback, Looper.getMainLooper())
            fusedClient?.lastLocation?.addOnSuccessListener { loc ->
                if (loc != null && PakistanGeoService.isWithinPakistan(loc.latitude, loc.longitude)) {
                    lastBroadcastCoords = loc.latitude to loc.longitude
                    repository.updateDriverLiveLocation(loc.latitude, loc.longitude)
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Throwable) {}
        onDispose {
            try {
                fusedClient?.removeLocationUpdates(callback)
            } catch (_: Throwable) {}
            if (!driver.isOnline || !driver.isApproved) {
                com.example.util.DriverLocationTrackingService.stopTracking(context)
            }
        }
    }

    val combinedRequests = remember(incomingRequests, incomingReq, filterMatchingVehicleOnly, driver.vehicleCategory) {
        val all = (incomingRequests + listOfNotNull(incomingReq))
            .distinctBy { it.id }
            .filter { it.status == RideStatus.SEARCHING }
        if (filterMatchingVehicleOnly) {
            all.filter { it.vehicleCategory == driver.vehicleCategory }
        } else {
            all
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("driver_dashboard_list"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Dedicated Captain Availability Status Card
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("driver_status_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = if (driver.isOnline && driver.isApproved) {
                                    listOf(Color(0xFF064E3B), Color(0xFF047857))
                                } else {
                                    listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                }
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(
                                                when {
                                                    !driver.isApproved -> Color(0xFFF59E0B)
                                                    driver.isOnline -> Color(0xFF34D399)
                                                    else -> Color(0xFF94A3B8)
                                                },
                                                CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            !driver.isApproved -> if (isUrdu) "تصدیق زیرِ غور" else "APPROVAL PENDING"
                                            driver.isOnline -> if (isUrdu) "آن لائن • سواریاں دستیاب" else "ONLINE • ACCEPTING RIDES"
                                            else -> if (isUrdu) "آف لائن • سواری بند" else "OFFLINE • NOT RECEIVING"
                                        },
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        color = if (driver.isOnline && driver.isApproved) Color(0xFFA7F3D0) else Color(0xFFCBD5E1)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = driver.name,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "${driver.vehicleCategory.title} • ${driver.vehicleModel} (${driver.plateNo})",
                                    fontSize = 12.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Switch(
                                    checked = driver.isOnline,
                                    onCheckedChange = { enabled ->
                                        if (driver.isApproved) {
                                            repository.setDriverOnline(enabled)
                                        }
                                    },
                                    enabled = driver.isApproved,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF10B981),
                                        uncheckedThumbColor = Color(0xFFCBD5E1),
                                        uncheckedTrackColor = Color(0xFF334155)
                                    ),
                                    modifier = Modifier.testTag("driver_online_toggle")
                                )
                                Text(
                                    text = if (driver.isOnline) "Online" else "Offline",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }

                        // Approval status badge, Rating & Edit button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = if (driver.isApproved) Color(0xFF065F46) else Color(0xFF78350F),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (driver.isApproved) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (driver.isApproved) Color(0xFF34D399) else Color(0xFFFBBF24),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (driver.isApproved) "Verified Captain" else "Pending Admin Approval",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Surface(
                                    color = Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("${driver.rating}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { showRegisterDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("driver_edit_details_btn")
                            ) {
                                Text("Edit Profile", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (!driver.isApproved) {
                            Surface(
                                color = Color(0xFF7F1D1D).copy(alpha = 0.55f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("driver_unapproved_block_notice")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFFCA5A5),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isUrdu)
                                            "اکاؤنٹ بلاک: ماہی ایڈمن کی تصدیق تک آن لائن نہیں ہو سکتے"
                                        else
                                            "Blocked: Online toggle & incoming ride dispatch unlock once MAHI Admin approves your CNIC.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFFECACA)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live GPS Broadcast Status Card (for Approved Captains)
        if (driver.isApproved) {
            item {
                Surface(
                    color = if (hasLocationPermission && driver.isOnline) Color(0xFFECFDF5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (hasLocationPermission && driver.isOnline) Color(0xFF10B981).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_live_gps_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Live GPS Sync",
                                tint = if (hasLocationPermission && driver.isOnline) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = when {
                                        !hasLocationPermission -> if (isUrdu) "لائیو نقشے کیلئے GPS اجازت درکار ہے" else "Location Permission Needed for Live Map"
                                        !driver.isOnline -> if (isUrdu) "لائیو GPS آف لائن ہے" else "GPS Broadcast Paused (Go Online)"
                                        lastBroadcastCoords != null -> "Live GPS Synced: ${String.format("%.4f", lastBroadcastCoords!!.first)}, ${String.format("%.4f", lastBroadcastCoords!!.second)}"
                                        else -> if (isUrdu) "لائیو GPS فائر اسٹور سے منسلک ہے" else "Live GPS Telemetry Streaming Active"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasLocationPermission && driver.isOnline) Color(0xFF065F46) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (!hasLocationPermission) {
                            OutlinedButton(
                                onClick = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp).testTag("btn_driver_enable_gps")
                            ) {
                                Text("Enable GPS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Role Access Notice for Guest or Passenger Mode
        if (currentUser == null || currentUser?.isGuest == true || currentUser?.role != UserRole.DRIVER) {
            item {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_role_notice_card")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isUrdu) "کپتان موڈ: مسافر / مہمان اکاؤنٹ" else "Captain Preview Mode",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = if (isUrdu)
                                    "حقیقی سواریاں قبول کرنے کیلئے اپنا تصدیق شدہ کپتان اکاؤنٹ رجسٹر کریں"
                                else
                                    "Register your verified Captain profile & +92 mobile to receive live ride requests in Jaranwala.",
                                fontSize = 11.sp,
                                color = Color(0xFF78350F)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { showRegisterDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_register_captain_prompt")
                        ) {
                            Text("Register", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Active Driver Trip Card (if currently executing a trip)
        if (activeRide != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_active_trip_card"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isUrdu) "جاری سفر (Active Trip)" else "Active Trip Management",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = activeRide!!.status.display,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "Customer: ${activeRide!!.passengerName} • ${activeRide!!.passengerPhone}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${activeRide!!.pickup} ➔ ${activeRide!!.destination}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (activeRide!!.isParcelDelivery) {
                            Surface(
                                color = Color(0xFFECFDF5),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "PARCEL DELIVERY (${activeRide!!.parcelWeightKg})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF065F46)
                                    )
                                    Text(
                                        text = "Recipient: ${activeRide!!.recipientName} (${activeRide!!.recipientPhone})",
                                        fontSize = 11.sp,
                                        color = Color(0xFF047857)
                                    )
                                    if (activeRide!!.parcelDescription.isNotBlank()) {
                                        Text("Contents: ${activeRide!!.parcelDescription}", fontSize = 11.sp, color = Color(0xFF064E3B))
                                    }
                                    if (activeRide!!.codAmountPKR > 0) {
                                        Text(
                                            text = "Collect Cash on Delivery (COD): ₨ ${activeRide!!.codAmountPKR}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                }
                            }
                        }

                        when (activeRide!!.status) {
                            RideStatus.ACCEPTED -> {
                                Button(
                                    onClick = { repository.setDriverArrived() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("driver_arrived_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Mark 'Arrived at Pickup' (پہنچ گئے)", fontWeight = FontWeight.Bold)
                                }
                            }
                            RideStatus.DRIVER_ARRIVED -> {
                                Button(
                                    onClick = { repository.startTrip() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("driver_start_trip_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Start Trip (سفر شروع کریں)", fontWeight = FontWeight.Bold)
                                }
                            }
                            RideStatus.IN_PROGRESS -> {
                                Button(
                                    onClick = { repository.completeTrip() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("driver_complete_trip_btn"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                ) {
                                    Text("Complete Trip & Collect ₨ ${activeRide!!.estimatedFarePKR}", fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        // 4. Dedicated Incoming Ride Requests Queue Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("driver_incoming_requests_section"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (driver.isOnline && driver.isApproved) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUrdu) "آنے والی سواری کی درخواستیں" else "Incoming Ride Requests",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = if (combinedRequests.isNotEmpty()) Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = if (combinedRequests.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${combinedRequests.size} Live",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (combinedRequests.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Filter chips for Driver incoming requests queue
                if (driver.isApproved && driver.isOnline) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !filterMatchingVehicleOnly,
                            onClick = { filterMatchingVehicleOnly = false },
                            label = { Text(if (isUrdu) "تمام درخواستیں" else "All Jaranwala", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.testTag("driver_filter_all_requests")
                        )
                        FilterChip(
                            selected = filterMatchingVehicleOnly,
                            onClick = { filterMatchingVehicleOnly = true },
                            label = { Text("${driver.vehicleCategory.title} Only", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.testTag("driver_filter_my_vehicle")
                        )
                    }
                }
            }
        }

        // Incoming Ride Request Cards OR Offline / Empty Radar States
        if (!driver.isApproved) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(30.dp))
                        Text(
                            text = if (isUrdu) "درخواستیں بند ہیں (تصدیق درکار)" else "Requests Paused Until Approved",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Once MAHI Owner Admin verifies your documents, you can go Online to accept passenger bookings.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (!driver.isOnline) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_offline_empty_state"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Offline",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = if (isUrdu) "آپ اس وقت آف لائن ہیں" else "You are Currently Offline",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (isUrdu)
                                "جرانوالہ میں مسافروں کی لائیو درخواستیں دیکھنے کے لیے آن لائن سوئچ آن کریں۔"
                            else
                                "Toggle Online above to stream incoming passenger ride requests from Firestore.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { repository.setDriverOnline(true) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_go_online_quick")
                        ) {
                            Text(if (isUrdu) "آن لائن ہو جائیں (Go Online)" else "Go Online Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (combinedRequests.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_radar_listening_card"),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B).copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = "Listening Radar",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (isUrdu) "کپتان ریڈار فعال ہے • تلاش جاری" else "Radar Active • Listening for Requests",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isUrdu)
                                    "جرانوالہ و گردونواح میں کوئی نئی بکنگ آتے ہی یہاں فوراً ظاہر ہوگی۔"
                                else
                                    "Connected to Firestore dispatch queue. New passenger bookings appear here immediately.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            itemsIndexed(combinedRequests, key = { _, req -> req.id }) { index, request ->
                IncomingRideRequestCard(
                    request = request,
                    isFirstCard = index == 0,
                    isUrdu = isUrdu,
                    onAccept = { repository.acceptDriverRequest(request.id) },
                    onDecline = { repository.rejectDriverRequest(request.id) }
                )
            }
        }

        // 5. Captain Earnings & Stats
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUrdu) "کپتان کی کمائی اور اعداد و شمار" else "Captain Earnings & Stats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (driver.isDemoData) "Sample Baseline" else "Live Account",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Today", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "₨ ${driver.todayEarningsPKR}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "After 10% platform share",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("This Week", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "₨ ${driver.weeklyEarningsPKR}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFD97706)
                        )
                        Text(
                            text = "${driver.totalTrips} completed trips",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 6. Vehicle & Document Verification Info Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                        Text(
                            text = "Document & Vehicle Verification",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Surface(
                            color = if (driver.isApproved) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (driver.isApproved) "Verified" else "Under Review",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (driver.isApproved) Color(0xFF15803D) else Color(0xFF92400E),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("CNIC Number:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(driver.cnic, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Driving License:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(driver.licenseNo, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Plate Registration:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(driver.plateNo, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Mobile Verification:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "Firebase OTP (${driver.phone})",
                            fontSize = 12.sp,
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Captain Registration / Profile Update Dialog with real Firebase Phone OTP validation
    if (showRegisterDialog) {
        var regName by remember { mutableStateOf(driver.name) }
        var regPhone by remember { mutableStateOf(driver.phone) }
        var regCnic by remember { mutableStateOf(driver.cnic) }
        var regLicense by remember { mutableStateOf(driver.licenseNo) }
        var regCategory by remember { mutableStateOf(driver.vehicleCategory) }
        var regModel by remember { mutableStateOf(driver.vehicleModel) }
        var regPlate by remember { mutableStateOf(driver.plateNo) }
        var regFormError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isSendingOtp) showRegisterDialog = false },
            title = { Text("Captain Onboarding (ڈرائیور رجسٹریشن)", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_jaranwala_ride_logo),
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Jaranwala Ride Captain", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Firebase Phone Verified Onboarding", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = regName,
                            onValueChange = { regName = it; regFormError = null },
                            label = { Text("Full Name (پورا نام)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("driver_reg_name_input")
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = regPhone,
                            onValueChange = { regPhone = it; regFormError = null },
                            label = { Text("Phone Number (+92 / 03xx)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("driver_reg_phone_input")
                        )
                    }
                    item {
                        Text("Vehicle Category (گاڑی کی قسم):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VehicleCategory.values().forEach { cat ->
                                FilterChip(
                                    selected = regCategory == cat,
                                    onClick = { regCategory = cat },
                                    label = { Text(cat.title, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = regCnic,
                            onValueChange = { regCnic = it; regFormError = null },
                            label = { Text("CNIC (33102-XXXXXXX-X)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = regLicense,
                            onValueChange = { regLicense = it; regFormError = null },
                            label = { Text("Driving License Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = regModel,
                            onValueChange = { regModel = it; regFormError = null },
                            label = { Text("Vehicle Model & Make") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = regPlate,
                            onValueChange = { regPlate = it; regFormError = null },
                            label = { Text("Number Plate (e.g. FS-24-1234)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    regFormError?.let { err ->
                        item {
                            Text(err, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (regName.isBlank()) {
                            regFormError = "Please enter your full name."
                            return@Button
                        }
                        val phoneCheck = PakistanPhoneValidator.validate(regPhone)
                        if (!phoneCheck.isValid) {
                            regFormError = phoneCheck.errorMessage ?: "Invalid Pakistan mobile number (+92)."
                            return@Button
                        }
                        val cleanCnic = regCnic.replace("-", "").trim()
                        if (cleanCnic.length != 13 || !cleanCnic.all { it.isDigit() }) {
                            regFormError = "CNIC must be 13 digits (e.g. 33102-1234567-1)."
                            return@Button
                        }
                        if (regLicense.isBlank() || regModel.isBlank() || regPlate.isBlank()) {
                            regFormError = "Please enter license, vehicle model, and plate number."
                            return@Button
                        }

                        val draft = PendingCaptainOnboarding(
                            name = regName.trim(),
                            phoneLocal = phoneCheck.localFormatted,
                            phoneE164 = phoneCheck.normalizedE164,
                            cnic = regCnic.trim(),
                            license = regLicense.trim(),
                            category = regCategory,
                            model = regModel.trim(),
                            plate = regPlate.trim()
                        )
                        pendingOnboarding = draft
                        val driverDetails = DriverRegDetails(
                            cnic = draft.cnic,
                            licenseNo = draft.license,
                            vehicleCategory = draft.category,
                            vehicleModel = draft.model,
                            plateNo = draft.plate
                        )
                        isSendingOtp = true
                        regFormError = null

                        if (activity != null) {
                            phoneAuthHelper.sendVerificationCode(
                                activity = activity,
                                rawPhoneNumber = draft.phoneE164,
                                selectedRole = UserRole.DRIVER,
                                fullName = draft.name,
                                driverDetails = driverDetails,
                                onCodeSent = { vId ->
                                    verificationId = vId
                                    isSendingOtp = false
                                    showRegisterDialog = false
                                    enteredOtp = ""
                                    otpError = null
                                    showOtpDialog = true
                                },
                                onInstantVerified = { _ ->
                                    repository.completeCaptainOnboarding(
                                        name = draft.name,
                                        phone = draft.phoneLocal,
                                        cnic = draft.cnic,
                                        license = draft.license,
                                        category = draft.category,
                                        model = draft.model,
                                        plate = draft.plate,
                                        context = context
                                    )
                                    isSendingOtp = false
                                    showRegisterDialog = false
                                },
                                onError = { err, urduErr ->
                                    isSendingOtp = false
                                    regFormError = if (isUrdu) urduErr else err
                                }
                            )
                        } else if (PakistanFirebasePhoneAuthHelper.isRobolectricEnvironment()) {
                            repository.requestPhoneOtp(draft.phoneLocal)
                            verificationId = "ROBOLECTRIC-VERIF-${System.currentTimeMillis()}"
                            isSendingOtp = false
                            showRegisterDialog = false
                            enteredOtp = ""
                            otpError = null
                            showOtpDialog = true
                        } else {
                            isSendingOtp = false
                            regFormError = "Active screen required for Firebase SMS verification."
                        }
                    },
                    enabled = !isSendingOtp,
                    modifier = Modifier.testTag("driver_submit_reg_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isSendingOtp) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Send Firebase OTP")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRegisterDialog = false }, enabled = !isSendingOtp) {
                    Text("Cancel")
                }
            }
        )
    }

    // Real Firebase SMS OTP Verification Dialog for Captain Onboarding
    if (showOtpDialog && pendingOnboarding != null) {
        val draft = pendingOnboarding!!
        AlertDialog(
            onDismissRequest = { if (!isVerifyingOtp) showOtpDialog = false },
            title = { Text("Firebase SMS Verification", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isUrdu)
                            "آپ کے موبائل نمبر ${draft.phoneLocal} پر ۶ ہندسوں کا تصدیقی کوڈ بھیجا گیا ہے۔"
                        else
                            "Enter the 6-digit SMS code sent via Firebase Auth to ${draft.phoneLocal}.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = enteredOtp,
                        onValueChange = {
                            if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                                enteredOtp = it
                                otpError = null
                            }
                        },
                        placeholder = { Text("• • • • • •") },
                        label = { Text("6-digit SMS Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("driver_otp_input"),
                        isError = otpError != null
                    )
                    otpError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("driver_otp_error_text")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (enteredOtp.trim().length != 6) {
                            otpError = "Please enter the complete 6-digit SMS code."
                            return@Button
                        }
                        isVerifyingOtp = true
                        val details = DriverRegDetails(
                            cnic = draft.cnic,
                            licenseNo = draft.license,
                            vehicleCategory = draft.category,
                            vehicleModel = draft.model,
                            plateNo = draft.plate
                        )
                        phoneAuthHelper.verifyOtpAndSignIn(
                            otpCode = enteredOtp,
                            selectedRole = UserRole.DRIVER,
                            fullName = draft.name,
                            driverDetails = details,
                            verificationId = verificationId,
                            context = context,
                            onSuccess = { _ ->
                                repository.completeCaptainOnboarding(
                                    name = draft.name,
                                    phone = draft.phoneLocal,
                                    cnic = draft.cnic,
                                    license = draft.license,
                                    category = draft.category,
                                    model = draft.model,
                                    plate = draft.plate,
                                    context = context
                                )
                                isVerifyingOtp = false
                                showOtpDialog = false
                                enteredOtp = ""
                            },
                            onFailure = { msg, urduMsg ->
                                isVerifyingOtp = false
                                otpError = if (isUrdu) urduMsg else msg
                            }
                        )
                    },
                    enabled = !isVerifyingOtp,
                    modifier = Modifier.testTag("verify_otp_btn")
                ) {
                    if (isVerifyingOtp) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Verify & Submit")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showOtpDialog = false }, enabled = !isVerifyingOtp) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun IncomingRideRequestCard(
    request: RideBooking,
    isFirstCard: Boolean,
    isUrdu: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isFirstCard) "incoming_request_card" else "incoming_request_card_${request.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (request.vehicleCategory) {
                                VehicleCategory.BIKE -> Icons.Default.DirectionsBike
                                VehicleCategory.RICKSHAW -> Icons.Default.ElectricRickshaw
                                VehicleCategory.CAR -> Icons.Default.DirectionsCar
                            },
                            contentDescription = request.vehicleCategory.title,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (request.isParcelDelivery) {
                                if (isUrdu) "نیا پارسل / ڈلیوری آرڈر!" else "New Parcel Delivery Request!"
                            } else {
                                if (isUrdu) "نئی سواری کی درخواست!" else "New Ride Request!"
                            },
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${request.vehicleCategory.title} • #${request.id}",
                            fontSize = 11.sp,
                            color = Color(0xFFA7F3D0)
                        )
                    }
                }
                Text(
                    text = "₨ ${request.estimatedFarePKR}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = Color(0xFFFBBF24)
                )
            }

            Surface(
                color = Color.Black.copy(alpha = 0.22f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = request.pickup,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = request.destination,
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (request.isParcelDelivery) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Parcel to ${request.recipientName.ifBlank { "Recipient" }} • ${request.parcelDescription.ifBlank { request.parcelWeightKg }}",
                                color = Color(0xFFFDE68A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (request.codAmountPKR > 0) {
                            Text(
                                text = "COD to Collect: ₨ ${request.codAmountPKR}",
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Text(
                        text = "${request.passengerName} (${request.passengerPhone}) • ${request.estimatedDistanceKm} km",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag(if (isFirstCard) "driver_decline_btn" else "driver_decline_btn_${request.id}"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5))
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isUrdu) "مسترد" else "Decline", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .testTag(if (isFirstCard) "driver_accept_btn" else "driver_accept_btn_${request.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isUrdu) "قبول کریں" else "Accept Ride", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
