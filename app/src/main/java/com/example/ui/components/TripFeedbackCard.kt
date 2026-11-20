package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppLanguage
import com.example.data.RideBooking

private const val MAX_REVIEW_CHARS = 200

/**
 * Simple, focused post-trip feedback UI component shown after a ride is marked COMPLETED.
 * Lets passengers rate their trip with a 1-to-5 star bar and enter a short review message.
 */
@Composable
fun TripFeedbackCard(
    ride: RideBooking,
    appLang: AppLanguage = AppLanguage.BILINGUAL,
    onSubmitFeedback: (stars: Int, review: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUrdu = appLang == AppLanguage.URDU
    var selectedStars by remember(ride.id) {
        mutableIntStateOf(if (ride.ratingGiven in 1..5) ride.ratingGiven else 5)
    }
    var reviewText by remember(ride.id) { mutableStateOf(ride.reviewGiven) }
    var isSubmitted by remember(ride.id, ride.ratingGiven) { mutableStateOf(ride.ratingGiven > 0) }

    val starLabel = when (selectedStars) {
        5 -> if (isUrdu) "بہترین سفر (5/5)" else "Excellent Ride (5/5)"
        4 -> if (isUrdu) "اچھا تجربہ (4/5)" else "Good Experience (4/5)"
        3 -> if (isUrdu) "مناسب سفر (3/5)" else "Okay Trip (3/5)"
        2 -> if (isUrdu) "بہتری کی گنجائش (2/5)" else "Could Be Better (2/5)"
        else -> if (isUrdu) "ناخوشگوار تجربہ (1/5)" else "Needs Improvement (1/5)"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trip_feedback_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Trip Completed",
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isUrdu) "سفر مکمل • اپنی رائے دیں" else "Trip Completed • Rate Your Ride",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${ride.driverName ?: "Captain"} • ₨ ${ride.estimatedFarePKR}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isSubmitted) {
                Surface(
                    color = Color(0xFFECFDF5),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trip_feedback_submitted_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isUrdu) {
                                    "شکریہ! آپ نے $selectedStars/5 اسٹار ریٹنگ دی"
                                } else {
                                    "Thank you! You rated this trip $selectedStars/5 stars"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF065F46)
                            )
                        }
                        if (reviewText.isNotBlank()) {
                            Text(
                                text = "\"${reviewText.trim()}\"",
                                fontSize = 12.sp,
                                color = Color(0xFF047857)
                            )
                        }
                    }
                }
            } else {
                // Interactive 5-Star Rating Bar (48dp touch targets)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.testTag("trip_feedback_star_bar"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { starIndex ->
                            val filled = starIndex <= selectedStars
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { selectedStars = starIndex }
                                    .semantics {
                                        contentDescription = "Rate $starIndex stars"
                                    }
                                    .testTag("trip_feedback_star_$starIndex"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "$starIndex Star",
                                    tint = if (filled) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = starLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706),
                        modifier = Modifier.testTag("trip_feedback_rating_label")
                    )
                }

                // Short Review Text Field
                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { updated ->
                        if (updated.length <= MAX_REVIEW_CHARS) {
                            reviewText = updated
                        }
                    },
                    label = {
                        Text(
                            if (isUrdu) "اپنا مختصر تبصرہ لکھیں (اختیاری)" else "Write a short review (optional)"
                        )
                    },
                    placeholder = {
                        Text(
                            if (isUrdu) "مثلاً: محفوظ ڈرائیونگ اور وقت کی پابندی" else "e.g., Smooth ride and polite captain"
                        )
                    },
                    supportingText = {
                        Text("${reviewText.length}/$MAX_REVIEW_CHARS")
                    },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trip_feedback_review_input")
                )

                // Submit Button
                Button(
                    onClick = {
                        val trimmedReview = reviewText.trim()
                        isSubmitted = true
                        onSubmitFeedback(selectedStars, trimmedReview)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_submit_trip_feedback"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF047857)
                    )
                ) {
                    Text(
                        text = if (isUrdu) "فیڈبیک جمع کروائیں" else "Submit Feedback",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
