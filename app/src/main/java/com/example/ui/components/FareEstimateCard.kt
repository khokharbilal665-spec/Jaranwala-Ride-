package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AdminConfig
import com.example.data.AppLanguage
import com.example.data.VehicleCategory
import com.example.pricing.DistanceSource
import com.example.pricing.RidePricingCalculator

/**
 * Transparent Fare Estimation & Breakdown UI Component.
 * Provides clear, upfront pricing transparency using Location API distance
 * before a user confirms booking.
 */
@Composable
fun FareEstimateCard(
    distanceKm: Double,
    selectedCategory: VehicleCategory,
    onCategorySelected: (VehicleCategory) -> Unit,
    adminConfig: AdminConfig,
    appLang: AppLanguage = AppLanguage.BILINGUAL,
    pickup: String = "Committee Chowk",
    destination: String = "Jaranwala Railway Station",
    promoDiscountPKR: Int = 0,
    appliedPromoCode: String? = null,
    distanceSource: DistanceSource = DistanceSource.COORDINATE_HAVERSINE,
    apiDurationMinutes: Int? = null,
    isCalculatingDistance: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isUrdu = appLang == AppLanguage.URDU

    val breakdown = RidePricingCalculator.calculateFareBreakdown(
        category = selectedCategory,
        distanceKm = distanceKm,
        adminConfig = adminConfig,
        apiDurationMinutes = apiDurationMinutes,
        promoCode = appliedPromoCode,
        promoDiscountPKR = promoDiscountPKR,
        distanceSource = distanceSource
    )

    val baseFare = breakdown.baseFarePKR
    val perKmRate = breakdown.perKmRatePKR
    val distanceCost = breakdown.distanceChargePKR
    val totalFarePKR = breakdown.finalEstimatedFarePKR
    val estimatedMinutes = breakdown.estimatedDurationMinutes

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("fare_estimate_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF04261D)
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Title & Guarantee Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF065F46)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isUrdu) "کرایہ کا شفاف تخمینہ" else "Fare Estimate & Breakdown",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (isUrdu) "جرانوالہ شہر میں فکسڈ ریٹس" else "Fixed city rates • No hidden charges",
                            fontSize = 11.sp,
                            color = Color(0xFFA7F3D0)
                        )
                    }
                }

                Surface(
                    color = Color(0xFF065F46),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "100% Transparent",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Live Location API calculation indicator
            if (isCalculatingDistance) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .testTag("fare_distance_loading"),
                        color = Color(0xFF10B981),
                        trackColor = Color(0xFF064E3B)
                    )
                    Text(
                        text = if (isUrdu) "لوکیشن API سے سڑک کا فاصلہ حاصل کیا جا رہا ہے..." else "Fetching live road distance from Location API...",
                        fontSize = 10.sp,
                        color = Color(0xFF6EE7B7)
                    )
                }
            }

            // Quick Vehicle Category Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fare_vehicle_selector"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VehicleCategory.values().forEach { category ->
                    val isSelected = selectedCategory == category
                    val catFare = RidePricingCalculator.calculateFareBreakdown(
                        category = category,
                        distanceKm = distanceKm,
                        adminConfig = adminConfig,
                        apiDurationMinutes = apiDurationMinutes
                    ).finalEstimatedFarePKR

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFF10B981) else Color(0xFF1E3A2F),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onCategorySelected(category) }
                            .testTag("fare_option_${category.name.lowercase()}"),
                        color = if (isSelected) Color(0xFF064E3B) else Color(0xFF021B14)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = category.title,
                                tint = if (isSelected) Color(0xFF34D399) else Color(0xFF94A3B8),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (category) {
                                    VehicleCategory.BIKE -> "Bike"
                                    VehicleCategory.RICKSHAW -> "Rickshaw"
                                    VehicleCategory.CAR -> "Car AC"
                                },
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                            )
                            Text(
                                text = "₨ $catFare",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = if (isSelected) Color(0xFF10B981) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            // Route & Distance Summary Strip
            Surface(
                color = Color(0xFF031A13),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$distanceKm km",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White,
                                modifier = Modifier.testTag("fare_distance_km")
                            )
                            Text(
                                text = if (isUrdu) " (${distanceSource.urduLabel})" else " (${distanceSource.label})",
                                fontSize = 10.sp,
                                color = if (distanceSource.isLiveApi) Color(0xFF34D399) else Color(0xFF94A3B8),
                                modifier = Modifier.testTag("fare_distance_source")
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "~$estimatedMinutes mins",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White,
                                modifier = Modifier.testTag("fare_eta_minutes")
                            )
                        }
                    }

                    Text(
                        text = "$pickup → $destination",
                        fontSize = 10.sp,
                        color = Color(0xFFA7F3D0),
                        maxLines = 1
                    )
                }
            }

            // Itemized Fare Breakdown List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Base Fare
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isUrdu) "شروعاتی کرایہ (Base Fare):" else "Base Fare (${selectedCategory.title}):",
                        fontSize = 13.sp,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "₨ $baseFare",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier.testTag("fare_base_amount")
                    )
                }

                // 2. Distance Cost
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isUrdu) "فاصلہ چارجز (Distance Rate):" else "Distance Rate:",
                            fontSize = 13.sp,
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = "₨ $perKmRate / km × $distanceKm km",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Text(
                        text = "₨ $distanceCost",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier.testTag("fare_distance_amount")
                    )
                }

                // 3. Peak Surge Fee
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isUrdu) "رش چارجز (Surge Fee):" else "Peak Hour Surge:",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "₨ 0 (کوئی سرچارج نہیں)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color(0xFF34D399)
                    )
                }

                // 4. Booking & Platform fee
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isUrdu) "سروس فیس (Service Fee):" else "Platform / Service Fee:",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "₨ 0 (مفت / Free)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color(0xFF34D399)
                    )
                }

                if (promoDiscountPKR > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isUrdu) "پرومو رعایت ($appliedPromoCode):" else "Promo Discount ($appliedPromoCode):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399)
                        )
                        Text(
                            text = "- ₨ $promoDiscountPKR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF34D399),
                            modifier = Modifier.testTag("fare_promo_discount_amount")
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFF10B981).copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Total Fare Highlight
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isUrdu) "کل قابل ادا رقم (Total Fare):" else "Total Estimated Fare:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = if (isUrdu) "کیش ادائیگی سفر کے اختتام پر" else "Pay cash to Captain upon arrival",
                            fontSize = 10.sp,
                            color = Color(0xFFA7F3D0)
                        )
                    }

                    Surface(
                        color = Color(0xFF064E3B),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                    ) {
                        Text(
                            text = "₨ $totalFarePKR",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color(0xFF34D399),
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("fare_total_amount")
                        )
                    }
                }
            }

            // Trust & Transparency note from MAHI
            Surface(
                color = Color(0xFF021B14),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isUrdu)
                            "ماہی شفافیت گارنٹی: میٹر یا ایپ کے ریٹ کے مطابق ہی کرایہ لیا جائے گا۔"
                        else
                            "MAHI Guarantee: Fixed rates as calculated. No extra demands or price changes.",
                        fontSize = 10.sp,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}
