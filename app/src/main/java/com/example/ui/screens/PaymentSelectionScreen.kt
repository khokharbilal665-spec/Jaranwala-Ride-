package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppLanguage
import com.example.data.JaranwalaRepository
import com.example.data.PaymentMethod
import com.example.util.PakistanPhoneValidator

/**
 * PaymentSelectionScreen:
 * Allows passengers to choose between Cash on Drop and Pakistani digital wallet options
 * (JazzCash & Easypaisa) before confirming a ride or scheduled booking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentSelectionScreen(
    repository: JaranwalaRepository,
    fareAmountPKR: Int? = null,
    onBack: () -> Unit,
    onPaymentConfirmed: (PaymentMethod) -> Unit = { onBack() },
    modifier: Modifier = Modifier
) {
    val appLang by repository.appLanguage.collectAsState()
    val isUrdu = appLang == AppLanguage.URDU
    val currentMethod by repository.selectedPaymentMethod.collectAsState()
    val savedWalletAccount by repository.selectedWalletAccount.collectAsState()
    val currentUser by repository.currentUser.collectAsState()

    var selectedMethod by remember(currentMethod) { mutableStateOf(currentMethod) }
    var walletAccountNumber by remember(savedWalletAccount, currentUser?.phoneNumber) {
        mutableStateOf(
            savedWalletAccount.ifBlank {
                currentUser?.phoneNumber?.takeIf { it != "0300-0000000" } ?: ""
            }
        )
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    BackHandler {
        onBack()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("payment_selection_screen"),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isUrdu) "ادائیگی کا طریقہ منتخب کریں" else "Select Payment Method",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (isUrdu) "نقد، جاز کیش یا ایزی پیسہ" else "Cash on Drop, JazzCash or Easypaisa",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_back_payment_selection")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
                .testTag("payment_selection_lazy_column"),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Fare / Header Summary Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF042F22), Color(0xFF065F46))
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isUrdu) "جرانوالہ رائیڈ ادائیگی" else "Jaranwala Ride Checkout",
                                    color = Color(0xFF6EE7B7),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isUrdu) "اپنا پسندیدہ ادائیگی کا ذریعہ چنیں" else "Choose how you'd like to pay",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (isUrdu) "تمام لین دین محفوظ PKR میں" else "Secure PKR settlement with Captain",
                                    color = Color(0xFFD1FAE5),
                                    fontSize = 11.sp
                                )
                            }
                            if (fareAmountPKR != null && fareAmountPKR > 0) {
                                Surface(
                                    color = Color(0xFF022C22),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF34D399))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = if (isUrdu) "کل کرایہ" else "Trip Fare",
                                            fontSize = 10.sp,
                                            color = Color(0xFFA7F3D0)
                                        )
                                        Text(
                                            text = "₨ $fareAmountPKR",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF34D399),
                                            modifier = Modifier.testTag("payment_screen_fare_amount")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section Title: Cash Option
            item {
                Text(
                    text = if (isUrdu) "نقد ادائیگی (Cash)" else "Pay with Cash",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 1. Cash Option Card
            item {
                PaymentOptionCard(
                    method = PaymentMethod.CASH,
                    isSelected = selectedMethod == PaymentMethod.CASH,
                    isUrdu = isUrdu,
                    badgeLabel = if (isUrdu) "فوری منظور" else "Recommended",
                    accentColor = Color(0xFF10B981),
                    icon = Icons.Default.Payments,
                    testTag = "payment_option_cash",
                    onClick = {
                        selectedMethod = PaymentMethod.CASH
                        validationError = null
                    }
                )
            }

            // Section Title: Digital Wallets
            item {
                Text(
                    text = if (isUrdu) "پاکستانی ڈیجیٹل والٹس (Mobile Wallets)" else "Pakistani Digital Wallets",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 2. JazzCash Option Card
            item {
                PaymentOptionCard(
                    method = PaymentMethod.JAZZCASH,
                    isSelected = selectedMethod == PaymentMethod.JAZZCASH,
                    isUrdu = isUrdu,
                    badgeLabel = "JazzCash • 03XX",
                    accentColor = Color(0xFFDC2626),
                    icon = Icons.Default.AccountBalanceWallet,
                    testTag = "payment_option_jazzcash",
                    onClick = {
                        selectedMethod = PaymentMethod.JAZZCASH
                        validationError = null
                    }
                )
            }

            // 3. Easypaisa Option Card
            item {
                PaymentOptionCard(
                    method = PaymentMethod.EASYPAISA,
                    isSelected = selectedMethod == PaymentMethod.EASYPAISA,
                    isUrdu = isUrdu,
                    badgeLabel = "Easypaisa • 03XX",
                    accentColor = Color(0xFF16A34A),
                    icon = Icons.Default.PhoneAndroid,
                    testTag = "payment_option_easypaisa",
                    onClick = {
                        selectedMethod = PaymentMethod.EASYPAISA
                        validationError = null
                    }
                )
            }

            // Wallet Mobile Account Input (Visible for JazzCash or Easypaisa)
            item {
                AnimatedVisibility(visible = selectedMethod != PaymentMethod.CASH) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wallet_account_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (selectedMethod == PaymentMethod.JAZZCASH) Color(0xFFDC2626).copy(alpha = 0.4f)
                            else Color(0xFF16A34A).copy(alpha = 0.4f)
                        )
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
                                Text(
                                    text = if (selectedMethod == PaymentMethod.JAZZCASH) {
                                        if (isUrdu) "جاز کیش اکاؤنٹ نمبر" else "JazzCash Mobile Account"
                                    } else {
                                        if (isUrdu) "ایزی پیسہ اکاؤنٹ نمبر" else "Easypaisa Mobile Account"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                val profilePhone = currentUser?.phoneNumber
                                if (!profilePhone.isNullOrBlank() && profilePhone != "0300-0000000") {
                                    TextButton(
                                        onClick = {
                                            walletAccountNumber = profilePhone
                                            validationError = null
                                        }
                                    ) {
                                        Text(
                                            text = if (isUrdu) "میرا نمبر استعمال کریں" else "Use Profile Phone",
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = walletAccountNumber,
                                onValueChange = {
                                    walletAccountNumber = it
                                    validationError = null
                                },
                                label = {
                                    Text(if (isUrdu) "موبائل نمبر (03XX-XXXXXXX)" else "Wallet Mobile Number (03XX-XXXXXXX)")
                                },
                                placeholder = { Text("0301-1234567") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wallet_account_input")
                            )

                            if (validationError != null) {
                                Surface(
                                    color = Color(0xFFFEE2E2),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("wallet_validation_error")
                                ) {
                                    Text(
                                        text = validationError!!,
                                        color = Color(0xFFB91C1C),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isUrdu)
                                        "ایپ میں کبھی MPIN درج نہ کریں۔ ${repository.getPaymentProcessor(selectedMethod).displayName} فعال ہے۔"
                                    else
                                        "Never enter MPIN here. ${repository.getPaymentProcessor(selectedMethod).displayName} active.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("payment_gateway_stub_badge")
                                )
                            }
                        }
                    }
                }
            }

            // Confirm Payment Selection Button
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (selectedMethod != PaymentMethod.CASH) {
                            val validation = PakistanPhoneValidator.validate(walletAccountNumber)
                            if (!validation.isValid) {
                                validationError = if (isUrdu)
                                    "براہِ کرم درست پاکستانی موبائل نمبر درج کریں (03XX-XXXXXXX)۔"
                                else
                                    "Please enter a valid Pakistani mobile wallet number (03XX-XXXXXXX)."
                                return@Button
                            }
                        }
                        repository.selectPaymentMethod(selectedMethod, walletAccountNumber)
                        onPaymentConfirmed(selectedMethod)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("btn_confirm_payment_method"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (selectedMethod) {
                            PaymentMethod.CASH -> Color(0xFF047857)
                            PaymentMethod.JAZZCASH -> Color(0xFFB91C1C)
                            PaymentMethod.EASYPAISA -> Color(0xFF15803D)
                        }
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isUrdu)
                            "${selectedMethod.urduTitle} منتخب کریں"
                        else
                            "Use ${selectedMethod.title}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PaymentOptionCard(
    method: PaymentMethod,
    isSelected: Boolean,
    isUrdu: Boolean,
    badgeLabel: String,
    accentColor: Color,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = if (isSelected) 0.22f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = method.title,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isUrdu) method.urduTitle else method.title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = accentColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (isUrdu) method.urduSubtitle else method.subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not Selected",
                tint = if (isSelected) accentColor else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
