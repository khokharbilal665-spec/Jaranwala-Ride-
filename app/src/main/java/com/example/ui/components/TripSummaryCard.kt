package com.example.ui.components

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppLanguage
import com.example.data.JaranwalaRepository
import com.example.data.RideBooking
import com.example.data.VehicleCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Trip Summary Card displayed immediately after a ride is finished.
 * Displays the total fare, route distance, travel duration, complete booking details,
 * and allows the passenger to review their ride experience and submit captain feedback.
 */
@Composable
fun TripSummaryCard(
    ride: RideBooking,
    repository: JaranwalaRepository,
    appLang: AppLanguage = AppLanguage.BILINGUAL,
    onDismiss: () -> Unit,
    onViewHistory: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isUrdu = appLang == AppLanguage.URDU

    var stars by remember { mutableIntStateOf(if (ride.ratingGiven > 0) ride.ratingGiven else 5) }
    var reviewText by remember { mutableStateOf(ride.reviewGiven) }
    val selectedTags = remember { mutableStateListOf<String>("Safe Driving / محفوظ ڈرائیونگ") }
    var isSubmitted by remember { mutableStateOf(ride.ratingGiven > 0) }
    var showDetailedBreakdown by remember { mutableStateOf(false) }

    val formattedDate = remember(ride.timestamp) {
        try {
            val sdf = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault())
            sdf.format(Date(ride.timestamp))
        } catch (_: Throwable) {
            "Today, Jaranwala"
        }
    }

    val estimatedDurationMin = remember(ride.estimatedDistanceKm) {
        (ride.estimatedDistanceKm * 3.5).toInt().coerceAtLeast(5)
    }

    val presetTags = listOf(
        "Polite Captain / شائستہ اخلاق",
        "Safe Driving / محفوظ ڈرائیونگ",
        "Clean Vehicle / صاف ستھری سواری",
        "Punctual / وقت کی پابندی",
        "Fair Fare / مناسب کرایہ",
        "Smooth AC / پرسکون سفر"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(2.dp, Color(0xFF10B981).copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .testTag("trip_summary_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top Banner: Trip Summary Header & Booking ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = "Trip Summary",
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isUrdu) "سفر کا خلاصہ (Trip Summary)" else "Trip Summary (خلاصہ)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = formattedDate,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Booking ID Badge
                Surface(
                    color = Color(0xFF064E3B),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "#${ride.id}",
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("trip_summary_id_badge")
                    )
                }
            }

            // 2. Primary KPI Banner: Total Fare & Route Distance
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF03261C)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Total Fare
                    Column {
                        Text(
                            text = if (isUrdu) "کل کرایہ (Total Fare)" else "Total Fare (کل کرایہ)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "₨",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${ride.estimatedFarePKR}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.testTag("trip_summary_fare_text")
                            )
                            if (ride.discountPKR > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFFD97706),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "-₨${ride.discountPKR}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        val resolvedPayMethod = com.example.data.PaymentMethod.fromFirestore(ride.paymentMethod)
                        Text(
                            text = if (isUrdu) {
                                "${resolvedPayMethod.urduTitle} • ادا شدہ"
                            } else {
                                "${resolvedPayMethod.title} • ${if (ride.paymentStatus == "PAID") "Paid" else "Settled"}"
                            },
                            fontSize = 10.sp,
                            color = Color(0xFF34D399)
                        )
                    }

                    // Route Distance & Duration
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (isUrdu) "کل فاصلہ (Distance)" else "Total Distance",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${String.format(Locale.US, "%.1f", ride.estimatedDistanceKm)} km",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.testTag("trip_summary_distance_text")
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "~$estimatedDurationMin mins travel time",
                                fontSize = 10.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            // 3. Route Details: Pickup & Destination
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Origin / Pickup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDCFCE7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Pickup Location",
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isUrdu) "پک اپ کا مقام (Pickup Location):" else "Pickup Location:",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = ride.pickup,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("trip_summary_pickup_text")
                            )
                        }
                    }

                    // Route connector
                    Box(
                        modifier = Modifier
                            .padding(start = 11.dp)
                            .height(14.dp)
                            .width(2.dp)
                            .background(Color(0xFF94A3B8))
                    )

                    // Destination / Drop-off
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEE2E2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Destination",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isUrdu) "منزل کا مقام (Drop-off Destination):" else "Destination:",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = ride.destination,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("trip_summary_destination_text")
                            )
                        }
                    }
                }
            }

            // 4. Vehicle & Captain Booking Details
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Vehicle Type Icon Box
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF047857)),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (ride.vehicleCategory) {
                            VehicleCategory.BIKE -> Icons.Default.DirectionsBike
                            VehicleCategory.RICKSHAW -> Icons.Default.ElectricRickshaw
                            VehicleCategory.CAR -> Icons.Default.DirectionsCar
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = ride.vehicleCategory.title,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ride.driverName ?: "Captain Arshad",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified Captain",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${ride.driverRating}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${ride.vehicleCategory.title} • Plate: ${ride.vehiclePlate ?: "FS-23-4412"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Passenger: ${ride.passengerName} (${ride.passengerPhone})",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Direct Phone dialer
                    val driverPhone = ride.driverPhone ?: "0300-1122334"
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$driverPhone"))
                                    context.startActivity(intent)
                                } catch (_: Throwable) {}
                            }
                            .testTag("btn_trip_summary_call_driver"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call Captain",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 5. Expandable Fare Breakdown Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDetailedBreakdown = !showDetailedBreakdown },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUrdu) "کرایہ کی تفصیلی رسید دیکھیں" else "View Detailed Fare Receipt",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (showDetailedBreakdown) "▲" else "▼",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = showDetailedBreakdown) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Base Fare (${ride.vehicleCategory.title})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₨ ${ride.vehicleCategory.baseFarePKR}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        val distanceCost = (ride.estimatedDistanceKm * ride.vehicleCategory.perKmRatePKR).toInt()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Distance (${String.format(Locale.US, "%.1f", ride.estimatedDistanceKm)} km × ₨${ride.vehicleCategory.perKmRatePKR})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₨ $distanceCost", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        if (ride.discountPKR > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Promo Discount (${ride.promoCode ?: "PROMO"})", fontSize = 11.sp, color = Color(0xFF16A34A))
                                Text("- ₨ ${ride.discountPKR}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (isUrdu) "کل ادا شدہ رقم" else "Final Amount Paid", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("₨ ${ride.estimatedFarePKR}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF15803D))
                        }
                    }
                }
            }

            // 6. Post-Ride Review and Rating Section
            if (!isSubmitted) {
                HorizontalDivider()
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isUrdu) "کپتان کو ریٹنگ اور فیڈ بیک دیں:" else "Rate your Captain & Ride Experience:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { starIndex ->
                            val isFilled = starIndex <= stars
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable { stars = starIndex }
                                    .testTag("summary_rating_star_$starIndex"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFilled) Icons.Default.Star else Icons.Outlined.Star,
                                    contentDescription = "$starIndex Stars",
                                    tint = if (isFilled) Color(0xFFF59E0B) else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    // Compliment badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetTags.take(3).forEach { tag ->
                            val isSelected = selectedTags.contains(tag)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) selectedTags.remove(tag) else selectedTags.add(tag)
                                },
                                label = { Text(tag.substringBefore("/").trim(), fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFDCFCE7),
                                    selectedLabelColor = Color(0xFF15803D)
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = reviewText,
                        onValueChange = { reviewText = it },
                        label = { Text("Comment or praise (اختیاری رائے)") },
                        placeholder = { Text("Very good safe driving in Jaranwala...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("summary_feedback_input"),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        maxLines = 3
                    )

                    Button(
                        onClick = {
                            val combinedReview = if (selectedTags.isNotEmpty()) {
                                if (reviewText.isNotBlank()) "$reviewText • [${selectedTags.joinToString(", ")}]" else selectedTags.joinToString(", ")
                            } else reviewText

                            repository.submitDriverRating(
                                rideId = ride.id,
                                driverId = ride.driverId,
                                stars = stars,
                                review = combinedReview,
                                tags = selectedTags.toList()
                            )
                            isSubmitted = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_trip_summary_submit_rating"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isUrdu) "ریٹنگ جمع کرائیں" else "Submit Rating & Review", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUrdu) "آپ کا $stars اسٹار ریویو اور فیڈ بیک محفوظ کر لیا گیا ہے۔" else "Your $stars-star rating and feedback were saved successfully!",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    }
                }
            }

            // 7. Action Footer: Book Another Ride & Done
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_trip_summary_book_another")
                        .testTag("btn_post_ride_book_another"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF064E3B))
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isUrdu) "نئی سواری بک کریں (Book Another)" else "Book Another Ride (نئی سواری)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                if (onViewHistory != null) {
                    OutlinedButton(
                        onClick = onViewHistory,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_trip_summary_view_history"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isUrdu) "ریکارڈ" else "History", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
