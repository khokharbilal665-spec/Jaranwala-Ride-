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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppLanguage
import com.example.data.JaranwalaRepository
import com.example.data.RideBooking

/**
 * Dedicated Post-Ride UI Component allowing passengers to rate their ride experience,
 * select quick compliment badges, leave written feedback for the captain, and archive
 * the completed ride record into Room & Firestore databases.
 */
@Composable
fun PostRideExperienceCard(
    ride: RideBooking,
    repository: JaranwalaRepository,
    appLang: AppLanguage = AppLanguage.BILINGUAL,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stars by remember { mutableIntStateOf(5) }
    var reviewText by remember { mutableStateOf("") }
    val selectedTags = remember { mutableStateListOf<String>("Safe Driving / محفوظ ڈرائیونگ") }
    var tipAmount by remember { mutableIntStateOf(0) }
    var isSubmitted by remember { mutableStateOf(false) }

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
            .border(1.5.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(22.dp))
            .testTag("post_ride_experience_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Completion Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Ride Completed",
                        tint = Color(0xFF15803D),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (appLang == AppLanguage.URDU) "سواری مکمل ہو گئی! • الحمد لله" else "Ride Completed Successfully!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF15803D)
                    )
                    Text(
                        text = if (appLang == AppLanguage.URDU) "کپتان کو ریٹنگ دیں اور اپنی رائے کا اظہار کریں" else "Rate your experience & provide captain feedback",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "₨ ${ride.estimatedFarePKR}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF92400E),
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("post_ride_fare_badge")
                    )
                }
            }

            // Captain & Vehicle Summary Box
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Captain Avatar
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF047857), Color(0xFF10B981))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (ride.driverName ?: "Captain Arshad").take(1),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
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
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = "${ride.vehicleCategory.title} • Plate: ${ride.vehiclePlate ?: "FS-23-4412"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Cash Paid ✓",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (!isSubmitted) {
                // Interactive 5-Star Rating Bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (appLang == AppLanguage.URDU) "کپتان کو اسٹار ریٹنگ دیں:" else "How was your ride experience?",
                        fontSize = 13.sp,
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
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable { stars = starIndex }
                                    .testTag("rating_star_$starIndex"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFilled) Icons.Default.Star else Icons.Outlined.Star,
                                    contentDescription = "$starIndex Stars",
                                    tint = if (isFilled) Color(0xFFF59E0B) else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }

                    // Rating Feedback Label
                    Text(
                        text = when (stars) {
                            5 -> "⭐⭐⭐⭐⭐ Outstanding / شاندار سروس"
                            4 -> "⭐⭐⭐⭐ Very Good / بہت اچھا سفر"
                            3 -> "⭐⭐⭐ Satisfactory / مناسب سروس"
                            2 -> "⭐⭐ Needs Improvement / کم تسلی بخش"
                            else -> "⭐ Unsatisfactory / غیر معیاری"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (stars) {
                            5 -> Color(0xFF15803D)
                            4 -> Color(0xFF047857)
                            3 -> Color(0xFFB45309)
                            else -> Color(0xFFB91C1C)
                        }
                    )
                }

                // Quick Compliment Chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (appLang == AppLanguage.URDU) "کپتان کے لیے تعریفی بیجز:" else "What went well? (Optional):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

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
                }

                // Feedback Written Input Field
                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { reviewText = it },
                    label = { Text("Feedback for Captain (Optional)") },
                    placeholder = { Text("Write a comment or praise for your Jaranwala driver...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_ride_feedback_input"),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    maxLines = 3
                )

                // Optional Captain Tip selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tip Captain (اختیاری انعام):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "None", 20 to "+₨20", 50 to "+₨50", 100 to "+₨100").forEach { (amount, label) ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (tipAmount == amount) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { tipAmount = amount }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tipAmount == amount) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Action Buttons: Submit & Book Another
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_submit_post_ride_feedback"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (appLang == AppLanguage.URDU) "رائے جمع کریں" else "Submit Rating", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_post_ride_book_another"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (appLang == AppLanguage.URDU) "چھوڑیں" else "Skip")
                    }
                }
            } else {
                // Post-Submission Confirmation State
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_ride_success_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Feedback Saved Successfully!",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D),
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "Your $stars-star rating and feedback were saved to Cloud Firestore & Local Room Database.",
                            color = Color(0xFF166534),
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_post_ride_book_another"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (appLang == AppLanguage.URDU) "نئی سواری بک کریں" else "Book Another Ride", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
