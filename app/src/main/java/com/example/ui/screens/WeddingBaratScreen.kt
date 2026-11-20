package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppLanguage
import com.example.data.ConvoyMixType
import com.example.data.DecorationTier
import com.example.data.JaranwalaRepository
import com.example.data.WeddingBooking
import com.example.data.WeddingCarType

@Composable
fun WeddingBaratScreen(
    repository: JaranwalaRepository,
    initialIsBarat: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bookings by repository.weddingBookings.collectAsState()
    val appLang by repository.appLanguage.collectAsState()
    val currentUser by repository.currentUser.collectAsState()

    var selectedTab by remember(initialIsBarat) { mutableIntStateOf(if (initialIsBarat) 1 else 0) } // 0: Single Wedding Car, 1: Barat Convoy
    val isBarat = selectedTab == 1

    var selectedCarType by remember { mutableStateOf(WeddingCarType.PRADO_TX) }
    var selectedConvoyMix by remember { mutableStateOf(ConvoyMixType.STANDARD_SEDANS) }
    var baratFleetCount by remember { mutableIntStateOf(5) }
    var selectedDecoration by remember { mutableStateOf(DecorationTier.ROYAL_ROSES) }

    var customerName by remember { mutableStateOf("Malik Usman") }
    var customerPhone by remember { mutableStateOf("0300-7654321") }

    androidx.compose.runtime.LaunchedEffect(currentUser?.uid, currentUser?.name, currentUser?.phoneNumber) {
        val user = currentUser ?: return@LaunchedEffect
        val validName = user.name.takeIf { it.isNotBlank() && !it.startsWith("Guest", ignoreCase = true) }
        val validPhone = user.phoneNumber.takeIf { it.isNotBlank() && it != "0300-0000000" }
        if (validName != null && (customerName.isBlank() || customerName == "Malik Usman")) {
            customerName = validName
        }
        if (validPhone != null && (customerPhone.isBlank() || customerPhone == "0300-7654321")) {
            customerPhone = validPhone
        }
    }
    var eventDate by remember { mutableStateOf("25 Nov 2026") }
    var eventTime by remember { mutableStateOf("03:30 PM") }
    var pickupAddress by remember { mutableStateOf("Cinema Chowk, Jaranwala") }
    var venueDestination by remember { mutableStateOf("Royal Palm Marriage Hall, Lahore Road") }
    var specialNotes by remember { mutableStateOf("Red rose floral bonnet decoration with white ribbons") }

    // Validation errors state
    var nameError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }
    var pickupError by remember { mutableStateOf(false) }
    var venueError by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var lastConfirmedBooking by remember { mutableStateOf<WeddingBooking?>(null) }

    val baseRatePKR = remember(isBarat, selectedCarType, baratFleetCount, selectedConvoyMix) {
        if (isBarat) {
            baratFleetCount * selectedConvoyMix.multiplierPerCarPKR
        } else {
            selectedCarType.basePricePKR
        }
    }

    val calculatedTotalPKR = remember(baseRatePKR, selectedDecoration) {
        baseRatePKR + selectedDecoration.additionalPricePKR
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Image Banner
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.wedding_car_banner_1793268754523),
                        contentDescription = "Decorated Wedding Car",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xDD000000)),
                                    startY = 50f
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Surface(
                            color = Color(0xFFD97706),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "شادی و بارات کار سروس • Royal Convoy",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Jaranwala Wedding & Barat Service",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Luxury decorated bridal cars & convoy fleets in Jaranwala",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Service Mode Tabs (Single Car vs Barat Convoy)
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("wedding_barat_tabs")
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = if (appLang == AppLanguage.URDU) "شادی کار (Single Car)" else "Bridal Car (شادی کار)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    },
                    icon = {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("tab_single_car")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = if (appLang == AppLanguage.URDU) "بارات قافلہ (Barat Fleet)" else "Barat Convoy (بارات قافلہ)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    },
                    icon = {
                        Icon(Icons.Default.Diversity3, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("tab_barat_convoy")
                )
            }
        }

        if (isBarat) {
            // Specialized Booking Form for Wedding Convoys (Fleet Size, Duration, Decoration Preferences)
            item {
                com.example.ui.components.WeddingConvoyBookingForm(
                    repository = repository,
                    appLang = appLang,
                    onBookingSuccess = { booking ->
                        lastConfirmedBooking = booking
                    }
                )
            }
        }

        // Vehicle / Fleet Selection for Single Bridal Car
        if (!isBarat) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isBarat) {
                        Text(
                            text = "Choose Luxury Bridal Car Model:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        WeddingCarType.values().forEach { car ->
                            val isSelected = selectedCarType == car
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color(0xFFD97706) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedCarType = car }
                                    .testTag("car_type_${car.name.lowercase()}"),
                                color = if (isSelected) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(car.modelName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(car.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = "₨ ${car.basePricePKR}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFFD97706)
                                    )
                                }
                            }
                        }
                    } else {
                        // Barat Convoy options
                        Text(
                            text = "Select Convoy Vehicle Mix (گاڑیوں کا امتزاج):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        ConvoyMixType.values().forEach { mix ->
                            val isSelected = selectedConvoyMix == mix
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedConvoyMix = mix }
                                    .testTag("convoy_mix_${mix.name.lowercase()}"),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(mix.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(mix.urduTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text("₨ ${mix.multiplierPerCarPKR}/car", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Fleet Count (قافلے میں گاڑیوں کی تعداد):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        // Interactive Fleet Counter (+ / -)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IconButton(
                                onClick = { if (baratFleetCount > 2) baratFleetCount-- },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .testTag("barat_count_decrease")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease Cars")
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$baratFleetCount Cars",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Accommodates ~${baratFleetCount * 4} Guests",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            IconButton(
                                onClick = { if (baratFleetCount < 25) baratFleetCount++ },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .testTag("barat_count_increase")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase Cars", tint = Color.White)
                            }
                        }
                    }

                    // Floral Decoration Selector
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Floral & Ribbon Decoration Tier:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    DecorationTier.values().forEach { tier ->
                        val isSelected = selectedDecoration == tier
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF10B981) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .background(if (isSelected) Color(0xFFECFDF5) else Color.Transparent)
                                .clickable { selectedDecoration = tier }
                                .padding(10.dp)
                                .testTag("decoration_tier_${tier.name.lowercase()}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(tier.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(tier.urduTitle, fontSize = 11.sp, color = Color(0xFF047857))
                            }
                            Text(
                                text = "+ ₨ ${tier.additionalPricePKR}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF047857)
                            )
                        }
                    }
                }
            }
        }

        // Event Schedule & Venue Inputs (Full Form with Validation)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Event Details & Locations (تفصیلات برائے بکنگ):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    // Customer Name with validation
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = {
                            customerName = it
                            nameError = false
                        },
                        label = { Text("Customer Full Name (گاہک کا نام) *") },
                        isError = nameError,
                        supportingText = if (nameError) { { Text("Customer name is required", color = Color(0xFFEF4444)) } } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wedding_name_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Customer Phone with validation
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = {
                            customerPhone = it
                            phoneError = false
                        },
                        label = { Text("Phone Number (+92) *") },
                        isError = phoneError,
                        supportingText = if (phoneError) { { Text("Valid mobile number is required", color = Color(0xFFEF4444)) } } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wedding_phone_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = eventDate,
                            onValueChange = { eventDate = it },
                            label = { Text("Event Date (تاریخ)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wedding_date_input"),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = eventTime,
                            onValueChange = { eventTime = it },
                            label = { Text("Event Time (وقت)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wedding_time_input"),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    OutlinedTextField(
                        value = pickupAddress,
                        onValueChange = {
                            pickupAddress = it
                            pickupError = false
                        },
                        label = { Text("Pickup Address in Jaranwala *") },
                        isError = pickupError,
                        supportingText = if (pickupError) { { Text("Pickup address is required", color = Color(0xFFEF4444)) } } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wedding_pickup_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = venueDestination,
                        onValueChange = {
                            venueDestination = it
                            venueError = false
                        },
                        label = { Text("Marriage Hall / Destination Venue *") },
                        isError = venueError,
                        supportingText = if (venueError) { { Text("Venue destination is required", color = Color(0xFFEF4444)) } } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wedding_venue_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = specialNotes,
                        onValueChange = { specialNotes = it },
                        label = { Text("Special Requests / Floral Design Notes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wedding_notes_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        // Price Summary & Submit Button
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estimated Total (کل کرایہ):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "₨ $calculatedTotalPKR",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isBarat) "Fleet ($baratFleetCount Cars):" else "Car Base Rental:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("₨ $baseRatePKR", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Decoration (${selectedDecoration.title}):",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("+ ₨ ${selectedDecoration.additionalPricePKR}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Text(
                        text = "Includes trained uniformed chauffeur, fuel in Jaranwala, and floral ribbon decoration.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            var valid = true
                            if (customerName.isBlank()) {
                                nameError = true
                                valid = false
                            }
                            if (customerPhone.isBlank() || customerPhone.length < 7) {
                                phoneError = true
                                valid = false
                            }
                            if (pickupAddress.isBlank()) {
                                pickupError = true
                                valid = false
                            }
                            if (venueDestination.isBlank()) {
                                venueError = true
                                valid = false
                            }

                            if (valid) {
                                val booking = repository.bookWeddingService(
                                    isBarat = isBarat,
                                    name = customerName,
                                    phone = customerPhone,
                                    carModel = if (isBarat) "$baratFleetCount Cars (${selectedConvoyMix.title})" else selectedCarType.modelName,
                                    carCount = if (isBarat) baratFleetCount else 1,
                                    convoyMix = selectedConvoyMix,
                                    decoration = selectedDecoration,
                                    date = eventDate,
                                    time = eventTime,
                                    pickup = pickupAddress,
                                    venue = venueDestination,
                                    notes = specialNotes
                                )
                                lastConfirmedBooking = booking
                                showSuccessDialog = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("book_wedding_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBarat) "Reserve Barat Convoy • ₨ $calculatedTotalPKR" else "Reserve Bridal Car • ₨ $calculatedTotalPKR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }

        // Active Wedding Reservations List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Wedding Reservations (بکنگ ریکارڈ):",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Active & Demo",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        items(bookings) { b ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wedding_booking_card_${b.id}"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (b.isBaratConvoy) Color(0xFFEDE9FE) else Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (b.isBaratConvoy) "Barat Convoy (${b.carCount} Cars)" else "Bridal Car",
                                    color = if (b.isBaratConvoy) Color(0xFF6D28D9) else Color(0xFFB45309),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (b.isDemoData) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("Demo", fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                        }
                        Text(
                            text = "₨ ${b.totalCostPKR}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                    Text(b.primaryCar, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Date: ${b.eventDate} at ${b.eventTime}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Venue: ${b.venueLocation}", fontSize = 12.sp)
                    Text("Client: ${b.customerName} (${b.customerPhone})", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (b.status == "CONFIRMED") Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Status: ${b.status}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (b.status == "CONFIRMED") Color(0xFF16A34A) else Color(0xFFDC2626),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (b.status == "CONFIRMED") {
                            OutlinedButton(
                                onClick = { repository.cancelWeddingBooking(b.id) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("cancel_wedding_${b.id}"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Cancel", fontSize = 11.sp, color = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Booking Registered!", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Your reservation ${lastConfirmedBooking?.id ?: ""} in Jaranwala has been recorded and synced for fleet dispatch."
                    )
                    Text(
                        "Vehicle: ${lastConfirmedBooking?.primaryCar ?: ""}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        "Total Fare: ₨ ${lastConfirmedBooking?.totalCostPKR ?: 0}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        "Our wedding captain coordinator will review the booking for $customerName ($customerPhone).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSuccessDialog = false },
                    modifier = Modifier.testTag("dialog_confirm_btn")
                ) {
                    Text("Done")
                }
            }
        )
    }
}
