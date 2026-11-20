package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppLanguage
import com.example.data.JaranwalaRepository
import com.example.data.RideBooking
import com.example.data.RideStatus
import com.example.data.VehicleCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RidesDashboardScreen(
    repository: JaranwalaRepository,
    onBack: () -> Unit,
    onRebookRide: ((pickup: String, destination: String, category: VehicleCategory) -> Unit)? = null,
    onTrackRideOnMap: ((rideId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appLang by repository.appLanguage.collectAsState()
    val isUrdu = appLang == AppLanguage.URDU
    val currentUser by repository.currentUser.collectAsState()

    var activeRides by remember { mutableStateOf<List<RideBooking>>(emptyList()) }
    var pastRides by remember { mutableStateOf<List<RideBooking>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Filtering & Search
    var selectedFilterTab by remember { mutableIntStateOf(0) } // 0: All, 1: Active, 2: Completed, 3: Cancelled
    var selectedVehicleFilter by remember { mutableStateOf<VehicleCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Rating & Complaint Dialog states
    var ratingRide by remember { mutableStateOf<RideBooking?>(null) }
    var selectedRating by remember { mutableIntStateOf(5) }
    var complaintRide by remember { mutableStateOf<RideBooking?>(null) }
    var complaintMessage by remember { mutableStateOf("") }
    var complaintCategory by remember { mutableStateOf("Driver Behavior") }

    // Cancel Confirmation & Details Dialog
    var rideToCancel by remember { mutableStateOf<RideBooking?>(null) }
    var selectedRideForDetails by remember { mutableStateOf<RideBooking?>(null) }

    fun refreshData() {
        isRefreshing = true
        repository.fetchAllDashboardRidesFromFirestore { active, past ->
            activeRides = active
            pastRides = past
            isLoading = false
            isRefreshing = false
        }
    }

    // Initial fetch + real-time Firestore listener scoped by currentUser
    LaunchedEffect(currentUser?.uid, currentUser?.role) {
        refreshData()
    }

    DisposableEffect(currentUser?.uid, currentUser?.role) {
        val listener = repository.listenToAllRidesFromFirestore { active, past ->
            activeRides = active
            pastRides = past
            isLoading = false
            isRefreshing = false
        }
        onDispose {
            listener?.remove()
        }
    }

    BackHandler {
        onBack()
    }

    // Combined and filtered lists
    val allRides = remember(activeRides, pastRides) {
        (activeRides + pastRides).distinctBy { it.id }.sortedByDescending { it.timestamp }
    }

    val displayedRides = remember(allRides, selectedFilterTab, selectedVehicleFilter, searchQuery) {
        allRides.filter { ride ->
            // Filter by Tab
            val matchesTab = when (selectedFilterTab) {
                1 -> ride.status == RideStatus.SEARCHING || ride.status == RideStatus.ACCEPTED ||
                        ride.status == RideStatus.DRIVER_ARRIVED || ride.status == RideStatus.IN_PROGRESS
                2 -> ride.status == RideStatus.COMPLETED
                3 -> ride.status == RideStatus.CANCELLED
                else -> true
            }

            // Filter by Vehicle
            val matchesVehicle = selectedVehicleFilter == null || ride.vehicleCategory == selectedVehicleFilter

            // Search Filter
            val matchesSearch = searchQuery.isBlank() ||
                    ride.id.contains(searchQuery, ignoreCase = true) ||
                    ride.pickup.contains(searchQuery, ignoreCase = true) ||
                    ride.destination.contains(searchQuery, ignoreCase = true) ||
                    (ride.driverName?.contains(searchQuery, ignoreCase = true) == true) ||
                    ride.passengerName.contains(searchQuery, ignoreCase = true)

            matchesTab && matchesVehicle && matchesSearch
        }
    }

    val totalSpentPKR = remember(pastRides) {
        pastRides.filter { it.status == RideStatus.COMPLETED }.sumOf { it.estimatedFarePKR }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("rides_dashboard_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isUrdu) "سواریوں کا ڈیش بورڈ" else "Rides Dashboard",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFF047857),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Firestore Live",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isUrdu) "جرانوالہ و پنجاب میں فعال اور مکمل سواریاں" else "Active & Past Rides across Jaranwala & Punjab",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_back_from_rides_dashboard")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshData() },
                        enabled = !isRefreshing,
                        modifier = Modifier.testTag("btn_refresh_firestore_rides")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh from Firestore")
                        }
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
                .testTag("rides_dashboard_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Metrics Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Active Count Card
                    Card(
                        modifier = Modifier.weight(1f).testTag("summary_active_rides_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF34D399), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isUrdu) "فعال سواریاں" else "Active Rides",
                                    color = Color(0xFFA7F3D0),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${activeRides.size}",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Completed Count Card
                    Card(
                        modifier = Modifier.weight(1f).testTag("summary_completed_rides_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isUrdu) "مکمل شدہ" else "Completed",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${pastRides.count { it.status == RideStatus.COMPLETED }}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Total Spent PKR Card
                    Card(
                        modifier = Modifier.weight(1f).testTag("summary_total_spent_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isUrdu) "کل خرچ" else "Total Spent",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "₨ $totalSpentPKR",
                                color = Color(0xFFFDE68A),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = if (isUrdu) "پک اپ، منزل، آئی ڈی یا ڈرائیور تلاش کریں..." else "Search by pickup, destination, ride ID or driver...",
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_rides_input")
                )
            }

            // Tab Filter Row: All, Active, Completed, Cancelled
            item {
                TabRow(
                    selectedTabIndex = selectedFilterTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedFilterTab == 0,
                        onClick = { selectedFilterTab = 0 },
                        modifier = Modifier.testTag("tab_all_rides"),
                        text = {
                            Text(
                                text = if (isUrdu) "تمام (${allRides.size})" else "All (${allRides.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    Tab(
                        selected = selectedFilterTab == 1,
                        onClick = { selectedFilterTab = 1 },
                        modifier = Modifier.testTag("tab_active_rides"),
                        text = {
                            Text(
                                text = if (isUrdu) "فعال (${activeRides.size})" else "Active (${activeRides.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedFilterTab == 1) Color(0xFF047857) else Color.Unspecified
                            )
                        }
                    )
                    Tab(
                        selected = selectedFilterTab == 2,
                        onClick = { selectedFilterTab = 2 },
                        modifier = Modifier.testTag("tab_past_rides"),
                        text = {
                            Text(
                                text = if (isUrdu) "مکمل شدہ" else "Completed",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    Tab(
                        selected = selectedFilterTab == 3,
                        onClick = { selectedFilterTab = 3 },
                        modifier = Modifier.testTag("tab_cancelled_rides"),
                        text = {
                            Text(
                                text = if (isUrdu) "منسوخ" else "Cancelled",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }

            // Vehicle Category Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedVehicleFilter == null,
                            onClick = { selectedVehicleFilter = null },
                            label = { Text(if (isUrdu) "تمام گاڑیاں" else "All Vehicles", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("filter_vehicle_all")
                        )
                    }
                    VehicleCategory.values().forEach { category ->
                        item {
                            FilterChip(
                                selected = selectedVehicleFilter == category,
                                onClick = { selectedVehicleFilter = if (selectedVehicleFilter == category) null else category },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (category) {
                                            VehicleCategory.BIKE -> Icons.Default.DirectionsBike
                                            VehicleCategory.RICKSHAW -> Icons.Default.ElectricRickshaw
                                            VehicleCategory.CAR -> Icons.Default.DirectionsCar
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(if (isUrdu) category.urduTitle else category.title, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("filter_vehicle_${category.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Loading indicator
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isUrdu) "فائر بیس فائر اسٹور سے سواریاں لوڈ کی جا رہی ہیں..." else "Fetching rides from Cloud Firestore...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (displayedRides.isEmpty()) {
                // Empty State
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(30.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                modifier = Modifier.size(54.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isUrdu) "کوئی سواری نہیں ملی" else "No rides found in Firestore",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (isUrdu) "آپ نے جو فلٹر منتخب کیا ہے اس میں کوئی ریکارڈ موجود نہیں ہے۔" else "No rides match the selected filters or search query.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onBack,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(if (isUrdu) "نئی سواری بک کریں" else "Book a New Ride")
                            }
                        }
                    }
                }
            } else {
                // List of Detailed Ride Cards
                items(displayedRides, key = { it.id }) { ride ->
                    DetailedRideCard(
                        ride = ride,
                        isUrdu = isUrdu,
                        onCardClick = { selectedRideForDetails = ride },
                        onCallDriver = { phone ->
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                context.startActivity(intent)
                            } catch (_: Throwable) {}
                        },
                        onRebook = {
                            onRebookRide?.invoke(ride.pickup, ride.destination, ride.vehicleCategory)
                            onBack()
                        },
                        onRate = {
                            ratingRide = ride
                            selectedRating = if (ride.ratingGiven > 0) ride.ratingGiven else 5
                        },
                        onReportIssue = {
                            complaintRide = ride
                            complaintMessage = ""
                        },
                        onCancelRide = {
                            rideToCancel = ride
                        },
                        onTrackOnMap = {
                            onTrackRideOnMap?.invoke(ride.id)
                        }
                    )
                }
            }
        }
    }

    // Cancel Confirmation Dialog
    rideToCancel?.let { targetRide ->
        AlertDialog(
            onDismissRequest = { rideToCancel = null },
            title = {
                Text(
                    text = if (isUrdu) "سواری منسوخ کریں؟" else "Cancel Ride?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isUrdu) "کیا آپ واقعی سواری #${targetRide.id} منسوخ کرنا چاہتے ہیں؟" else "Are you sure you want to cancel ride #${targetRide.id} from ${targetRide.pickup} to ${targetRide.destination}?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.cancelRide(targetRide.id)
                        rideToCancel = null
                        refreshData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text(if (isUrdu) "ہاں، منسوخ کریں" else "Yes, Cancel Ride")
                }
            },
            dismissButton = {
                TextButton(onClick = { rideToCancel = null }) {
                    Text(if (isUrdu) "واپس" else "Dismiss")
                }
            }
        )
    }

    // Rating Dialog
    ratingRide?.let { targetRide ->
        AlertDialog(
            onDismissRequest = { ratingRide = null },
            title = {
                Text(
                    text = if (isUrdu) "کپتان کو ریٹنگ دیں" else "Rate Captain Experience",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = targetRide.driverName ?: "Captain",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = targetRide.vehiclePlate ?: targetRide.vehicleCategory.title,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (1..5).forEach { starIndex ->
                            IconButton(onClick = { selectedRating = starIndex }) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "$starIndex stars",
                                    tint = if (starIndex <= selectedRating) Color(0xFFF59E0B) else Color.LightGray,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.rateCompletedRide(targetRide.id, selectedRating)
                        ratingRide = null
                        refreshData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (isUrdu) "جمع کرائیں" else "Submit Rating")
                }
            },
            dismissButton = {
                TextButton(onClick = { ratingRide = null }) {
                    Text(if (isUrdu) "منسوخ" else "Cancel")
                }
            }
        )
    }

    // Complaint Dialog
    complaintRide?.let { targetRide ->
        AlertDialog(
            onDismissRequest = { complaintRide = null },
            title = {
                Text(
                    text = if (isUrdu) "شکایت درج کریں" else "Report an Issue",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ride #${targetRide.id} • ${targetRide.pickup} ➔ ${targetRide.destination}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = complaintMessage,
                        onValueChange = { complaintMessage = it },
                        label = { Text(if (isUrdu) "مسئلے کی تفصیل" else "Describe your issue") },
                        placeholder = { Text("e.g. Overcharged, route deviation, captain behavior") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (complaintMessage.isNotBlank()) {
                            repository.submitComplaint(
                                rideId = targetRide.id,
                                name = currentUser?.name ?: "Passenger",
                                category = complaintCategory,
                                urduCat = "شکایت",
                                msg = complaintMessage
                            )
                        }
                        complaintRide = null
                    },
                    enabled = complaintMessage.isNotBlank()
                ) {
                    Text(if (isUrdu) "شکایت بھیجیں" else "Submit Complaint")
                }
            },
            dismissButton = {
                TextButton(onClick = { complaintRide = null }) {
                    Text(if (isUrdu) "منسوخ" else "Cancel")
                }
            }
        )
    }
    // Past Trip Details Modal
    selectedRideForDetails?.let { detailRide ->
        com.example.ui.components.PastTripDetailsDialog(
            ride = detailRide,
            appLang = appLang,
            onDismiss = { selectedRideForDetails = null },
            onRebook = {
                selectedRideForDetails = null
                onRebookRide?.invoke(detailRide.pickup, detailRide.destination, detailRide.vehicleCategory)
                onBack()
            }
        )
    }
}

@Composable
fun DetailedRideCard(
    ride: RideBooking,
    isUrdu: Boolean,
    onCardClick: () -> Unit = {},
    onCallDriver: (String) -> Unit,
    onRebook: () -> Unit,
    onRate: () -> Unit,
    onReportIssue: () -> Unit,
    onCancelRide: () -> Unit,
    onTrackOnMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = ride.status == RideStatus.SEARCHING ||
            ride.status == RideStatus.ACCEPTED ||
            ride.status == RideStatus.DRIVER_ARRIVED ||
            ride.status == RideStatus.IN_PROGRESS

    val statusColor = when (ride.status) {
        RideStatus.SEARCHING -> Color(0xFFD97706)
        RideStatus.ACCEPTED -> Color(0xFF047857)
        RideStatus.DRIVER_ARRIVED -> Color(0xFF0D9488)
        RideStatus.IN_PROGRESS -> Color(0xFF2563EB)
        RideStatus.COMPLETED -> Color(0xFF16A34A)
        RideStatus.CANCELLED -> Color(0xFFDC2626)
        else -> Color(0xFF64748B)
    }

    val cardBorder = if (isActive) {
        BorderStroke(1.5.dp, statusColor.copy(alpha = 0.6f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("ride_card_${ride.id}"),
        shape = RoundedCornerShape(18.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Vehicle Icon + Ride ID + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = when (ride.vehicleCategory) {
                                    VehicleCategory.BIKE -> Color(0xFFECFDF5)
                                    VehicleCategory.RICKSHAW -> Color(0xFFFFFBEB)
                                    VehicleCategory.CAR -> Color(0xFFEFF6FF)
                                },
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (ride.vehicleCategory) {
                                VehicleCategory.BIKE -> Icons.Default.DirectionsBike
                                VehicleCategory.RICKSHAW -> Icons.Default.ElectricRickshaw
                                VehicleCategory.CAR -> Icons.Default.DirectionsCar
                            },
                            contentDescription = null,
                            tint = when (ride.vehicleCategory) {
                                VehicleCategory.BIKE -> Color(0xFF047857)
                                VehicleCategory.RICKSHAW -> Color(0xFFB45309)
                                VehicleCategory.CAR -> Color(0xFF1D4ED8)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "#${ride.id}",
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                            if (ride.isDemoData) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "DEMO",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isUrdu) ride.vehicleCategory.urduTitle else ride.vehicleCategory.title,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Badge
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = if (isUrdu) ride.status.urduDisplay else ride.status.display,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Timestamp & City
            val formattedDate = remember(ride.timestamp) {
                SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(ride.timestamp))
            }
            Text(
                text = "$formattedDate • Jaranwala City",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Route Visualizer (Origin to Destination)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Pickup
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isUrdu) "پک اپ پوائنٹ (Pickup)" else "Pickup Location",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = ride.pickup,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Destination
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isUrdu) "منزل (Destination)" else "Drop-off Location",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = ride.destination,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Fare & Distance Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "₨ ${ride.estimatedFarePKR}",
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (ride.discountPKR > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "-₨ ${ride.discountPKR} (${ride.promoCode ?: "Promo"})",
                                color = Color(0xFF15803D),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "${ride.estimatedDistanceKm} km • Cash on Drop-off",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            // Driver Details (if assigned)
            if (!ride.driverName.isNullOrBlank()) {
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFF334155), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ride.driverName.take(1),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = ride.driverName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "${ride.driverRating}",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = ride.vehiclePlate ?: "JRW Fleet",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        ride.driverPhone?.let { phone ->
                            FilledTonalButton(
                                onClick = { onCallDriver(phone) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp).testTag("btn_call_driver_${ride.id}"),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Call", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isActive) {
                    Button(
                        onClick = onTrackOnMap,
                        modifier = Modifier.weight(1f).height(38.dp).testTag("btn_track_live_${ride.id}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isUrdu) "لائیو ٹریک کریں" else "Live Track", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onCancelRide,
                        modifier = Modifier.height(38.dp).testTag("btn_cancel_ride_${ride.id}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                    ) {
                        Text(if (isUrdu) "منسوخ" else "Cancel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onRebook,
                        modifier = Modifier.weight(1f).height(36.dp).testTag("btn_rebook_ride_${ride.id}"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isUrdu) "دوبارہ بک کریں" else "Re-Book", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (ride.status == RideStatus.COMPLETED) {
                        FilledTonalButton(
                            onClick = onRate,
                            modifier = Modifier.height(36.dp).testTag("btn_rate_ride_${ride.id}"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFF59E0B))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (ride.ratingGiven > 0) "★ ${ride.ratingGiven}" else (if (isUrdu) "ریٹنگ" else "Rate"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onReportIssue,
                        modifier = Modifier.size(36.dp).testTag("btn_complaint_ride_${ride.id}")
                    ) {
                        Icon(
                            Icons.Default.ReportProblem,
                            contentDescription = "Report Issue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
