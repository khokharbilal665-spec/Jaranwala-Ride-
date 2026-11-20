package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppLanguage
import com.example.data.JaranwalaRepository
import com.example.data.RideBooking
import com.example.data.RideHistoryUiState
import com.example.data.RideStatus
import com.example.data.UserRole
import com.example.data.VehicleCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Ride History & Payments screen fetching and displaying past COMPLETED rides
 * from Cloud Firestore for the currently authenticated user, including fare amount in PKR (₨),
 * formatted date & time, ride status, and cash payment settlement status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideHistoryAndPaymentsScreen(
    repository: JaranwalaRepository,
    onBack: () -> Unit,
    onOpenAuth: (() -> Unit)? = null,
    onRebookRide: ((pickup: String, destination: String, category: VehicleCategory) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val appLang by repository.appLanguage.collectAsState()
    val isUrdu = appLang == AppLanguage.URDU
    val currentUser by repository.currentUser.collectAsState()
    val completedState by repository.completedPaymentRidesState.collectAsState()
    val passengerHistory by repository.passengerHistory.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<VehicleCategory?>(null) }
    var selectedReceiptRide by remember { mutableStateOf<RideBooking?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    LaunchedEffect(currentUser?.uid, currentUser?.role) {
        isRefreshing = true
        repository.fetchCompletedRidesAndPaymentsFromFirestore {
            isRefreshing = false
        }
    }

    DisposableEffect(currentUser?.uid, currentUser?.role) {
        val listener = repository.listenToCompletedRidesAndPayments()
        onDispose {
            listener?.remove()
        }
    }

    val isUnauthenticated = completedState is RideHistoryUiState.Unauthenticated ||
        currentUser == null || currentUser?.isGuest == true

    val completedRides = remember(completedState, passengerHistory, isUnauthenticated) {
        if (isUnauthenticated) {
            emptyList()
        } else {
            val fromState = (completedState as? RideHistoryUiState.Success)?.rides
            val source = fromState ?: passengerHistory.filter { it.status == RideStatus.COMPLETED }
            source.filter { it.status == RideStatus.COMPLETED }
                .distinctBy { it.id }
                .sortedByDescending { it.timestamp }
        }
    }

    val filteredRides = remember(completedRides, selectedCategory, searchQuery) {
        completedRides.filter { ride ->
            val catOk = selectedCategory == null || ride.vehicleCategory == selectedCategory
            val queryOk = searchQuery.isBlank() ||
                ride.id.contains(searchQuery, ignoreCase = true) ||
                ride.pickup.contains(searchQuery, ignoreCase = true) ||
                ride.destination.contains(searchQuery, ignoreCase = true) ||
                (ride.driverName?.contains(searchQuery, ignoreCase = true) == true)
            catOk && queryOk
        }
    }

    val totalFarePKR = remember(completedRides) {
        completedRides.sumOf { it.estimatedFarePKR }
    }
    val totalSavingsPKR = remember(completedRides) {
        completedRides.sumOf { it.discountPKR }
    }
    val settledCashCount = remember(completedRides) {
        completedRides.count {
            it.paymentStatus.equals("COLLECTED_CASH", ignoreCase = true) ||
                it.paymentStatus.equals("PAID", ignoreCase = true) ||
                it.status == RideStatus.COMPLETED
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("ride_history_payments_screen"),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isUrdu) "سفر اور ادائیگیوں کا ریکارڈ" else "Ride History & Payments",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF065F46),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = Color(0xFF6EE7B7),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Firestore",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF6EE7B7)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isUrdu) "مکمل شدہ سواریاں، کرایہ اور نقد ادائیگی کی تفصیلات" else "Completed trips, fares & verified cash receipts",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_back_history_payments")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isRefreshing = true
                            repository.fetchCompletedRidesAndPaymentsFromFirestore {
                                isRefreshing = false
                            }
                        },
                        modifier = Modifier.testTag("btn_refresh_history_payments")
                    ) {
                        if (isRefreshing || completedState is RideHistoryUiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh History and Payments"
                            )
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
                .padding(horizontal = 16.dp)
                .testTag("ride_history_payments_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (isUnauthenticated) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payments_unauthenticated_banner"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Authentication Required",
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = if (isUrdu) "اپنا ریکارڈ دیکھنے کیلئے لاگ ان کریں" else "Sign In to View Ride History & Payments",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color(0xFF78350F)
                            )
                            Text(
                                text = if (isUrdu)
                                    "محفوظ کلاؤڈ ادائیگی و مکمل سواریوں کی رسیدیں صرف تصدیق شدہ صارف کے اکاؤنٹ میں دستیاب ہیں۔"
                                else
                                    "Your completed trip receipts and cash fare ledger are securely scoped to your verified Firebase account.",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                            if (onOpenAuth != null) {
                                Button(
                                    onClick = onOpenAuth,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("btn_signin_for_payment_history")
                                ) {
                                    Text(
                                        text = if (isUrdu) "لاگ ان / سائن اپ کریں" else "Sign In Now",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                return@LazyColumn
            }

            // Payments & Completed Trips Summary Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payments_summary_card"),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF064E3B), Color(0xFF047857), Color(0xFF0F172A))
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
                                Column {
                                    Text(
                                        text = if (currentUser?.role == UserRole.DRIVER) {
                                            if (isUrdu) "کل کمائی (مکمل سواریاں)" else "Total Earnings (Completed)"
                                        } else {
                                            if (isUrdu) "کل ادا شدہ کرایہ" else "Total Paid on Completed Rides"
                                        },
                                        color = Color(0xFFA7F3D0),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "₨ $totalFarePKR",
                                        color = Color.White,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.testTag("payments_total_spent_text")
                                    )
                                }
                                Surface(
                                    color = Color(0xFFF59E0B),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = "Cash Ledger",
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "PKR CASH",
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = if (isUrdu) "مکمل سواریاں" else "Completed Rides",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "${completedRides.size} Trips",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.testTag("completed_rides_count_text")
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (isUrdu) "نقد ادائیگی" else "Cash Settled",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "$settledCashCount Receipts",
                                        color = Color(0xFF6EE7B7),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (isUrdu) "پرومو بچت" else "Promo Saved",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "₨ $totalSavingsPKR",
                                        color = Color(0xFFFDE68A),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (completedState is RideHistoryUiState.Error) {
                val err = completedState as RideHistoryUiState.Error
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payments_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isUrdu) err.urduMessage else err.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(
                                onClick = { repository.fetchCompletedRidesAndPaymentsFromFirestore() }
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            // Search & Vehicle Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                if (isUrdu) "پک اپ، منزل یا رائیڈ ID تلاش کریں..." else "Search by pickup, destination, captain or ID...",
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payments_history_search_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text(if (isUrdu) "تمام (${completedRides.size})" else "All (${completedRides.size})", fontSize = 12.sp) },
                                modifier = Modifier.testTag("payments_filter_all")
                            )
                        }
                        items(VehicleCategory.values()) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                                leadingIcon = {
                                    Icon(cat.icon, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                label = { Text(cat.title, fontSize = 12.sp) },
                                modifier = Modifier.testTag("payments_filter_${cat.name.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }

            if (filteredRides.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payments_empty_state"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = if (isUrdu) "کوئی مکمل شدہ سواری نہیں ملی" else "No Completed Rides Found",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (isUrdu)
                                    "جب آپ کی کوئی سواری مکمل ہوگی تو کرایہ اور رسید یہاں نظر آئے گی۔"
                                else
                                    "Completed trips and cash payment receipts will appear here.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredRides, key = { it.id }) { ride ->
                    CompletedRidePaymentCard(
                        ride = ride,
                        isUrdu = isUrdu,
                        onViewReceipt = { selectedReceiptRide = ride },
                        onRebook = onRebookRide
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    selectedReceiptRide?.let { ride ->
        CompletedRideReceiptDialog(
            ride = ride,
            isUrdu = isUrdu,
            onDismiss = { selectedReceiptRide = null },
            onRebook = {
                selectedReceiptRide = null
                onRebookRide?.invoke(ride.pickup, ride.destination, ride.vehicleCategory)
            }
        )
    }
}

@Composable
private fun CompletedRidePaymentCard(
    ride: RideBooking,
    isUrdu: Boolean,
    onViewReceipt: () -> Unit,
    onRebook: ((String, String, VehicleCategory) -> Unit)?
) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(ride.timestamp) { dateFormatter.format(Date(ride.timestamp)) }

    val paymentBadgeText = when {
        ride.paymentMethod.equals("JAZZCASH", ignoreCase = true) ->
            if (isUrdu) "جاز کیش ادا شدہ (JazzCash)" else "Paid via JazzCash"
        ride.paymentMethod.equals("EASYPAISA", ignoreCase = true) ->
            if (isUrdu) "ایزی پیسہ ادا شدہ (Easypaisa)" else "Paid via Easypaisa"
        ride.paymentStatus.equals("COLLECTED_CASH", ignoreCase = true) ||
            ride.paymentStatus.equals("PAID", ignoreCase = true) ->
            if (isUrdu) "نقد ادا شدہ (Paid Cash)" else "Cash Collected"
        else -> if (isUrdu) "مکمل • نقد ادائیگی" else "Cash Settled"
    }

    val vehicleIcon = when (ride.vehicleCategory) {
        VehicleCategory.BIKE -> Icons.Default.DirectionsBike
        VehicleCategory.RICKSHAW -> Icons.Default.ElectricRickshaw
        VehicleCategory.CAR -> Icons.Default.DirectionsCar
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewReceipt() }
            .testTag("completed_ride_card_${ride.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Date + Vehicle + Fare Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = vehicleIcon,
                            contentDescription = ride.vehicleCategory.title,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = ride.vehicleCategory.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formattedDate,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("completed_ride_date_${ride.id}")
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₨ ${ride.estimatedFarePKR}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("completed_ride_fare_${ride.id}")
                    )
                    if (ride.isParcelDelivery) {
                        Text(
                            text = if (ride.codAmountPKR > 0) "PARCEL • COD ₨ ${ride.codAmountPKR}" else "PARCEL DELIVERY",
                            fontSize = 9.sp,
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    if (ride.discountPKR > 0) {
                        Text(
                            text = "Saved ₨ ${ride.discountPKR}",
                            fontSize = 10.sp,
                            color = Color(0xFF15803D),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Route Summary
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = ride.pickup,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
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
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Status + Payment Method + Captain Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isUrdu) ride.status.urduDisplay else ride.status.display,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D),
                                modifier = Modifier.testTag("completed_ride_status_${ride.id}")
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = Color(0xFF92400E),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = paymentBadgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                                modifier = Modifier.testTag("completed_ride_payment_status_${ride.id}")
                            )
                        }
                    }
                }

                if (ride.ratingGiven > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${ride.ratingGiven}.0",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Action Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${ride.driverName ?: "Jaranwala Captain"} • ${ride.estimatedDistanceKm} km",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onViewReceipt,
                        modifier = Modifier.testTag("btn_receipt_${ride.id}")
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isUrdu) "رسید" else "Receipt", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (onRebook != null) {
                        OutlinedButton(
                            onClick = { onRebook(ride.pickup, ride.destination, ride.vehicleCategory) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isUrdu) "دوبارہ بک" else "Rebook", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedRideReceiptDialog(
    ride: RideBooking,
    isUrdu: Boolean,
    onDismiss: () -> Unit,
    onRebook: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val dateStr = remember(ride.timestamp) { dateFormat.format(Date(ride.timestamp)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("completed_ride_receipt_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isUrdu) "ادائیگی کی رسید" else "Trip Payment Receipt",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ride ID: ${ride.id}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Date: $dateStr", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Text("Pickup: ${ride.pickup}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Dropoff: ${ride.destination}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Distance: ${ride.estimatedDistanceKm} km • ${ride.vehicleCategory.title}", fontSize = 12.sp)
                Text("Captain: ${ride.driverName ?: "Verified Captain"} (${ride.vehiclePlate ?: "JRW"})", fontSize = 12.sp)
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Status:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ride.status.display, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Payment Method:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${ride.paymentMethod} (${ride.paymentStatus})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (ride.discountPKR > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Promo (${ride.promoCode ?: "OFFER"}):", fontSize = 12.sp, color = Color(0xFF15803D))
                        Text("- ₨ ${ride.discountPKR}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Fare Paid:", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text("₨ ${ride.estimatedFarePKR}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(onClick = onRebook) {
                Text(if (isUrdu) "دوبارہ بک کریں" else "Book Again")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_close_receipt_dialog")
            ) {
                Text(if (isUrdu) "بند کریں" else "Close")
            }
        }
    )
}
