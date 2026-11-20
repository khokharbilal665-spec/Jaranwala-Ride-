package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Fingerprint
import androidx.fragment.app.FragmentActivity
import com.example.util.OwnerBiometricHelper
import com.example.data.OwnerAdminLoginResult
import com.example.data.SecurityEventType
import com.example.data.AdminAuthResult
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.HelpOutline
import com.example.util.PakistanFirebasePhoneAuthHelper
import com.example.util.findActivity
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import com.example.R
import com.example.data.AppLanguage
import com.example.data.DriverRegDetails
import com.example.data.JaranwalaRepository
import com.example.data.UserRole
import com.example.data.VehicleCategory
import com.example.util.PakistanPhoneValidator

enum class AuthMode {
    LOGIN, REGISTER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    repository: JaranwalaRepository = JaranwalaRepository.getInstance(),
    onAuthComplete: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    initialMode: AuthMode = AuthMode.LOGIN,
    initialRole: UserRole = UserRole.PASSENGER,
    allowGuestContinue: Boolean = true,
    showAdminRoleOption: Boolean = true,
    headerSubtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val appLang by repository.appLanguage.collectAsState()
    val isUrdu = appLang == AppLanguage.URDU

    var selectedMode by remember(initialMode) { mutableStateOf(initialMode) }
    var selectedRole by remember(initialRole) { mutableStateOf(initialRole) }

    var phoneNumber by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }

    // Driver specific fields
    var vehicleCategory by remember { mutableStateOf(VehicleCategory.RICKSHAW) }
    var vehicleModel by remember { mutableStateOf("Sazgar 4-Stroke 2023") }
    var plateNumber by remember { mutableStateOf("FS-23-4412") }
    var cnicNumber by remember { mutableStateOf("33102-1234567-1") }
    var licenseNumber by remember { mutableStateOf("PB-JRW-5521") }

    // OTP states
    var showOtpDialog by remember { mutableStateOf(false) }
    var enteredOtp by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf(false) }
    var otpErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }
    var resendCountdown by remember { mutableIntStateOf(30) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showForgotAccountDialog by remember { mutableStateOf(false) }
    var firebaseVerificationId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val phoneAuthHelper = remember { PakistanFirebasePhoneAuthHelper(repository = repository) }
    val activity = remember(context) { context.findActivity() }
    var isGoogleSigningIn by remember { mutableStateOf(false) }
    var googleStatusText by remember { mutableStateOf<String?>(null) }

    // Active session, profile avatar, and secure admin login
    val currentUser by repository.currentUser.collectAsState()
    val activeDriver by repository.activeDriver.collectAsState()
    var selectedAvatar by remember { mutableStateOf("avatar_classic") }
    var adminPasscode by remember { mutableStateOf("") }
    var adminMasterKey by remember { mutableStateOf("") }
    var requiresAdmin2FA by remember { mutableStateOf(false) }
    var showAdminPasscode by remember { mutableStateOf(false) }
    var adminAuthError by remember { mutableStateOf<String?>(null) }
    var isVerifyingAdmin by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF031A12),
                        Color(0xFF064E3B),
                        Color(0xFF0A2E22)
                    )
                )
            )
            .testTag("auth_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dismiss or Close button if modal
            if (onDismiss != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_auth")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Top Official Logo & Branding Header (Strictly preserved official JR logo)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .border(3.dp, Color(0xFF10B981), RoundedCornerShape(26.dp))
                        .background(Color(0xFF022C22)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_jaranwala_ride_logo),
                        contentDescription = "Jaranwala Ride Official Logo - Created by MAHI",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "JARANWALA RIDE",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp
                )

                Text(
                    text = headerSubtitle ?: if (isUrdu) "آپ کا شہر، آپ کی سواری • جرانوالہ" else "Your City, Your Ride • Jaranwala",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF34D399)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = Color(0xFFD97706),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Created by MAHI",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            // Active User Profile & Session Card with Log Out
            currentUser?.let { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_user_session_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF042F2E)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF10B981))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = Color(0xFF10B981),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val avatarEmoji = when (user.photoUrl) {
                                        "avatar_executive" -> "👔"
                                        "avatar_student" -> "🎓"
                                        "avatar_captain" -> "🚖"
                                        "avatar_lady" -> "🧕"
                                        "avatar_vip" -> "⭐"
                                        else -> "👤"
                                    }
                                    Text(avatarEmoji, fontSize = 22.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFF065F46),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = user.role.title,
                                            color = Color(0xFFD1FAE5),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (user.phoneNumber.isNotBlank()) user.phoneNumber else if (user.email?.isNotBlank() == true) user.email else "Guest Session",
                                    color = Color(0xFF34D399),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (user.role == UserRole.DRIVER && !activeDriver.isApproved) {
                            Surface(
                                color = Color(0xFF78350F).copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pending_driver_approval_banner")
                            ) {
                                Text(
                                    text = if (isUrdu)
                                        "کپتان رجسٹریشن زیرِ غور ہے: ماہی ایڈمن کی منظوری تک سواری قبول کرنا بند ہے۔"
                                    else
                                        "Captain Approval Pending: Online status remains blocked until MAHI Admin approves your documents.",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    repository.signOut(context)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("btn_logout"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF991B1B),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "Log Out", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isUrdu) "لاگ آؤٹ (Logout)" else "Log Out", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = onAuthComplete,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("btn_continue_to_app"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.Black
                                )
                            ) {
                                Text(if (isUrdu) "داخل ہوں" else "Continue", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Mode Selector: Login vs Register
            TabRow(
                selectedTabIndex = if (selectedMode == AuthMode.LOGIN) 0 else 1,
                containerColor = Color(0xFF022C22),
                contentColor = Color(0xFF10B981),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[if (selectedMode == AuthMode.LOGIN) 0 else 1]),
                        color = Color(0xFF10B981),
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = selectedMode == AuthMode.LOGIN,
                    onClick = {
                        selectedMode = AuthMode.LOGIN
                        validationError = null
                    },
                    text = {
                        Text(
                            text = if (isUrdu) "لاگ ان (Login)" else "Login (لاگ ان)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (selectedMode == AuthMode.LOGIN) Color.White else Color(0xFF9CA3AF)
                        )
                    },
                    modifier = Modifier.testTag("toggle_mode_login")
                )
                Tab(
                    selected = selectedMode == AuthMode.REGISTER,
                    onClick = {
                        selectedMode = AuthMode.REGISTER
                        validationError = null
                    },
                    text = {
                        Text(
                            text = if (isUrdu) "نئی رجسٹریشن (Register)" else "Register (رجسٹریشن)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (selectedMode == AuthMode.REGISTER) Color.White else Color(0xFF9CA3AF)
                        )
                    },
                    modifier = Modifier.testTag("toggle_mode_register")
                )
            }

            // Role Selector: Passenger vs Driver
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF042F2E).copy(alpha = 0.85f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isUrdu) "اکاؤنٹ کی قسم منتخب کریں:" else "Select Account Role:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E8F0)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Passenger Option
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (selectedRole == UserRole.PASSENGER) 2.dp else 1.dp,
                                    color = if (selectedRole == UserRole.PASSENGER) Color(0xFF10B981) else Color(0xFF334155),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedRole = UserRole.PASSENGER }
                                .testTag("toggle_role_passenger"),
                            color = if (selectedRole == UserRole.PASSENGER) Color(0xFF064E3B) else Color(0xFF0F172A)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = if (selectedRole == UserRole.PASSENGER) Color(0xFF34D399) else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Passenger",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "سواری",
                                    fontSize = 10.sp,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }

                        // Driver Option
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (selectedRole == UserRole.DRIVER) 2.dp else 1.dp,
                                    color = if (selectedRole == UserRole.DRIVER) Color(0xFF10B981) else Color(0xFF334155),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedRole = UserRole.DRIVER }
                                .testTag("toggle_role_driver"),
                            color = if (selectedRole == UserRole.DRIVER) Color(0xFF064E3B) else Color(0xFF0F172A)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ElectricRickshaw,
                                    contentDescription = null,
                                    tint = if (selectedRole == UserRole.DRIVER) Color(0xFF34D399) else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Captain",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "کپتان",
                                    fontSize = 10.sp,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }

                        // Admin Option
                        if (showAdminRoleOption) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (selectedRole == UserRole.ADMIN) 2.dp else 1.dp,
                                        color = if (selectedRole == UserRole.ADMIN) Color(0xFFF59E0B) else Color(0xFF334155),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedRole = UserRole.ADMIN }
                                    .testTag("toggle_role_admin"),
                                color = if (selectedRole == UserRole.ADMIN) Color(0xFF78350F) else Color(0xFF0F172A)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = if (selectedRole == UserRole.ADMIN) Color(0xFFFBBF24) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Admin",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "مالک / ایڈمن",
                                        fontSize = 10.sp,
                                        color = Color(0xFFFBBF24)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Owner Admin Login Screen when ADMIN role selected
            if (selectedRole == UserRole.ADMIN) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("admin_login_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF062A20)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFD97706)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isUrdu) "مالک و ایڈمنسٹریٹر تصدیق" else "Owner Admin Authentication",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isUrdu) "محفوظ ایڈمن پاس کوڈ درج کریں" else "Enter authorized security passcode",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = adminPasscode,
                            onValueChange = {
                                adminPasscode = it
                                adminAuthError = null
                            },
                            label = { Text(if (isUrdu) "ایڈمن پاس کوڈ (Admin Passcode)" else "Admin Security Passcode", color = Color(0xFF94A3B8)) },
                            placeholder = { Text("••••", color = Color.Gray) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFF59E0B))
                            },
                            trailingIcon = {
                                IconButton(onClick = { showAdminPasscode = !showAdminPasscode }) {
                                    Icon(
                                        imageVector = if (showAdminPasscode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Passcode Visibility",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            },
                            visualTransformation = if (showAdminPasscode) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_passcode_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFF59E0B),
                                unfocusedBorderColor = Color(0xFF1E3A2F),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        if (requiresAdmin2FA) {
                            OutlinedTextField(
                                value = adminMasterKey,
                                onValueChange = {
                                    adminMasterKey = it
                                    adminAuthError = null
                                },
                                label = { Text(if (isUrdu) "اونر ماسٹر کی (Master Key)" else "Owner Master Key (Alternative)", color = Color(0xFF94A3B8)) },
                                placeholder = { Text("Enter configured Recovery Key", color = Color.Gray) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("owner_master_key_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF10B981),
                                    unfocusedBorderColor = Color(0xFF1E3A2F),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }

                        adminAuthError?.let { err ->
                            Surface(
                                color = Color(0xFF7F1D1D),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("admin_auth_error_banner")
                            ) {
                                Text(
                                    text = err,
                                    color = Color(0xFFFECACA),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (adminPasscode.isBlank()) {
                                    adminAuthError = if (isUrdu) "براہ کرم پاس کوڈ درج کریں" else "Please enter your passcode"
                                    return@Button
                                }
                                isVerifyingAdmin = true
                                val result = repository.authenticateOwnerAdmin(
                                    passcode = adminPasscode,
                                    biometricVerified = false,
                                    masterKey = if (adminMasterKey.isNotBlank()) adminMasterKey else null,
                                    context = context
                                )
                                isVerifyingAdmin = false
                                when (result) {
                                    is OwnerAdminLoginResult.Success -> {
                                        adminAuthError = null
                                        onAuthComplete()
                                    }
                                    is OwnerAdminLoginResult.RequiresSecondFactor -> {
                                        requiresAdmin2FA = true
                                        adminAuthError = if (isUrdu) "پاس کوڈ درست ہے! فنگر پرنٹ یا ماسٹر کی درج کریں۔" else "Passcode verified! Fingerprint Biometric or Owner Master Key required."
                                    }
                                    is OwnerAdminLoginResult.LockedOut -> {
                                        adminAuthError = if (isUrdu) "سیکیورٹی لاک آؤٹ فعال ہے۔ ${result.remainingSeconds} سیکنڈ بعد کوشش کریں۔" else "Security lockout active. Try again in ${result.remainingSeconds}s."
                                    }
                                    is OwnerAdminLoginResult.InvalidCredentials -> {
                                        adminAuthError = if (isUrdu) "غلط پاس کوڈ! ${result.attemptsLeft} کوششیں باقی ہیں۔" else "Incorrect Passcode! ${result.attemptsLeft} attempts remaining."
                                    }
                                    is OwnerAdminLoginResult.SecondFactorFailed -> {
                                        adminAuthError = result.message
                                    }
                                }
                            },
                            enabled = !isVerifyingAdmin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_admin_login"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD97706),
                                contentColor = Color.White
                            )
                        ) {
                            if (isVerifyingAdmin) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Verifying...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isUrdu) "ایڈمن پورٹل کھولیں (Unlock Portal)" else "Unlock Owner Admin Portal",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Native Biometric Fingerprint Trigger for Admin
                        OutlinedButton(
                            onClick = {
                                val fragmentAct = context as? FragmentActivity
                                if (fragmentAct != null) {
                                    OwnerBiometricHelper.promptOwnerFingerprint(
                                        activity = fragmentAct,
                                        title = "JARANWALA RIDE — Owner Verification",
                                        subtitle = "Touch fingerprint sensor to verify Owner MAHI",
                                        negativeText = "Use Master Key",
                                        onSuccess = {
                                            val res = if (adminPasscode.isBlank() && repository.isCurrentFirebaseUserMahiOwner()) {
                                                repository.authenticateOwnerWithFirebaseSession(
                                                    biometricVerified = true,
                                                    context = context
                                                )
                                            } else {
                                                repository.authenticateOwnerAdmin(
                                                    passcode = adminPasscode,
                                                    biometricVerified = true,
                                                    context = context
                                                )
                                            }
                                            if (res is OwnerAdminLoginResult.Success) {
                                                adminAuthError = null
                                                onAuthComplete()
                                            } else if (res is OwnerAdminLoginResult.InvalidCredentials) {
                                                adminAuthError = if (isUrdu) "براہ کرم پہلے درست پاس کوڈ درج کریں" else "Please enter your Admin Passcode first."
                                            }
                                        },
                                        onError = { _, err ->
                                            requiresAdmin2FA = true
                                            adminAuthError = "Biometric: $err"
                                        },
                                        onFailed = {
                                            adminAuthError = if (isUrdu) "فنگر پرنٹ مسترد ہو گیا" else "Fingerprint rejected. Please retry or enter Master Key."
                                        }
                                    )
                                } else {
                                    requiresAdmin2FA = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_biometric_login")
                                .testTag("btn_biometric_admin_auth"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = "Biometric Fingerprint", modifier = Modifier.size(20.dp), tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isUrdu) "فنگر پرنٹ بائیو میٹرک تصدیق" else "Verify with Biometric Fingerprint",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            } else {
                // Form Fields Card for Passenger and Driver
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF062A20)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Registration Full Name
                        if (selectedMode == AuthMode.REGISTER) {
                            Column {
                                Text(
                                    text = if (isUrdu) "مکمل نام (Full Name):" else "Full Name (مکمل نام):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCBD5E1)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = fullName,
                                    onValueChange = { fullName = it },
                                    placeholder = { Text(if (selectedRole == UserRole.DRIVER) "Captain Tariq Mahmood" else "Ahmed Raza", color = Color.Gray) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF10B981))
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("name_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF10B981),
                                        unfocusedBorderColor = Color(0xFF1E3A2F),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }

                            // Profile Avatar Selection
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isUrdu) "پروفائل اوتار منتخب کریں (Choose Avatar):" else "Choose Profile Avatar:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCBD5E1)
                                )
                                val avatarChoices = listOf(
                                    Triple("avatar_classic", "👤 Classic", "کلاسک"),
                                    Triple("avatar_executive", "👔 Executive", "ایگزیکٹو"),
                                    Triple("avatar_student", "🎓 Student", "طالب علم"),
                                    Triple("avatar_captain", "🚖 Captain", "کپتان"),
                                    Triple("avatar_lady", "🧕 Lady", "خاتون"),
                                    Triple("avatar_vip", "⭐ VIP", "وی آئی پی")
                                )
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.testTag("avatar_selector")
                                ) {
                                    items(avatarChoices.size) { idx ->
                                        val item = avatarChoices[idx]
                                        val isSelected = selectedAvatar == item.first
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) Color(0xFF064E3B) else Color(0xFF0F172A),
                                            border = androidx.compose.foundation.BorderStroke(
                                                if (isSelected) 2.dp else 1.dp,
                                                if (isSelected) Color(0xFF10B981) else Color(0xFF334155)
                                            ),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { selectedAvatar = item.first }
                                                .testTag("avatar_chip_${item.first}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.second,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color(0xFF34D399) else Color(0xFFE2E8F0)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    // Mobile Number Input with Pakistan +92 prefix
                    Column {
                        Text(
                            text = if (isUrdu) "پاکستانی موبائل نمبر (Mobile Number):" else "Mobile Number (+92):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { input ->
                                if (input.length <= 15) phoneNumber = input
                            },
                            placeholder = { Text("0300-1234567", color = Color.Gray) },
                            leadingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, end = 6.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFF065F46),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "🇵🇰 +92",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phone_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF1E3A2F),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        val phoneValidation = remember(phoneNumber) {
                            if (phoneNumber.length >= 4) {
                                PakistanPhoneValidator.validate(phoneNumber)
                            } else null
                        }

                        if (phoneValidation != null && phoneValidation.isValid) {
                            Surface(
                                color = Color(0xFF065F46),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${phoneValidation.operatorName} • ${phoneValidation.localFormatted}",
                                        color = Color(0xFFD1FAE5),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Driver Specific Registration Details
                    if (selectedMode == AuthMode.REGISTER && selectedRole == UserRole.DRIVER) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (isUrdu) "گاڑی کی کیٹیگری:" else "Vehicle Category:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCBD5E1)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                VehicleCategory.values().forEach { cat ->
                                    FilterChip(
                                        selected = vehicleCategory == cat,
                                        onClick = { vehicleCategory = cat },
                                        label = {
                                            Text(
                                                text = when (cat) {
                                                    VehicleCategory.BIKE -> "Bike (بائیک)"
                                                    VehicleCategory.RICKSHAW -> "Rickshaw (رکشہ)"
                                                    VehicleCategory.CAR -> "Car AC (کار)"
                                                },
                                                fontSize = 11.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = cat.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF10B981),
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                }
                            }

                            // Vehicle Model & Plate
                            OutlinedTextField(
                                value = vehicleModel,
                                onValueChange = { vehicleModel = it },
                                label = { Text("Vehicle Model (ماڈل)", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("vehicle_model_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF10B981),
                                    unfocusedBorderColor = Color(0xFF1E3A2F),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            OutlinedTextField(
                                value = plateNumber,
                                onValueChange = { plateNumber = it },
                                label = { Text("Registration Plate No. (نمبر پلیٹ)", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("plate_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF10B981),
                                    unfocusedBorderColor = Color(0xFF1E3A2F),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            // CNIC & Driving License
                            OutlinedTextField(
                                value = cnicNumber,
                                onValueChange = { cnicNumber = it },
                                label = { Text("CNIC No. (شناختی کارڈ نمبر: 33102-xxxxxxx-x)", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("cnic_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF10B981),
                                    unfocusedBorderColor = Color(0xFF1E3A2F),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            OutlinedTextField(
                                value = licenseNumber,
                                onValueChange = { licenseNumber = it },
                                label = { Text("Driving License (ڈرائیونگ لائسنس نمبر)", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("license_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF10B981),
                                    unfocusedBorderColor = Color(0xFF1E3A2F),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }

                    // Validation error banner
                    validationError?.let { err ->
                        Surface(
                            color = Color(0xFF7F1D1D),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {}
                                .testTag("auth_validation_error_banner")
                        ) {
                            Text(
                                text = err,
                                color = Color(0xFFFECACA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Send SMS OTP Action Button
                    Button(
                        onClick = {
                            val validation = PakistanPhoneValidator.validate(phoneNumber)
                            if (!validation.isValid) {
                                validationError = if (isUrdu) (validation.urduErrorMessage ?: "غلط موبائل نمبر") else (validation.errorMessage ?: "Please enter a valid Pakistan mobile number")
                                return@Button
                            }
                            if (selectedMode == AuthMode.REGISTER && fullName.isBlank()) {
                                validationError = if (isUrdu) "براہ کرم اپنا پورا نام درج کریں" else "Please enter your full name"
                                return@Button
                            }
                            if (selectedMode == AuthMode.REGISTER && selectedRole == UserRole.DRIVER) {
                                val cleanCnic = cnicNumber.replace("-", "").trim()
                                if (cleanCnic.length != 13 || !cleanCnic.all { it.isDigit() }) {
                                    validationError = if (isUrdu) "قومی شناختی کارڈ نمبر ۱۳ ہندسوں پر مشتمل ہونا چاہیے" else "CNIC number must be exactly 13 digits (e.g. 33102-1234567-1)"
                                    return@Button
                                }
                                if (vehicleModel.isBlank() || plateNumber.isBlank() || licenseNumber.isBlank()) {
                                    validationError = if (isUrdu) "براہ کرم گاڑی، نمبر پلیٹ اور لائسنس کی تفصیل درج کریں" else "Please enter vehicle model, plate number, and license number"
                                    return@Button
                                }
                            }
                            validationError = null
                            otpError = false
                            otpErrorMessage = null
                            isSendingOtp = true
                            phoneNumber = validation.localFormatted
                            enteredOtp = ""
                            resendCountdown = 60

                            val driverDetails = if (selectedMode == AuthMode.REGISTER && selectedRole == UserRole.DRIVER) {
                                DriverRegDetails(
                                    cnic = cnicNumber.trim(),
                                    licenseNo = licenseNumber.trim(),
                                    vehicleCategory = vehicleCategory,
                                    vehicleModel = vehicleModel.trim(),
                                    plateNo = plateNumber.trim()
                                )
                            } else null

                            if (activity != null) {
                                phoneAuthHelper.sendVerificationCode(
                                    activity = activity,
                                    rawPhoneNumber = validation.normalizedE164,
                                    selectedRole = selectedRole,
                                    fullName = fullName,
                                    photoUrl = selectedAvatar,
                                    driverDetails = driverDetails,
                                    onCodeSent = { vId ->
                                        firebaseVerificationId = vId
                                        isSendingOtp = false
                                        showOtpDialog = true
                                    },
                                    onInstantVerified = { _ ->
                                        isSendingOtp = false
                                        showOtpDialog = false
                                        onAuthComplete()
                                    },
                                    onError = { err, urduErr ->
                                        isSendingOtp = false
                                        validationError = if (isUrdu) urduErr else err
                                    }
                                )
                            } else if (PakistanFirebasePhoneAuthHelper.isRobolectricEnvironment()) {
                                repository.requestPhoneOtp(validation.localFormatted)
                                firebaseVerificationId = "ROBOLECTRIC-VERIF-${System.currentTimeMillis()}"
                                isSendingOtp = false
                                showOtpDialog = true
                            } else {
                                isSendingOtp = false
                                validationError = if (isUrdu) "تصدیق کے لیے ایکٹو اسکرین دستیاب نہیں ہے۔" else "Unable to launch Firebase SMS verification on this context."
                            }
                        },
                        enabled = !isSendingOtp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_send_otp"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.Black
                        )
                    ) {
                        if (isSendingOtp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isUrdu) "کوڈ ارسال ہو رہا ہے..." else "Sending OTP Code...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isUrdu) "ایس ایم ایس کوڈ بھیجیں (Send OTP)" else "Send Verification Code (OTP)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // Forgot / Recover Account Link (Requirement 10)
                    TextButton(
                        onClick = { showForgotAccountDialog = true },
                        modifier = Modifier.testTag("btn_forgot_recover_account")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isUrdu) "اکاؤنٹ بحال کریں / ہیلپ ڈیسک (Forgot Account?)" else "Forgot or Recover Account? • ہیلپ ڈیسک",
                                color = Color(0xFF34D399),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            }

            // Alternative: Google Sign-In & Guest Exploration
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isUrdu) "یا دوسرا طریقہ منتخب کریں" else "Or continue with",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )

                // Google Sign-In via Firebase Auth and Jetpack Credential Manager
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            isGoogleSigningIn = true
                            validationError = null
                            googleStatusText = if (isUrdu) "گوگل کے ساتھ تصدیق ہو رہی ہے..." else "Signing in with Google..."
                            val defaultName = if (fullName.isNotBlank()) fullName else (if (selectedRole == UserRole.DRIVER) "Captain Google" else "Passenger Google")
                            if (PakistanFirebasePhoneAuthHelper.isRobolectricEnvironment()) {
                                repository.loginWithGoogle("user.jaranwala@gmail.com", defaultName, selectedRole)
                                isGoogleSigningIn = false
                                googleStatusText = null
                                onAuthComplete()
                                return@launch
                            }
                            try {
                                val webClientId = context.getString(R.string.default_web_client_id)
                                val credentialManager = CredentialManager.create(context)
                                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = webClientId)
                                    .build()

                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(signInOption)
                                    .build()

                                val credentialContext = activity ?: context
                                val result = credentialManager.getCredential(context = credentialContext, request = request)
                                val cred = result.credential
                                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                    val tokenCred = GoogleIdTokenCredential.createFrom(cred.data)
                                    val authCred = GoogleAuthProvider.getCredential(tokenCred.idToken, null)
                                    FirebaseAuth.getInstance().signInWithCredential(authCred)
                                        .addOnSuccessListener { authResult ->
                                            val fbUser = authResult.user
                                            isGoogleSigningIn = false
                                            googleStatusText = null
                                            if (fbUser != null) {
                                                repository.setUserFromFirebase(fbUser, selectedRole, context)
                                                onAuthComplete()
                                            } else {
                                                validationError = "Google sign-in returned an empty user session."
                                            }
                                        }
                                        .addOnFailureListener { ex ->
                                            isGoogleSigningIn = false
                                            googleStatusText = null
                                            val errCode = (ex as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
                                            validationError = if (errCode == "ERROR_OPERATION_NOT_ALLOWED") {
                                                if (isUrdu) {
                                                    "فائربیس کنسول میں Google Sign-In بند ہے۔ براہ کرم Authentication → Sign-in method میں Google فعال کریں۔"
                                                } else {
                                                    "Google Sign-In is disabled in Firebase Console (ERROR_OPERATION_NOT_ALLOWED). Enable Google under Authentication → Sign-in method."
                                                }
                                            } else {
                                                ex.localizedMessage ?: "Firebase Google sign-in failed."
                                            }
                                        }
                                } else {
                                    isGoogleSigningIn = false
                                    googleStatusText = null
                                    validationError = "Unexpected Google credential type returned."
                                }
                            } catch (e: GetCredentialCancellationException) {
                                Log.w("AuthScreen", "Google Sign-In cancelled: ${e.message}", e)
                                isGoogleSigningIn = false
                                googleStatusText = null
                            } catch (e: Exception) {
                                Log.e("AuthScreen", "Google Sign-In failed", e)
                                isGoogleSigningIn = false
                                googleStatusText = null
                                validationError = e.localizedMessage ?: "Google Sign-In failed. Please try again."
                            }
                        }
                    },
                    enabled = !isGoogleSigningIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_google_signin"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("G ", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFFFBBF24))
                        Text(
                            text = if (isGoogleSigningIn) (googleStatusText ?: "Signing in...")
                            else (if (isUrdu) "گوگل کے ساتھ لاگ ان کریں (Sign In with Google)" else "Sign In with Google (Firebase)"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Continue as Guest / Skip
                if (allowGuestContinue) {
                    OutlinedButton(
                        onClick = {
                            repository.continueAsGuest(selectedRole, context)
                            onAuthComplete()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_skip_auth"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFF94A3B8)
                        )
                    ) {
                        Text(
                            text = if (isUrdu) "مہمان کے طور پر دیکھیں (Continue as Guest)" else "Continue as Guest (مہمان)",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // OTP Verification Dialog
        if (showOtpDialog) {
            LaunchedEffect(showOtpDialog, resendCountdown) {
                if (showOtpDialog && resendCountdown > 0) {
                    kotlinx.coroutines.delay(1000L)
                    resendCountdown--
                }
            }

            AlertDialog(
                onDismissRequest = { if (!isVerifyingOtp) showOtpDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUrdu) "موبائل تصدیقی کوڈ (OTP)" else "SMS Verification Code",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (isUrdu)
                                "ہم نے آپ کے نمبر ($phoneNumber) پر ۶ ہندسوں کا تصدیقی ایس ایم ایس کوڈ بھیجا ہے۔"
                            else
                                "Enter the 6-digit SMS verification code sent via Firebase to $phoneNumber",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = enteredOtp,
                            onValueChange = {
                                if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                                    enteredOtp = it
                                    otpError = false
                                    otpErrorMessage = null
                                }
                            },
                            placeholder = { Text("• • • • • •") },
                            isError = otpError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("otp_input"),
                            label = { Text(if (isUrdu) "۶ ہندسوں کا کوڈ درج کریں" else "Enter 6-digit SMS Code") }
                        )

                        // Resend OTP Countdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (resendCountdown > 0) {
                                Text(
                                    text = if (isUrdu) "دوبارہ کوڈ: $resendCountdown سیکنڈ" else "Resend code in ${resendCountdown}s",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            } else {
                                Text(
                                    text = if (isUrdu) "کوڈ دوبارہ بھیجیں (Resend)" else "Resend Code (دوبارہ بھیجیں)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier
                                        .clickable {
                                            val validation = PakistanPhoneValidator.validate(phoneNumber)
                                            val resendDriverDetails = if (selectedMode == AuthMode.REGISTER && selectedRole == UserRole.DRIVER) {
                                                DriverRegDetails(
                                                    cnic = cnicNumber.trim(),
                                                    licenseNo = licenseNumber.trim(),
                                                    vehicleCategory = vehicleCategory,
                                                    vehicleModel = vehicleModel.trim(),
                                                    plateNo = plateNumber.trim()
                                                )
                                            } else null
                                            if (validation.isValid && activity != null) {
                                                phoneAuthHelper.sendVerificationCode(
                                                    activity = activity,
                                                    rawPhoneNumber = validation.normalizedE164,
                                                    selectedRole = selectedRole,
                                                    fullName = fullName,
                                                    photoUrl = selectedAvatar,
                                                    driverDetails = resendDriverDetails,
                                                    isResend = true,
                                                    onCodeSent = { vId ->
                                                        firebaseVerificationId = vId
                                                        resendCountdown = 60
                                                        otpError = false
                                                        otpErrorMessage = null
                                                    },
                                                    onInstantVerified = { _ ->
                                                        showOtpDialog = false
                                                        onAuthComplete()
                                                    },
                                                    onError = { err, urduErr ->
                                                        otpError = true
                                                        otpErrorMessage = if (isUrdu) urduErr else err
                                                    }
                                                )
                                            } else if (validation.isValid && PakistanFirebasePhoneAuthHelper.isRobolectricEnvironment()) {
                                                repository.requestPhoneOtp(validation.localFormatted)
                                                resendCountdown = 60
                                            } else {
                                                otpError = true
                                                otpErrorMessage = if (isUrdu) "دوبارہ کوڈ بھیجنے کے لیے درست نمبر درکار ہے۔" else "Unable to resend OTP without an active screen."
                                            }
                                        }
                                        .testTag("btn_resend_otp")
                                )
                            }
                        }

                        if (otpError) {
                            Text(
                                text = otpErrorMessage ?: if (isUrdu)
                                    "غلط تصدیقی کوڈ! براہ کرم ایس ایم ایس میں موصول ہونے والا ۶ ہندسوں کا کوڈ درج کریں۔"
                                else
                                    "Invalid verification code. Please check the 6-digit SMS code sent to your phone.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("otp_error_text")
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (enteredOtp.length < 6) {
                                otpError = true
                                otpErrorMessage = if (isUrdu) "براہ کرم ۶ ہندسوں کا مکمل کوڈ درج کریں" else "Please enter all 6 digits."
                                return@Button
                            }
                            isVerifyingOtp = true
                            val driverDetails = if (selectedRole == UserRole.DRIVER) {
                                DriverRegDetails(
                                    cnic = cnicNumber.trim(),
                                    licenseNo = licenseNumber.trim(),
                                    vehicleCategory = vehicleCategory,
                                    vehicleModel = vehicleModel.trim(),
                                    plateNo = plateNumber.trim()
                                )
                            } else null

                            phoneAuthHelper.verifyOtpAndSignIn(
                                otpCode = enteredOtp,
                                selectedRole = selectedRole,
                                fullName = fullName,
                                photoUrl = selectedAvatar,
                                driverDetails = driverDetails,
                                verificationId = firebaseVerificationId,
                                context = context,
                                onSuccess = { _ ->
                                    isVerifyingOtp = false
                                    showOtpDialog = false
                                    onAuthComplete()
                                },
                                onFailure = { msg, urduMsg ->
                                    isVerifyingOtp = false
                                    otpError = true
                                    otpErrorMessage = if (isUrdu) urduMsg else msg
                                }
                            )
                        },
                        enabled = !isVerifyingOtp,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
                        modifier = Modifier.testTag("btn_verify_otp")
                    ) {
                        if (isVerifyingOtp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (isUrdu) "تصدیق کریں اور داخل ہوں" else "Verify & Continue", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showOtpDialog = false }, enabled = !isVerifyingOtp) {
                        Text(if (isUrdu) "منسوخ" else "Cancel")
                    }
                }
            )
        }

        // Recover Account Dialog (Requirement 10)
        if (showForgotAccountDialog) {
            AlertDialog(
                onDismissRequest = { showForgotAccountDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUrdu) "اکاؤنٹ بحالی و ہیلپ ڈیسک" else "Account Recovery & Support",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (isUrdu)
                                "اگر آپ کا موبائل نمبر گم ہو گیا ہے، سم تبدیل ہو گئی ہے یا کیپٹن اکاؤنٹ کی منظوری درکار ہے، تو مندرجہ ذیل طریقے استعمال کریں:"
                            else
                                "If you lost your SIM card, changed your mobile number, or need captain account verification assistance:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            color = Color(0xFF064E3B).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (isUrdu) "1. مسافر و کپتان فون نمبر بحالی" else "1. Passenger & Captain Phone Recovery",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF10B981)
                                )
                                Text(
                                    text = if (isUrdu)
                                        "جرانوالہ رائیڈ 24/7 ہیلپ لائن 0342-9348582 (+92 342 9348582) پر رابطہ کریں یا کسٹمر سینٹر جناح روڈ جرانوالہ پر اپنا قومی شناختی کارڈ دکھا کر نیا نمبر منسلک کروائیں۔"
                                    else
                                        "Contact Jaranwala Ride 24/7 Helpline at +92 342 9348582 (0342-9348582) with your original CNIC to update your registered phone number.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFFD97706).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (isUrdu) "2. کپتان اکاؤنٹ منظوری کی صورتحال" else "2. Captain Pending Approval Check",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFD97706)
                                )
                                Text(
                                    text = if (isUrdu)
                                        "نئے کپتانوں کے کوائف (CNIC اور لائسنس) ماہی ایڈمن ۲ گھنٹے میں تصدیق کرتے ہیں۔ تصدیق کے بعد ایس ایم ایس موصول ہوگا۔"
                                    else
                                        "New captain registrations are verified by MAHI Admin within 2 hours. You will receive an SMS confirmation once approved.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (isUrdu) "3. بلال کھوکھر (ماہی) اونر ایڈمن اکاؤنٹ ریکوری" else "3. Bilal Khokhar (MAHI) Owner Admin 2FA Recovery",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF38BDF8)
                                )
                                Text(
                                    text = if (isUrdu)
                                        "ایڈمن اکاؤنٹ صرف رجسٹرڈ ماسٹر ریکوری کی (Master Key) اور بائیو میٹرک تصدیق سے ہی بحال ہو سکتا ہے۔"
                                    else
                                        "Owner Admin access can only be unlocked using the Immutable Master Recovery Key and 2FA factor.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showForgotAccountDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
                    ) {
                        Text(if (isUrdu) "سمجھ آ گئی" else "Understood")
                    }
                }
            )
        }
    }
}
