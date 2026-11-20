package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppLanguage
import com.example.data.JaranwalaRepository
import com.example.data.RideBooking

/**
 * Interactive Feedback & Driver Rating Dialog that persists ratings, review comments,
 * and compliment badges directly to Cloud Firestore's 'rides' collection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriverRatingDialog(
    ride: RideBooking,
    repository: JaranwalaRepository,
    appLang: AppLanguage = AppLanguage.BILINGUAL,
    onDismiss: () -> Unit,
    onRatingSubmitted: (Int, String) -> Unit = { _, _ -> }
) {
    var stars by remember { mutableIntStateOf(5) }
    var reviewText by remember { mutableStateOf("") }
    val selectedTags = remember { mutableStateListOf<String>("Safe Driving / محفوظ ڈرائیونگ") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isSubmitted by remember { mutableStateOf(false) }

    val presetTags = listOf(
        "Polite Captain / شائستہ انداز",
        "Safe Driving / محفوظ ڈرائیونگ",
        "Clean Vehicle / صاف ستھری سواری",
        "Punctual / وقت کی پابندی",
        "Smooth Ride / پرسکون سفر",
        "Reasonable / مناسب کرایہ"
    )

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        modifier = Modifier.testTag("driver_rating_dialog"),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEF3C7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = if (appLang == AppLanguage.URDU) "کپتان کو ریٹنگ دیں" else "Rate Your Captain",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (appLang == AppLanguage.URDU) "جرانوالہ رائیڈ فیڈ بیک سسٹم" else "Jaranwala Ride Feedback System",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Driver Details Summary Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                text = (ride.driverName ?: "Captain").take(1),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ride.driverName ?: "Captain",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Plate: ${ride.vehiclePlate ?: "PB-JRW"} • ${ride.vehicleCategory.title}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Fare: ₨ ${ride.estimatedFarePKR} • ${ride.destination}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Star Rating Row
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { starIndex ->
                            IconButton(
                                onClick = { stars = starIndex },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("star_rating_$starIndex")
                            ) {
                                Icon(
                                    imageVector = if (starIndex <= stars) Icons.Filled.Star else Icons.Outlined.Star,
                                    contentDescription = "$starIndex Stars",
                                    tint = if (starIndex <= stars) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }

                    // Rating Sentiment Label
                    val sentimentText = when (stars) {
                        5 -> if (appLang == AppLanguage.URDU) "زبردست اور بہترین سروس! (Excellent)" else "5.0 • Excellent Experience!"
                        4 -> if (appLang == AppLanguage.URDU) "بہت اچھی سواری (Very Good)" else "4.0 • Very Good Ride"
                        3 -> if (appLang == AppLanguage.URDU) "تسلی بخش تجربہ (Average)" else "3.0 • Satisfactory Ride"
                        2 -> if (appLang == AppLanguage.URDU) "بہتری کی گنجائش ہے (Below Average)" else "2.0 • Needs Improvement"
                        else -> if (appLang == AppLanguage.URDU) "غیر تسلی بخش سروس (Poor)" else "1.0 • Poor Experience"
                    }
                    Text(
                        text = sentimentText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (stars >= 4) Color(0xFF047857) else if (stars == 3) Color(0xFFD97706) else Color(0xFFDC2626)
                    )
                }

                // Compliment / Feedback Chips
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (appLang == AppLanguage.URDU) "کیا چیز پسند آئی؟ (انتخاب کریں)" else "What went well? (Select tags)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        presetTags.forEachIndexed { index, tag ->
                            val isSelected = selectedTags.contains(tag)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFFECFDF5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFF10B981) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        if (isSelected) selectedTags.remove(tag) else selectedTags.add(tag)
                                    }
                                    .testTag("feedback_tag_$index")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF047857),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = tag,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF047857) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Optional Review Comment Input
                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { reviewText = it },
                    label = {
                        Text(
                            text = if (appLang == AppLanguage.URDU) "کپتان کے لیے تفصیلی رائے لکھیں (اختیاری)" else "Write driver review / feedback (Optional)",
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rating_comment_input"),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    maxLines = 4
                )

                AnimatedVisibility(visible = isSubmitted) {
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (appLang == AppLanguage.URDU) "شکریہ! آپ کی ریٹنگ فائر اسٹور میں محفوظ ہو گئی۔" else "Rating & feedback saved to Firestore 'rides' collection!",
                            color = Color(0xFF15803D),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSubmitting = true
                    repository.submitDriverRating(
                        rideId = ride.id,
                        driverId = ride.driverId,
                        stars = stars,
                        review = reviewText.trim(),
                        tags = selectedTags.toList()
                    ) { success ->
                        isSubmitting = false
                        isSubmitted = true
                        onRatingSubmitted(stars, reviewText.trim())
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("btn_submit_rating"),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(if (appLang == AppLanguage.URDU) "ریٹنگ جمع کرائیں" else "Submit Rating")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_skip_rating"),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSubmitting
            ) {
                Text(if (appLang == AppLanguage.URDU) "بعد میں (Skip)" else "Skip")
            }
        }
    )
}
