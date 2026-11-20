package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.font.FontFamily
import com.example.data.AppLanguage
import com.example.data.JaranwalaRepository
import com.example.data.RideBooking
import com.example.data.RideHistoryUiState
import com.example.data.RideStatus
import com.example.data.VehicleCategory
import com.example.data.local.CompletedRideEntity
import com.example.data.local.WeddingConvoyHistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ride History Section that fetches and displays the user's previous ride requests from Firestore,
 * and seamlessly provides a local Room Database tab to store and display completed rides
 * and wedding convoy bookings on-device.
 */
@Composable
fun RideHistorySection(
    repository: JaranwalaRepository,
    appLang: AppLanguage = AppLanguage.BILINGUAL,
    onRebookRide: ((pickup: String, destination: String, category: VehicleCategory) -> Unit)? = null,
    onFileComplaint: ((rideId: String) -> Unit)? = null,
    onOpenRidesDashboard: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val history by repository.passengerHistory.collectAsState()
    val historyUiState by repository.rideHistoryUiState.collectAsState()
    val localCompletedRides by repository.localCompletedRides.collectAsState()
    val localWeddingHistory by repository.localWeddingHistory.collectAsState()
    val currentUser by repository.currentUser.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    var primaryHistoryTab by remember { mutableIntStateOf(0) } // 0: Live / Cloud, 1: Local Room Database
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, COMPLETED, ACTIVE, CANCELLED
    var searchQuery by remember { mutableStateOf("") }
    var roomSubFilter by remember { mutableStateOf("RIDES") } // RIDES, WEDDINGS
    var selectedTripForDetails by remember { mutableStateOf<RideBooking?>(null) }

    // Real-time Firestore listener scoped to authenticated user's UID & role
    DisposableEffect(currentUser?.uid, currentUser?.role) {
        val user = currentUser
        val registration = if (user != null && !user.isGuest && user.uid.isNotBlank()) {
            repository.listenToUserRideHistoryFromFirestore(user.uid, user.role)
        } else {
            null
        }
        onDispose {
            registration?.remove()
        }
    }

    // Auto-fetch from Firestore when component enters composition or user changes
    LaunchedEffect(currentUser?.uid, currentUser?.role) {
        isRefreshing = true
        repository.fetchRideHistoryFromFirestore(currentUser?.uid ?: "") {
            isRefreshing = false
        }
    }

    val filteredHistory = remember(history, selectedFilter, searchQuery) {
        history.filter { ride ->
            val matchesFilter = when (selectedFilter) {
                "COMPLETED" -> ride.status == RideStatus.COMPLETED
                "ACTIVE" -> ride.status != RideStatus.COMPLETED && ride.status != RideStatus.CANCELLED
                "CANCELLED" -> ride.status == RideStatus.CANCELLED
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                ride.pickup.contains(searchQuery, ignoreCase = true) ||
                ride.destination.contains(searchQuery, ignoreCase = true) ||
                ride.id.contains(searchQuery, ignoreCase = true) ||
                (ride.driverName?.contains(searchQuery, ignoreCase = true) == true) ||
                (ride.vehiclePlate?.contains(searchQuery, ignoreCase = true) == true) ||
                ride.vehicleCategory.title.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ride_history_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header with Storage Badges & Refresh Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Ride History",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (appLang == AppLanguage.URDU) "سابقہ سواریاں و شادی ریکارڈ" else "Trip & Wedding History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (primaryHistoryTab == 0) Icons.Default.CloudDone else Icons.Default.Storage,
                            contentDescription = "Storage Status",
                            tint = if (primaryHistoryTab == 0) Color(0xFF10B981) else Color(0xFF3B82F6),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (primaryHistoryTab == 0) "Cloud Firestore Synced" else "Local Room Database (SQLite)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Refresh & Dashboard Buttons with Loading Indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onOpenRidesDashboard != null) {
                    FilledTonalButton(
                        onClick = onOpenRidesDashboard,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("btn_open_rides_dashboard")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dashboard,
                            contentDescription = "Open Rides Dashboard",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (appLang == AppLanguage.URDU) "مکمل ڈیش بورڈ" else "Full Dashboard",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .testTag("history_loading_indicator"),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                IconButton(
                    onClick = {
                        isRefreshing = true
                        repository.fetchRideHistoryFromFirestore(currentUser?.uid ?: "") {
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_refresh_history")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Ride History from Firestore",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Primary Source Tabs: Live & Cloud History vs Local Room Database
        TabRow(
            selectedTabIndex = primaryHistoryTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = primaryHistoryTab == 0,
                onClick = { primaryHistoryTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (appLang == AppLanguage.URDU) "لائیو کلاؤڈ" else "Live Rides", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                modifier = Modifier.testTag("tab_cloud_history")
            )
            Tab(
                selected = primaryHistoryTab == 1,
                onClick = { primaryHistoryTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (appLang == AppLanguage.URDU) "روم ڈیٹا بیس" else "Room Database", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                modifier = Modifier.testTag("tab_room_history")
            )
        }

        if (primaryHistoryTab == 0) {
            // Search Input for Ride History
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        if (appLang == AppLanguage.URDU) "سابقہ سواری تلاش کریں (مقام، بکنگ آئی ڈی یا کپتان)..."
                        else "Search history by location, booking ID, or driver...",
                        fontSize = 12.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search History",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Search", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_history_search"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Live / Cloud History Filter Pills: All, Completed, Active, Cancelled
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${history.size})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("filter_all_history")
                )
                FilterChip(
                    selected = selectedFilter == "COMPLETED",
                    onClick = { selectedFilter = "COMPLETED" },
                    label = { Text("Completed (${history.count { it.status == RideStatus.COMPLETED }})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFDCFCE7),
                        selectedLabelColor = Color(0xFF15803D)
                    ),
                    modifier = Modifier.testTag("filter_completed_history")
                )
                FilterChip(
                    selected = selectedFilter == "ACTIVE",
                    onClick = { selectedFilter = "ACTIVE" },
                    label = { Text("Active (${history.count { it.status != RideStatus.COMPLETED && it.status != RideStatus.CANCELLED }})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFEF3C7),
                        selectedLabelColor = Color(0xFF92400E)
                    ),
                    modifier = Modifier.testTag("filter_active_history")
                )
                FilterChip(
                    selected = selectedFilter == "CANCELLED",
                    onClick = { selectedFilter = "CANCELLED" },
                    label = { Text("Cancelled (${history.count { it.status == RideStatus.CANCELLED }})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFEE2E2),
                        selectedLabelColor = Color(0xFFB91C1C)
                    ),
                    modifier = Modifier.testTag("filter_cancelled_history")
                )
            }

            // Firestore Sync State Banners (Unauthenticated, Error, Loading)
            when (val uiState = historyUiState) {
                is RideHistoryUiState.Unauthenticated -> {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_unauthenticated_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (appLang == AppLanguage.URDU)
                                    "کلاؤڈ ٹرپ ہسٹری دیکھنے کے لیے تصدیق شدہ اکاؤنٹ سے لاگ ان کریں۔"
                                else
                                    uiState.message,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }
                is RideHistoryUiState.Error -> {
                    Surface(
                        color = Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFB91C1C),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (appLang == AppLanguage.URDU) uiState.urduMessage else uiState.message,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF991B1B)
                                )
                            }
                            TextButton(
                                onClick = {
                                    isRefreshing = true
                                    repository.fetchRideHistoryFromFirestore(currentUser?.uid ?: "") {
                                        isRefreshing = false
                                    }
                                },
                                modifier = Modifier.testTag("btn_retry_history")
                            ) {
                                Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                            }
                        }
                    }
                }
                is RideHistoryUiState.Loading -> {
                    if (filteredHistory.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("history_loading_state")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "فائر اسٹور سے سفر کا ریکارڈ لوڈ ہو رہا ہے..." else "Syncing trip history from Cloud Firestore...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                else -> {}
            }

            // Empty State or List of Ride Requests
            if (filteredHistory.isEmpty() && historyUiState !is RideHistoryUiState.Loading) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_ride_history")
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = if (appLang == AppLanguage.URDU) "کوئی سابقہ سواری نہیں ملی" else "No ride requests found yet",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (appLang == AppLanguage.URDU) "جرانوالہ میں اپنی پہلی سواری باآسانی بک کریں" else "Book your first ride across Jaranwala landmarks!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (filteredHistory.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ride_history_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    filteredHistory.forEach { itemRide ->
                        RideHistoryItemCard(
                            ride = itemRide,
                            appLang = appLang,
                            onViewDetails = {
                                selectedTripForDetails = itemRide
                            },
                            onRebook = {
                                onRebookRide?.invoke(itemRide.pickup, itemRide.destination, itemRide.vehicleCategory)
                            },
                            onComplaint = {
                                onFileComplaint?.invoke(itemRide.id)
                            }
                        )
                    }
                }
            }
        } else {
            // Local Room Database UI View
            Surface(
                color = Color(0xFF0F2B20),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .testTag("room_database_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Room DB",
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local Room SQLite Database Active",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Stored permanently on device • Offline accessible history",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Room Sub-filter Chips: Completed Rides vs Wedding Convoys
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = roomSubFilter == "RIDES",
                    onClick = { roomSubFilter = "RIDES" },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Completed Rides (${localCompletedRides.size})", fontSize = 11.sp)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFDCFCE7),
                        selectedLabelColor = Color(0xFF15803D)
                    ),
                    modifier = Modifier.testTag("room_subfilter_rides")
                )
                FilterChip(
                    selected = roomSubFilter == "WEDDINGS",
                    onClick = { roomSubFilter = "WEDDINGS" },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Wedding Convoys (${localWeddingHistory.size})", fontSize = 11.sp)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFEF3C7),
                        selectedLabelColor = Color(0xFF92400E)
                    ),
                    modifier = Modifier.testTag("room_subfilter_weddings")
                )
            }

            if (roomSubFilter == "RIDES") {
                if (localCompletedRides.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("empty_room_rides")
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("No completed rides in local Room DB yet", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Complete a ride to automatically archive it into SQLite Room persistence.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("room_completed_rides_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        localCompletedRides.forEach { rideEntity ->
                            RoomCompletedRideCard(
                                ride = rideEntity,
                                appLang = appLang,
                                onRebook = {
                                    val cat = when (rideEntity.vehicleCategory.uppercase()) {
                                        "BIKE" -> VehicleCategory.BIKE
                                        "CAR" -> VehicleCategory.CAR
                                        else -> VehicleCategory.RICKSHAW
                                    }
                                    onRebookRide?.invoke(rideEntity.pickup, rideEntity.destination, cat)
                                },
                                onDelete = {
                                    repository.deleteLocalCompletedRide(rideEntity.id)
                                }
                            )
                        }
                    }
                }
            } else {
                if (localWeddingHistory.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("empty_room_weddings")
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("No wedding convoy bookings in local Room DB yet", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Book a wedding car or barat convoy to store it in local Room persistence.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("room_wedding_history_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        localWeddingHistory.forEach { weddingEntity ->
                            RoomWeddingBookingCard(
                                booking = weddingEntity,
                                appLang = appLang,
                                onDelete = {
                                    repository.deleteLocalWeddingBooking(weddingEntity.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Past Trip Details Modal Dialog
    selectedTripForDetails?.let { detailRide ->
        PastTripDetailsDialog(
            ride = detailRide,
            appLang = appLang,
            onDismiss = { selectedTripForDetails = null },
            onRebook = {
                selectedTripForDetails = null
                onRebookRide?.invoke(detailRide.pickup, detailRide.destination, detailRide.vehicleCategory)
            }
        )
    }
}

@Composable
fun PastTripDetailsDialog(
    ride: RideBooking,
    appLang: AppLanguage,
    onDismiss: () -> Unit,
    onRebook: () -> Unit
) {
    val isUrdu = appLang == AppLanguage.URDU
    val formattedDate = remember(ride.timestamp) {
        try {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(ride.timestamp))
        } catch (_: Exception) {
            "Recent Trip"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("trip_details_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isUrdu) "سفر کی مکمل تفصیل" else "Past Trip Receipt",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "#${ride.id}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "$formattedDate • ${ride.status.display}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isUrdu) "پک اپ: ${ride.pickup}" else "Pickup: ${ride.pickup}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isUrdu) "منزل: ${ride.destination}" else "Destination: ${ride.destination}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${ride.vehicleCategory.title} • ${ride.estimatedDistanceKm} km",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val receiptMethod = com.example.data.PaymentMethod.fromFirestore(ride.paymentMethod)
                    Text(if (isUrdu) "کل کرایہ (${receiptMethod.urduTitle})" else "Total Fare (${receiptMethod.title})", fontSize = 12.sp)
                    Text("₨ ${ride.estimatedFarePKR}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }

                if (ride.discountPKR > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Promo (${ride.promoCode ?: "Applied"})", fontSize = 11.sp, color = Color(0xFF15803D))
                        Text("-₨ ${ride.discountPKR}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                    }
                }

                if (!ride.driverName.isNullOrBlank()) {
                    HorizontalDivider()
                    Text(
                        text = "Captain: ${ride.driverName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Plate: ${ride.vehiclePlate ?: "JRW"} • Phone: ${ride.driverPhone ?: "N/A"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (ride.ratingGiven > 0 || ride.reviewGiven.isNotBlank()) {
                    Text(
                        text = "Your Rating: ${ride.ratingGiven}★ ${if (ride.reviewGiven.isNotBlank()) "— \"${ride.reviewGiven}\"" else ""}",
                        fontSize = 11.sp,
                        color = Color(0xFFD97706),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRebook,
                modifier = Modifier.testTag("btn_dialog_rebook_${ride.id}")
            ) {
                Text(if (isUrdu) "دوبارہ بک کریں" else "Book Again")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_close_trip_details")
            ) {
                Text(if (isUrdu) "بند کریں" else "Close")
            }
        }
    )
}

@Composable
fun RoomCompletedRideCard(
    ride: CompletedRideEntity,
    appLang: AppLanguage,
    onRebook: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(ride.completedAt) {
        try {
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            sdf.format(Date(ride.completedAt))
        } catch (_: Exception) {
            "Recent Trip"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("room_ride_card_${ride.id}"),
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
                    Surface(
                        color = Color(0xFFE0E7FF),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "ROOM DB",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF3730A3),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${ride.vehicleCategory} • ${ride.id}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = "₨ ${ride.farePKR}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Route Points
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = ride.pickup, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = ride.destination, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Captain info, Rating & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "${ride.captainName} (${ride.vehiclePlate})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        if (ride.ratingGiven > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                            Text(text = "${ride.ratingGiven}★", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                        }
                    }
                    if (ride.reviewGiven.isNotBlank()) {
                        Text(text = "\"${ride.reviewGiven}\"", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onRebook,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("btn_room_rebook_${ride.id}"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rebook", fontSize = 11.sp)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("btn_room_delete_ride_${ride.id}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete from Room DB", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RoomWeddingBookingCard(
    booking: WeddingConvoyHistoryEntity,
    appLang: AppLanguage,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("room_wedding_card_${booking.id}"),
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
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (booking.isBaratConvoy) "BARAT CONVOY" else "WEDDING CAR",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF92400E),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = booking.id, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(
                    text = "₨ ${booking.totalCostPKR}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color(0xFFD97706)
                )
            }

            Text(
                text = "${booking.primaryCar} • ${booking.carCount} Car(s) • ${booking.durationLabel}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "Date & Time: ${booking.eventDate} at ${booking.eventTime}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(text = "From: ${booking.pickupLocation}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Venue: ${booking.venueLocation}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "ROOM STORED • CONFIRMED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("btn_room_delete_wedding_${booking.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun RideHistoryItemCard(
    ride: RideBooking,
    appLang: AppLanguage,
    onViewDetails: () -> Unit = {},
    onRebook: () -> Unit,
    onComplaint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(ride.timestamp) {
        try {
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            sdf.format(Date(ride.timestamp))
        } catch (_: Exception) {
            "Recent Trip"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onViewDetails() }
            .testTag("ride_history_item_${ride.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Vehicle Icon + Ride ID + Status Chip + Fare
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when (ride.vehicleCategory) {
                                    VehicleCategory.BIKE -> Color(0xFFDCFCE7)
                                    VehicleCategory.RICKSHAW -> Color(0xFFFEF3C7)
                                    VehicleCategory.CAR -> Color(0xFFE0E7FF)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (ride.vehicleCategory) {
                                VehicleCategory.BIKE -> Icons.Default.DirectionsBike
                                VehicleCategory.RICKSHAW -> Icons.Default.ElectricRickshaw
                                VehicleCategory.CAR -> Icons.Default.DirectionsCar
                            },
                            contentDescription = ride.vehicleCategory.title,
                            tint = when (ride.vehicleCategory) {
                                VehicleCategory.BIKE -> Color(0xFF047857)
                                VehicleCategory.RICKSHAW -> Color(0xFFB45309)
                                VehicleCategory.CAR -> Color(0xFF4338CA)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ride.vehicleCategory.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = ride.id,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formattedTime,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                // Fare & Status Chip
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₨ ${ride.estimatedFarePKR}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = when (ride.status) {
                            RideStatus.COMPLETED -> Color(0xFFDCFCE7)
                            RideStatus.CANCELLED -> Color(0xFFFEE2E2)
                            RideStatus.IN_PROGRESS -> Color(0xFFD1FAE5)
                            RideStatus.ACCEPTED, RideStatus.DRIVER_ARRIVED -> Color(0xFFE0E7FF)
                            else -> Color(0xFFFEF3C7)
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = when (ride.status) {
                                RideStatus.COMPLETED -> if (appLang == AppLanguage.URDU) "مکمل" else "Completed"
                                RideStatus.CANCELLED -> if (appLang == AppLanguage.URDU) "منسوخ" else "Cancelled"
                                RideStatus.IN_PROGRESS -> if (appLang == AppLanguage.URDU) "سفر جاری" else "In Progress"
                                RideStatus.ACCEPTED -> if (appLang == AppLanguage.URDU) "کپتان تفویض" else "Assigned"
                                else -> ride.status.display
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (ride.status) {
                                RideStatus.COMPLETED -> Color(0xFF15803D)
                                RideStatus.CANCELLED -> Color(0xFFB91C1C)
                                RideStatus.IN_PROGRESS -> Color(0xFF047857)
                                RideStatus.ACCEPTED -> Color(0xFF4338CA)
                                else -> Color(0xFF92400E)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Route Points
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = ride.pickup,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = ride.destination,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Payment Status Banner
            Surface(
                color = when (ride.status) {
                    RideStatus.COMPLETED -> Color(0xFFDCFCE7)
                    RideStatus.CANCELLED -> Color(0xFFFEE2E2)
                    else -> Color(0xFFFEF3C7)
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_status_${ride.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (ride.status) {
                            RideStatus.COMPLETED -> if (appLang == AppLanguage.URDU) "ادائیگی: نقد ادا شدہ (Paid ₨ ${ride.estimatedFarePKR})" else "Cash on Drop-off: Paid (نقد ادا شدہ)"
                            RideStatus.CANCELLED -> if (appLang == AppLanguage.URDU) "ادائیگی: منسوخ (کوئی رقم نہیں)" else "Payment Status: Cancelled • ₨ 0 Due"
                            else -> if (appLang == AppLanguage.URDU) "ادائیگی بقایا: نقد ₨ ${ride.estimatedFarePKR}" else "Payment Due: Cash PKR ₨ ${ride.estimatedFarePKR}"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (ride.status) {
                            RideStatus.COMPLETED -> Color(0xFF15803D)
                            RideStatus.CANCELLED -> Color(0xFFB91C1C)
                            else -> Color(0xFF92400E)
                        }
                    )
                    Text(
                        text = "${ride.estimatedDistanceKm} km • ${ride.vehicleCategory.title}",
                        fontSize = 10.sp,
                        color = when (ride.status) {
                            RideStatus.COMPLETED -> Color(0xFF15803D)
                            RideStatus.CANCELLED -> Color(0xFFB91C1C)
                            else -> Color(0xFF92400E)
                        }
                    )
                }
            }

            // Driver Details & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ride.driverName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Captain: ${ride.driverName} (${ride.vehiclePlate ?: "JRW"})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (ride.ratingGiven > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${ride.ratingGiven}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Jaranwala City Ride",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Action Buttons: Rebook & Report
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Rebook shortcut button
                    Button(
                        onClick = onRebook,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("btn_rebook_${ride.id}"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (appLang == AppLanguage.URDU) "دوبارہ بک کریں" else "Book Again", fontSize = 11.sp)
                    }

                    // Report problem
                    OutlinedButton(
                        onClick = onComplaint,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("complaint_btn_${ride.id}"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReportProblem,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Report", fontSize = 11.sp, color = Color(0xFFEF4444))
                    }
                }
            }
        }
    }
}
