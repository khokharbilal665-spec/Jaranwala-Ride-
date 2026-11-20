package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.PaymentMethod
import com.example.pricing.RidePriceBreakdown

/**
 * Pre-booking confirmation dialog shown before dispatching the ride request.
 * Presents the Location API distance, travel duration, route summary, and
 * full itemized PKR price breakdown so passengers confirm with complete clarity.
 */
@Composable
fun RideBookingConfirmationDialog(
    pickup: String,
    destination: String,
    breakdown: RidePriceBreakdown,
    appLang: AppLanguage,
    onConfirmBooking: () -> Unit,
    onDismiss: () -> Unit,
    scheduledDateTimeDisplay: String? = null,
    isParcelDelivery: Boolean = false,
    parcelDescription: String = "",
    recipientName: String = "",
    recipientPhone: String = "",
    codAmountPKR: Int = 0,
    paymentMethod: PaymentMethod = PaymentMethod.CASH
) {
    val isUrdu = appLang == AppLanguage.URDU
    val isScheduled = !scheduledDateTimeDisplay.isNullOrBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ride_booking_confirmation_dialog"),
        containerColor = Color(0xFF04261D),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE2E8F0),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF10B981), Color(0xFF059669))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = breakdown.category.icon,
                        contentDescription = breakdown.category.title,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = when {
                            isParcelDelivery -> if (isUrdu) "پارسل ڈلیوری کی تصدیق" else "Confirm Parcel & Courier Fare"
                            isScheduled -> if (isUrdu) "شیڈول بُکنگ کی تصدیق" else "Confirm Scheduled Ride"
                            else -> if (isUrdu) "بُکنگ کی تصدیق کریں" else "Confirm Your Ride & Fare"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                    Text(
                        text = "${if (isParcelDelivery) "Parcel Courier • " else ""}${breakdown.category.title} • ${breakdown.category.urduTitle}",
                        fontSize = 11.sp,
                        color = Color(0xFF6EE7B7)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isParcelDelivery) {
                    Surface(
                        color = Color(0xFF065F46),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF34D399)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parcel_confirm_badge")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (isUrdu) "پارسل وصول کنندہ (Recipient)" else "Pickup & Delivery (Parcel)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF6EE7B7)
                            )
                            Text(
                                text = "To: $recipientName ($recipientPhone)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (parcelDescription.isNotBlank()) {
                                Text(
                                    text = "Contents: $parcelDescription",
                                    fontSize = 11.sp,
                                    color = Color(0xFFD1FAE5)
                                )
                            }
                            if (codAmountPKR > 0) {
                                Text(
                                    text = "Cash on Delivery (COD): ₨ $codAmountPKR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFBBF24)
                                )
                            }
                        }
                    }
                }
                if (isScheduled) {
                    Surface(
                        color = Color(0xFF065F46),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("schedule_confirm_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = scheduledDateTimeDisplay ?: "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFDE68A)
                            )
                        }
                    }
                }

                // Route Card
                Surface(
                    color = Color(0xFF021B14),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF065F46)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = pickup,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = destination,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        HorizontalDivider(color = Color(0xFF064E3B))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${breakdown.distanceKm} km",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399),
                                    modifier = Modifier.testTag("confirm_dialog_distance")
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "~${breakdown.estimatedDurationMinutes} mins",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFBBF24)
                                )
                            }
                        }
                        Text(
                            text = if (isUrdu) breakdown.distanceSource.urduLabel else breakdown.distanceSource.label,
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Itemized Fare Breakdown
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isUrdu) "شروعاتی کرایہ (Base):" else "Base Fare:",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Text(
                            text = "₨ ${breakdown.baseFarePKR}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${breakdown.distanceKm} km × ₨ ${breakdown.perKmRatePKR}/km:",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Text(
                            text = "₨ ${breakdown.distanceChargePKR}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    if (breakdown.promoDiscountPKR > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Promo (${breakdown.promoCode ?: "OFFER"}):",
                                fontSize = 12.sp,
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "- ₨ ${breakdown.promoDiscountPKR}",
                                fontSize = 12.sp,
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    HorizontalDivider(
                        color = Color(0xFF10B981).copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isUrdu) "کل قابل ادا کرایہ:" else "Estimated Total:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = Color(0xFFA7F3D0),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isUrdu) paymentMethod.urduTitle else paymentMethod.title,
                                    fontSize = 10.sp,
                                    color = Color(0xFFA7F3D0),
                                    modifier = Modifier.testTag("confirm_dialog_payment_method")
                                )
                            }
                        }
                        Surface(
                            color = Color(0xFF065F46),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Text(
                                text = "₨ ${breakdown.finalEstimatedFarePKR}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF34D399),
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("confirm_dialog_total_fare")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmBooking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("confirm_booking_dialog_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isScheduled) {
                        if (isUrdu) "شیڈول تصدیق • ₨ ${breakdown.finalEstimatedFarePKR}" else "Schedule Ride • ₨ ${breakdown.finalEstimatedFarePKR}"
                    } else {
                        if (isUrdu) "تصدیق کریں • ₨ ${breakdown.finalEstimatedFarePKR}" else "Confirm Ride • ₨ ${breakdown.finalEstimatedFarePKR}"
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, Color(0xFF64748B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("cancel_booking_dialog_btn")
            ) {
                Text(
                    text = if (isUrdu) "ترمیم / منسوخ" else "Edit",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    )
}
