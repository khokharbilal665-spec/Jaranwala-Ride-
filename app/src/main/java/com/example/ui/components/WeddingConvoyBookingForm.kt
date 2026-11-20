package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppLanguage
import com.example.data.ConvoyDecorationPreference
import com.example.data.ConvoyMixType
import com.example.data.JaranwalaRepository
import com.example.data.WeddingBooking
import com.example.data.WeddingConvoyDuration

/**
 * Specialized booking form for wedding convoys with fields for:
 * 1. Fleet Size (2 to 25 cars with quick presets and seating capacity)
 * 2. Booking Duration (4h, 8h, 12h, 24h with duration-adjusted pricing)
 * 3. Special Decoration Preferences (Roses, Orchids, Satin, Night LED, Chauffeur Only)
 * 4. Convoy vehicle mix and VIP escort features.
 */
@Composable
fun WeddingConvoyBookingForm(
    repository: JaranwalaRepository,
    appLang: AppLanguage = AppLanguage.BILINGUAL,
    onBookingSuccess: ((WeddingBooking) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var fleetSize by remember { mutableIntStateOf(5) }
    var selectedDuration by remember { mutableStateOf(WeddingConvoyDuration.FULL_BARAT_8H) }
    var selectedDecoration by remember { mutableStateOf(ConvoyDecorationPreference.FRESH_ROSES) }
    var selectedConvoyMix by remember { mutableStateOf(ConvoyMixType.STANDARD_SEDANS) }

    // Convoy Options
    var includePilotBike by remember { mutableStateOf(true) }
    var includeWindshieldStickers by remember { mutableStateOf(true) }
    var matchingRibbons by remember { mutableStateOf(true) }

    // Customer & Venue Form Fields
    val currentUser by repository.currentUser.collectAsState()
    var customerName by remember { mutableStateOf("Chaudhry Nadeem") }
    var customerPhone by remember { mutableStateOf("0300-5544332") }

    LaunchedEffect(currentUser?.uid, currentUser?.name, currentUser?.phoneNumber) {
        val user = currentUser ?: return@LaunchedEffect
        val validName = user.name.takeIf { it.isNotBlank() && !it.startsWith("Guest", ignoreCase = true) }
        val validPhone = user.phoneNumber.takeIf { it.isNotBlank() && it != "0300-0000000" }
        if (validName != null && (customerName.isBlank() || customerName == "Chaudhry Nadeem")) {
            customerName = validName
        }
        if (validPhone != null && (customerPhone.isBlank() || customerPhone == "0300-5544332")) {
            customerPhone = validPhone
        }
    }
    var eventDate by remember { mutableStateOf("18 Dec 2026") }
    var eventTime by remember { mutableStateOf("02:00 PM") }
    var pickupAddress by remember { mutableStateOf("Grain Market, Mandi Road, Jaranwala") }
    var venueDestination by remember { mutableStateOf("Royal Palm Marriage Hall, Lahore Road") }
    var specialNotes by remember { mutableStateOf("Front lead car with red roses; guest cars with matching satin door bows") }

    // Validation State
    var nameError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }
    var pickupError by remember { mutableStateOf(false) }
    var venueError by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var confirmedBooking by remember { mutableStateOf<WeddingBooking?>(null) }

    // Price Calculations
    val basePerCar = selectedConvoyMix.multiplierPerCarPKR
    val baseFleetCost = fleetSize * basePerCar
    val durationAdjustedCost = (baseFleetCost * selectedDuration.multiplier).toInt()
    val totalCostPKR = durationAdjustedCost + selectedDecoration.additionalPricePKR

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wedding_convoy_form"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form Title Banner
        Surface(
            color = Color(0xFF6D28D9).copy(alpha = 0.1f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF6D28D9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Diversity3,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (appLang == AppLanguage.URDU) "بارات قافلہ ریزرویشن فارم" else "Specialized Barat Convoy Reservation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF5B21B6)
                    )
                    Text(
                        text = if (appLang == AppLanguage.URDU) "باقاعدہ گاڑیوں کا بیڑا، دورانیہ اور خصوصی سجاوٹ" else "Configure fleet size, duration, & floral decoration",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 1. FLEET SIZE SECTION
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        text = if (appLang == AppLanguage.URDU) "۱۔ قافلے کا سائز (Fleet Size):" else "1. Convoy Fleet Size (گاڑیوں کی تعداد):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Surface(
                        color = Color(0xFFEDE9FE),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "~${fleetSize * 4} Guests Capacity",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6D28D9),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Interactive Counter with Steppers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fleet_size_selector"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = { if (fleetSize > 2) fleetSize-- },
                        modifier = Modifier
                            .size(46.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("btn_fleet_decrease")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease Fleet")
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$fleetSize Vehicles",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF6D28D9),
                            modifier = Modifier.testTag("fleet_size_count_text")
                        )
                        Text(
                            text = if (appLang == AppLanguage.URDU) "$fleetSize سجائی گئی کاریں باضابطہ ڈرائیورز" else "Uniformed Chauffeurs Included",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    IconButton(
                        onClick = { if (fleetSize < 25) fleetSize++ },
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color(0xFF6D28D9), CircleShape)
                            .testTag("btn_fleet_increase")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase Fleet", tint = Color.White)
                    }
                }

                // Quick Preset Chips
                Text(
                    text = "Quick Presets:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(3 to "3 Cars", 5 to "5 Cars", 8 to "8 Cars", 12 to "12 Cars").forEach { (count, label) ->
                        FilterChip(
                            selected = fleetSize == count,
                            onClick = { fleetSize = count },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFEDE9FE),
                                selectedLabelColor = Color(0xFF6D28D9)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. BOOKING DURATION SECTION
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (appLang == AppLanguage.URDU) "۲۔ بکنگ کا دورانیہ (Booking Duration):" else "2. Booking Duration (بکنگ کا دورانیہ):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Column(
                    modifier = Modifier.testTag("duration_selector"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WeddingConvoyDuration.values().forEach { duration ->
                        val isSelected = selectedDuration == duration
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF6D28D9) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedDuration = duration }
                                .testTag("duration_option_${duration.name.lowercase()}"),
                            color = if (isSelected) Color(0xFFF5F3FF) else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF6D28D9) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = duration.title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = duration.urduTitle,
                                            fontSize = 11.sp,
                                            color = if (isSelected) Color(0xFF6D28D9) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    color = if (isSelected) Color(0xFFEDE9FE) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${(duration.multiplier * 100).toInt()}% Rate",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFF5B21B6) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. SPECIAL DECORATION PREFERENCES SECTION
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (appLang == AppLanguage.URDU) "۳۔ خصوصی سجاوٹ و پھول (Special Decoration):" else "3. Special Decoration Preferences (آرائش و پھول):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Column(
                    modifier = Modifier.testTag("decoration_preference_selector"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConvoyDecorationPreference.values().forEach { deco ->
                        val isSelected = selectedDecoration == deco
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF047857) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedDecoration = deco }
                                .testTag("decoration_option_${deco.name.lowercase()}"),
                            color = if (isSelected) Color(0xFFECFDF5) else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = deco.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = deco.urduTitle,
                                        fontSize = 11.sp,
                                        color = Color(0xFF047857)
                                    )
                                    Text(
                                        text = deco.description,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (deco.additionalPricePKR > 0) "+ ₨ ${deco.additionalPricePKR}" else "Included (₨ 0)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (deco.additionalPricePKR > 0) Color(0xFF047857) else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. VEHICLE CONVOY MIX
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "4. Vehicle Convoy Mix (گاڑیوں کا امتزاج):",
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
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedConvoyMix = mix }
                            .testTag("convoy_mix_type_${mix.name.lowercase()}"),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
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
                            Text(
                                text = "₨ ${mix.multiplierPerCarPKR}/car",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Convoy Protocol Options
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "VIP Convoy Protocols:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = includePilotBike,
                        onCheckedChange = { includePilotBike = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6D28D9))
                    )
                    Text("Lead Pilot Escort Bike with hazard lights", fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = includeWindshieldStickers,
                        onCheckedChange = { includeWindshieldStickers = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6D28D9))
                    )
                    Text("Numbered Convoy Windshield Stickers (1-$fleetSize)", fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = matchingRibbons,
                        onCheckedChange = { matchingRibbons = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6D28D9))
                    )
                    Text("Matching procession silk ribbons for all guest vehicles", fontSize = 12.sp)
                }
            }
        }

        // 5. EVENT DETAILS & VENUES
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "5. Contact & Venue Information:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                OutlinedTextField(
                    value = customerName,
                    onValueChange = {
                        customerName = it
                        nameError = false
                    },
                    label = { Text("Contact Person Name (نام) *") },
                    isError = nameError,
                    supportingText = if (nameError) { { Text("Name is required", color = Color(0xFFEF4444)) } } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("convoy_customer_name"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = {
                        customerPhone = it
                        phoneError = false
                    },
                    label = { Text("Phone Number (+92) *") },
                    isError = phoneError,
                    supportingText = if (phoneError) { { Text("Valid phone number is required", color = Color(0xFFEF4444)) } } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("convoy_customer_phone"),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = eventDate,
                        onValueChange = { eventDate = it },
                        label = { Text("Date (تاریخ)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("convoy_event_date"),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = eventTime,
                        onValueChange = { eventTime = it },
                        label = { Text("Time (وقت)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("convoy_event_time"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                OutlinedTextField(
                    value = pickupAddress,
                    onValueChange = {
                        pickupAddress = it
                        pickupError = false
                    },
                    label = { Text("Groom House / Assembly Point in Jaranwala *") },
                    isError = pickupError,
                    supportingText = if (pickupError) { { Text("Pickup location is required", color = Color(0xFFEF4444)) } } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("convoy_pickup_location"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = venueDestination,
                    onValueChange = {
                        venueDestination = it
                        venueError = false
                    },
                    label = { Text("Marquee / Banquet Hall / Bride House *") },
                    isError = venueError,
                    supportingText = if (venueError) { { Text("Destination venue is required", color = Color(0xFFEF4444)) } } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("convoy_venue_destination"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = specialNotes,
                    onValueChange = { specialNotes = it },
                    label = { Text("Special Requests / Route Instructions") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("convoy_special_notes"),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // PRICE SUMMARY & CONFIRMATION ACTION
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
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
                    Column {
                        Text(
                            text = "Estimated Convoy Total:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "کل کرایہ برائے بارات",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "₨ $totalCostPKR",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF6D28D9),
                        modifier = Modifier.testTag("convoy_total_cost")
                    )
                }

                // Itemized Breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Fleet: $fleetSize x ${selectedConvoyMix.title}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("₨ $baseFleetCost", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Duration: ${selectedDuration.title} (${(selectedDuration.multiplier * 100).toInt()}%)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("₨ $durationAdjustedCost", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Decoration: ${selectedDecoration.title}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("+ ₨ ${selectedDecoration.additionalPricePKR}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

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
                            val escorts = mutableListOf<String>()
                            if (includePilotBike) escorts.add("Pilot Escort Bike")
                            if (includeWindshieldStickers) escorts.add("Numbered Convoy Windshield Stickers")
                            if (matchingRibbons) escorts.add("Matching Silk Ribbons")

                            val booking = repository.bookWeddingConvoy(
                                customerName = customerName.trim(),
                                customerPhone = customerPhone.trim(),
                                fleetSize = fleetSize,
                                convoyMix = selectedConvoyMix,
                                duration = selectedDuration,
                                decoration = selectedDecoration,
                                escortOptions = escorts,
                                pickupLocation = pickupAddress.trim(),
                                venueLocation = venueDestination.trim(),
                                eventDate = eventDate.trim(),
                                eventTime = eventTime.trim(),
                                specialNotes = specialNotes.trim()
                            )
                            confirmedBooking = booking
                            showConfirmationDialog = true
                            onBookingSuccess?.invoke(booking)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_submit_convoy_booking"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D28D9))
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (appLang == AppLanguage.URDU) "بارات قافلہ کنفرم کریں (₨ $totalCostPKR)" else "Confirm Convoy Booking (₨ $totalCostPKR)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    // Success Dialog on Confirmation
    if (showConfirmationDialog && confirmedBooking != null) {
        val b = confirmedBooking!!
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = if (appLang == AppLanguage.URDU) "بارات قافلہ کامیابی سے بک ہو گیا!" else "Barat Convoy Confirmed!",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Booking Reference: ${b.id}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6D28D9),
                        fontSize = 14.sp
                    )
                    Text("Fleet Size: ${b.carCount} Vehicles")
                    Text("Duration: ${b.durationLabel}")
                    Text("Decoration: ${b.decorationPreference}")
                    Text("Pickup: ${b.pickupLocation}")
                    Text("Venue: ${b.venueLocation}")
                    Text("Date & Time: ${b.eventDate} at ${b.eventTime}")
                    Text(
                        text = "Total PKR: ₨ ${b.totalCostPKR}",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF10B981),
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Saved to Firestore wedding_bookings. Uniformed chauffeurs assigned.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showConfirmationDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D28D9))
                ) {
                    Text("Done")
                }
            }
        )
    }
}
