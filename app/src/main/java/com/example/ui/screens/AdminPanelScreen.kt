package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import com.example.R
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.example.data.AdminAuthResult
import com.example.data.JaranwalaRepository
import com.example.data.OwnerAdminLoginResult
import com.example.data.PasswordResetResult
import com.example.data.SecurityEventType
import com.example.data.UserRole
import com.example.data.VehicleCategory
import com.example.util.OwnerBiometricHelper

@Composable
fun AdminPanelScreen(
    repository: JaranwalaRepository,
    modifier: Modifier = Modifier
) {
    val currentUser by repository.currentUser.collectAsState()
    var isAuthenticated by remember(currentUser?.role) { mutableStateOf(currentUser?.role == UserRole.ADMIN) }
    var enteredPin by remember { mutableStateOf("") }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var isLockedOut by remember { mutableStateOf(false) }

    var showResetPasscodeDialog by remember { mutableStateOf(false) }
    var newPasscodeInput by remember { mutableStateOf("") }
    var confirmPasscodeInput by remember { mutableStateOf("") }
    var ownerEmailInput by remember { mutableStateOf("khokharbilal665@gmail.com") }
    var resetErrorMessage by remember { mutableStateOf<String?>(null) }
    var resetSuccessMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity
    var requiresSecondFactor by remember { mutableStateOf(false) }
    var ownerMasterKeyLoginInput by remember { mutableStateOf("") }
    var showMasterKeyLogin by remember { mutableStateOf(false) }
    var ownerMasterRecoveryKey by remember { mutableStateOf("") }

    val adminConfig by repository.adminConfig.collectAsState()
    val drivers by repository.drivers.collectAsState()
    val complaints by repository.complaints.collectAsState()
    val weddingBookings by repository.weddingBookings.collectAsState()
    val passengerHistory by repository.passengerHistory.collectAsState()
    val adminAllRides by repository.adminAllRides.collectAsState()
    val activeRide by repository.currentPassengerRide.collectAsState()
    val securityAuditLogs by repository.securityAuditLogs.collectAsState()
    val ownerSecurityAlerts by repository.ownerSecurityAlerts.collectAsState()

    val combinedAdminRides = remember(adminAllRides, passengerHistory) {
        (adminAllRides + passengerHistory).distinctBy { it.id }.sortedByDescending { it.timestamp }
    }

    var activeTab by remember { mutableIntStateOf(0) } // 0: Captains, 1: Rickshaws, 2: Passengers & Rides, 3: Weddings, 4: Payments & Fares, 5: Map & Settings, 6: Security & Logs

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            repository.listenToAdminCollections()
        } else {
            repository.detachAdminListeners()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            repository.detachAdminListeners()
        }
    }

    if (!isAuthenticated) {
        // Secure Access Gate (No exposed PIN, Rate-limited, Protected)
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_jaranwala_ride_logo),
                contentDescription = "Official Jaranwala Ride Logo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .testTag("admin_portal_logo")
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Authorized Admin Portal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Secure Jaranwala Ride Management System",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color(0xFFFEF3C7),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Confidential: Restricted to authorized operations personnel only.",
                    fontSize = 11.sp,
                    color = Color(0xFF92400E),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = enteredPin,
                onValueChange = {
                    if (!isLockedOut) {
                        enteredPin = it
                        authErrorMessage = null
                    }
                },
                label = { Text("Enter Administrator Passcode") },
                // Notice: No plaintext PIN is exposed or displayed in placeholder or hints
                placeholder = { Text("••••") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                enabled = !isLockedOut,
                isError = authErrorMessage != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_pin_input"),
                shape = RoundedCornerShape(14.dp)
            )

            // Two-Factor Authentication Prompt
            if (requiresSecondFactor || showMasterKeyLogin) {
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Two-Factor Verification Required",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = "Owner identity MAHI requires independent 2FA factor (Native Fingerprint Biometric OR Pre-registered Master Key).",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = ownerMasterKeyLoginInput,
                    onValueChange = {
                        ownerMasterKeyLoginInput = it
                        authErrorMessage = null
                    },
                    label = { Text("Owner Master Key (Alternative to Fingerprint)") },
                    placeholder = { Text("Enter configured Recovery Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("owner_master_key_input"),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            if (authErrorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = authErrorMessage ?: "",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Primary Passcode Authenticate Button
            Button(
                onClick = {
                    val result = repository.authenticateOwnerAdmin(
                        passcode = enteredPin,
                        biometricVerified = false,
                        masterKey = if (ownerMasterKeyLoginInput.isNotBlank()) ownerMasterKeyLoginInput else null,
                        context = context
                    )
                    when (result) {
                        is OwnerAdminLoginResult.Success -> {
                            isAuthenticated = true
                            authErrorMessage = null
                            isLockedOut = false
                            enteredPin = ""
                            ownerMasterKeyLoginInput = ""
                        }
                        is OwnerAdminLoginResult.RequiresSecondFactor -> {
                            requiresSecondFactor = true
                            authErrorMessage = "Passcode verified! Please touch the Fingerprint Sensor or enter your Owner Master Key."
                        }
                        is OwnerAdminLoginResult.InvalidCredentials -> {
                            authErrorMessage = "Invalid Passcode. ${result.attemptsLeft} attempt(s) remaining before security lockout."
                            isLockedOut = false
                        }
                        is OwnerAdminLoginResult.SecondFactorFailed -> {
                            authErrorMessage = result.message
                        }
                        is OwnerAdminLoginResult.LockedOut -> {
                            authErrorMessage = "Security Lockout: Account locked for ${result.remainingSeconds}s due to unauthorized attempts."
                            isLockedOut = true
                        }
                    }
                },
                enabled = !isLockedOut && enteredPin.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("admin_login_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (requiresSecondFactor) "Authorize with Master Key" else "Verify & Authenticate",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Native Biometric Fingerprint Button (CRITICAL Requirement)
            OutlinedButton(
                onClick = {
                    if (activity != null) {
                        OwnerBiometricHelper.promptOwnerFingerprint(
                            activity = activity,
                            title = "JARANWALA RIDE — Owner Verification",
                            subtitle = "Touch fingerprint sensor to verify MAHI Owner Identity",
                            negativeText = "Use Owner Master Key",
                            onSuccess = {
                                val result = if (enteredPin.isBlank() && repository.isCurrentFirebaseUserMahiOwner()) {
                                    repository.authenticateOwnerWithFirebaseSession(
                                        biometricVerified = true,
                                        context = context
                                    )
                                } else {
                                    repository.authenticateOwnerAdmin(
                                        passcode = enteredPin,
                                        biometricVerified = true,
                                        context = context
                                    )
                                }
                                when (result) {
                                    is OwnerAdminLoginResult.Success -> {
                                        isAuthenticated = true
                                        authErrorMessage = null
                                        isLockedOut = false
                                        enteredPin = ""
                                        ownerMasterKeyLoginInput = ""
                                    }
                                    is OwnerAdminLoginResult.InvalidCredentials -> {
                                        authErrorMessage = "Please enter your configured Admin Passcode first (or sign in via Firebase as verified Owner MAHI)."
                                    }
                                    is OwnerAdminLoginResult.LockedOut -> {
                                        isLockedOut = true
                                        authErrorMessage = "Security Lockout: Account locked for ${result.remainingSeconds}s."
                                    }
                                    else -> {
                                        authErrorMessage = "Two-factor owner verification incomplete."
                                    }
                                }
                            },
                            onError = { _, err ->
                                showMasterKeyLogin = true
                                authErrorMessage = "Biometric: $err. Please use Owner Master Key below."
                            },
                            onFailed = {
                                repository.recordSecurityAuditLog(
                                    SecurityEventType.BIOMETRIC_FAILED,
                                    "Fingerprint sensor rejected biometric attempt",
                                    severity = "HIGH"
                                )
                                authErrorMessage = "Fingerprint not recognized. Please retry or enter Master Key."
                            }
                        )
                    } else {
                        showMasterKeyLogin = true
                    }
                },
                enabled = !isLockedOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_biometric_login"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981))
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = "Biometric Fingerprint", modifier = Modifier.size(20.dp), tint = Color(0xFF10B981))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verify with Fingerprint (Biometric)", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Authorized Owner Passcode Setup / Reset Trigger
            OutlinedButton(
                onClick = {
                    showResetPasscodeDialog = !showResetPasscodeDialog
                    newPasscodeInput = ""
                    confirmPasscodeInput = ""
                    ownerMasterRecoveryKey = ""
                    resetErrorMessage = null
                    resetSuccessMessage = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_admin_passcode_reset_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (showResetPasscodeDialog) "Hide Passcode Configuration" else "Configure / Reset Administrator Passcode",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Owner Passcode Setup & Reset Card (Inline, Responsive, Zero-Trust Protected)
            if (showResetPasscodeDialog) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .testTag("admin_passcode_setup_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Secure Passcode Configuration", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "SECURITY NOTICE: Changing or resetting the admin password NEVER grants admin access. Password reset alone cannot unlock the account. Only the verified Owner MAHI (khokharbilal665@gmail.com) with the Master Recovery Key can authorize a reset.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Verified Owner Email
                        OutlinedTextField(
                            value = ownerEmailInput,
                            onValueChange = { ownerEmailInput = it },
                            label = { Text("Owner Email / ID") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("owner_email_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Owner Master Recovery Key (Mandatory Factor)
                        OutlinedTextField(
                            value = ownerMasterRecoveryKey,
                            onValueChange = {
                                ownerMasterRecoveryKey = it
                                resetErrorMessage = null
                            },
                            label = { Text("Owner Master Recovery Key") },
                            placeholder = { Text("Enter configured Recovery Key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("owner_recovery_key_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // New Passcode
                        OutlinedTextField(
                            value = newPasscodeInput,
                            onValueChange = {
                                if (it.length <= 8) newPasscodeInput = it
                                resetErrorMessage = null
                            },
                            label = { Text("New Administrator Passcode") },
                            placeholder = { Text("4 to 6 digits") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_admin_pin_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Confirm Passcode
                        OutlinedTextField(
                            value = confirmPasscodeInput,
                            onValueChange = {
                                if (it.length <= 8) confirmPasscodeInput = it
                                resetErrorMessage = null
                            },
                            label = { Text("Confirm New Passcode") },
                            placeholder = { Text("Repeat new passcode") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_admin_pin_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (resetErrorMessage != null) {
                            Text(
                                text = resetErrorMessage ?: "",
                                color = Color(0xFFEF4444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (resetSuccessMessage != null) {
                            Text(
                                text = resetSuccessMessage ?: "",
                                color = Color(0xFF10B981),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                if (newPasscodeInput.length < 4) {
                                    resetErrorMessage = "Passcode must be at least 4 digits"
                                } else if (newPasscodeInput != confirmPasscodeInput) {
                                    resetErrorMessage = "Passcodes do not match"
                                } else {
                                    val res = repository.resetAdminPasscodeSecurely(
                                        ownerEmail = ownerEmailInput,
                                        ownerMasterKey = ownerMasterRecoveryKey,
                                        newPasscode = newPasscodeInput
                                    )
                                    when (res) {
                                        is PasswordResetResult.Success -> {
                                            resetSuccessMessage = "Passcode updated securely for MAHI! (Note: Passcode reset does NOT grant admin access. Please authenticate using your new passcode + 2FA)."
                                            resetErrorMessage = null
                                            enteredPin = newPasscodeInput
                                        }
                                        is PasswordResetResult.RejectedUnauthorized -> {
                                            resetErrorMessage = res.reason
                                            resetSuccessMessage = null
                                        }
                                        is PasswordResetResult.InvalidMasterKey -> {
                                            resetErrorMessage = "Invalid Owner Master Recovery Key. ${res.attemptsRemaining} attempts left before lockout."
                                            resetSuccessMessage = null
                                        }
                                        is PasswordResetResult.LockedOut -> {
                                            resetErrorMessage = "Security Lockout: Account locked for ${res.remainingSeconds}s due to unauthorized reset attempts."
                                            resetSuccessMessage = null
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_new_admin_passcode_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save & Apply Passcode", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Zero-trust policy: Ordinary users cannot grant themselves admin access.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        return
    }

    // Authenticated Owner & Super Admin Dashboard
    val rickshaws = remember(drivers) {
        drivers.filter { it.vehicleCategory == VehicleCategory.RICKSHAW }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("admin_dashboard_content"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Admin Top Bar
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = Color(0xFF064E3B),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF047857), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Admin Control Center",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.testTag("admin_dashboard_title")
                            )
                            Text(
                                text = "JARANWALA RIDE • Owner: Bilal Khokhar (MAHI)",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { isAuthenticated = false },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("admin_lock_btn"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Lock", fontSize = 11.sp)
                    }
                }
            }
        }

        // MAHI Owner Profile & Creator Tribute Card (Prominent & Preserved)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("founder_profile_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF064E3B), Color(0xFF047857), Color(0xFF0F172A))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Original MAHI Profile Photograph (Preserved with Golden Border)
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .border(3.dp, Color(0xFFF59E0B), CircleShape)
                                .background(Color(0xFF0A2E22)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_jaranwala_ride_1793268661847),
                                contentDescription = "Bilal Khokhar (MAHI) - Founder, Creator & Authorized Owner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Bilal Khokhar (MAHI)",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified Owner",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Text(
                            text = "Bilal Khokhar • Application Creator, Sole Owner & Super Administrator • بانی اور مالک",
                            color = Color(0xFFFDE68A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Phone / رابطہ: +92 342 9348582 (0342-9348582)",
                            color = Color(0xFF6EE7B7),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Created by MAHI Gold Emblem
                        Surface(
                            color = Color(0xFFD97706),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Created by Bilal Khokhar (MAHI)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Sole creator, authorized owner & super administrator of JARANWALA RIDE. Operating ride hailing, auto-rickshaw network & royal wedding convoy service tailored for the proud citizens of Jaranwala, Faisalabad District, Punjab.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Owner Credentials & System Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Surface(
                                color = Color(0xFF047857),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Jaranwala, Punjab",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                color = Color(0xFF0F766E),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Owner: +92 342 9348582",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Zero-Trust Active",
                                    color = Color(0xFF34D399),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sub-tabs for Owner Management Sections
        item {
            ScrollableTabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                edgePadding = 8.dp
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Captains (${drivers.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Rickshaws (${rickshaws.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Passengers & Rides", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    text = { Text("Weddings (${weddingBookings.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 4,
                    onClick = { activeTab = 4 },
                    text = { Text("Payments & Fares", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 5,
                    onClick = { activeTab = 5 },
                    text = { Text("Map & Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 6,
                    onClick = { activeTab = 6 },
                    modifier = Modifier.testTag("tab_security_audit"),
                    text = { Text("Security & Logs (${securityAuditLogs.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        when (activeTab) {
            0 -> {
                // Captain Management
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Captain Management & Verification Queue",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${drivers.count { it.isApproved }} Active Captains",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                items(drivers) { d ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(d.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        if (d.isDemoData) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "Verified",
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text("${d.vehicleCategory.title} • ${d.vehicleModel}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(
                                    color = if (d.isApproved) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (d.isApproved) "APPROVED" else "PENDING",
                                        color = if (d.isApproved) Color(0xFF16A34A) else Color(0xFFD97706),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("CNIC: ${d.cnic}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("License: ${d.licenseNo}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Plate: ${d.plateNo} • Phone: ${d.phone}", fontSize = 11.sp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!d.isApproved) {
                                    Button(
                                        onClick = { repository.setDriverApproval(d.id, true) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("admin_approve_driver_${d.id}"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Approve Captain", fontSize = 12.sp)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { repository.setDriverApproval(d.id, false) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("admin_suspend_driver_${d.id}"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                                    ) {
                                        Text("Suspend / Revoke", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Rickshaw Management
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Rickshaw & Qingqi Fleet Network",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Jaranwala Local",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Auto-Rickshaw (چنگ چی / آٹو رکشہ) routes connecting Cinema Chowk, Katchery, Lahore Mor, Satyana Road, Grain Market and nearby chaks.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Rickshaw Rates Quick Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Active Rickshaw Fare Rate", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Base Fare: ₨ ${adminConfig.rickshawBaseFare} • Per KM: ₨ ${adminConfig.rickshawPerKm}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Fixed PKR", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }

                if (rickshaws.isEmpty()) {
                    item {
                        Text(
                            text = "No dedicated rickshaw drivers registered yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(rickshaws) { r ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(r.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Model: ${r.vehicleModel} • Plate: ${r.plateNo}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(
                                        color = if (r.isApproved) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (r.isApproved) "ONLINE" else "STANDBY",
                                            color = if (r.isApproved) Color(0xFF16A34A) else Color(0xFFD97706),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text("Phone: ${r.phone} • CNIC: ${r.cnic}", fontSize = 11.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!r.isApproved) {
                                        Button(
                                            onClick = { repository.setDriverApproval(r.id, true) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("admin_approve_driver_${r.id}"),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                                        ) {
                                            Text("Authorize Rickshaw", fontSize = 11.sp)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { repository.setDriverApproval(r.id, false) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("admin_suspend_driver_${r.id}"),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                                        ) {
                                            Text("Suspend Rickshaw", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Passenger Management & Ride Bookings
                item {
                    Text(
                        text = "Passenger Management & Real-Time Ride Bookings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Active Live Ride Telemetry
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Real-Time Ride Dispatch Status", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            val ride = activeRide
                            if (ride != null) {
                                Text("Ride ID: ${ride.id} • Status: ${ride.status}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                Text("Route: ${ride.pickup} ➔ ${ride.destination}", fontSize = 11.sp)
                                Text("Passenger: ${ride.passengerName} (${ride.passengerPhone})", fontSize = 11.sp)
                                Text("Fare: ₨ ${ride.estimatedFarePKR} • Vehicle: ${ride.vehicleCategory.title}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("No active in-progress ride dispatch currently. Fleet ready on standby across Jaranwala.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Registered Passenger Accounts & Firestore Trips Overview
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Active Passenger Session", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            val user = currentUser
                            if (user != null) {
                                Text("Name: ${user.name} • Phone: ${user.phoneNumber}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("UID: ${user.uid} • Role: ${user.role} • Verified: ${user.isVerified}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("Guest / Logged-out passenger sessions active", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (combinedAdminRides.isNotEmpty()) {
                    item {
                        Text(
                            text = "Synced Rides & Parcel Orders (${combinedAdminRides.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    items(combinedAdminRides.take(10)) { trip ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = "${trip.id} • ${if (trip.isParcelDelivery) "PARCEL (${trip.vehicleCategory.title})" else trip.vehicleCategory.title}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text("₨ ${trip.estimatedFarePKR} • ${trip.status.name}", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Text("${trip.pickup} ➔ ${trip.destination}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Passenger: ${trip.passengerName} • Payment: ${trip.paymentStatus}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Customer Support & Complaints Desk
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Customer Support & Complaints Desk (${complaints.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                if (complaints.isEmpty()) {
                    item {
                        Text(
                            text = "No open complaints found. Jaranwala riders are happy!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(complaints) { c ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Ticket: ${c.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        if (c.isDemoData) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("Support", fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                    }
                                    val isResolved = c.status.equals("RESOLVED", ignoreCase = true)
                                    Surface(
                                        color = if (isResolved) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = c.status,
                                            color = if (isResolved) Color(0xFF16A34A) else Color(0xFFDC2626),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text("Category: ${c.category} (${c.urduCategory})", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Text("Passenger: ${c.passengerName} • Ride: ${c.rideId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(c.message, fontSize = 13.sp)

                                if (!c.status.equals("RESOLVED", ignoreCase = true)) {
                                    Button(
                                        onClick = { repository.resolveComplaint(c.id) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("admin_resolve_complaint_${c.id}"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Mark as Resolved")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // Wedding Service Management
                item {
                    Text(
                        text = "Wedding Service Management & Convoy Fleet Reservations",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                items(weddingBookings) { wb ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(wb.id, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (wb.isDemoData) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Wedding Convoy", fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                }
                                Text("₨ ${wb.totalCostPKR}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(wb.primaryCar, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Client: ${wb.customerName} (${wb.customerPhone})", fontSize = 12.sp)
                            Text("Date: ${wb.eventDate} at ${wb.eventTime}", fontSize = 12.sp)
                            Text("Pickup: ${wb.pickupLocation} ➔ Venue: ${wb.venueLocation}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            4 -> {
                // Payment, Cash Settlement Ledger & Commission Management
                item {
                    AdminCashSettlementLedgerCard(
                        rides = combinedAdminRides,
                        weddingBookings = weddingBookings,
                        commissionPercent = adminConfig.platformCommissionPercent
                    )
                }
                item {
                    FareConfigEditor(repository = repository, config = adminConfig)
                }
            }

            5 -> {
                // Pakistan Map & Location Settings + App Settings & Reports
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Pakistan Map Settings Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF10B981))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pakistan Map & Location Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                Text(
                                    text = "Primary City Center: Jaranwala, Punjab, Pakistan (31.3333° N, 73.4333° E)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Nationwide Map Coverage: Complete map of Pakistan is enabled with high-resolution tiles, places autocomplete and reverse geocoding for all provinces, cities and villages.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Nearby Covered Localities: Buchiana, Syedwala, Khurrianwala, Lundianwala, Satiana, Manawala, Nankana Sahib, Mandi Jaranwala & Faisalabad District.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "✓ GPS Telemetry Active • Default Location Locked to Pakistan",
                                        color = Color(0xFF16A34A),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // App Settings and Reports Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("App Settings & Performance Reports", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                Text("Application: JARANWALA RIDE HD Edition (v2.4)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Architecture: Kotlin Jetpack Compose M3 • Edge-to-Edge Enabled", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Persistence: Local Room Database + Cloud Firestore Sync Ready", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Security: Client-Side SHA-256 Passcode Gate • Zero-Trust Policy", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedButton(
                                    onClick = {
                                        isAuthenticated = false
                                        showResetPasscodeDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Reconfigure Admin Passcode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

        6 -> {
            // Security, Immutable Identity Lock & Audit Logs
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("immutable_owner_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Immutable Owner Identity Lock",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }
                        Text(
                            text = "Super Administrator privileges are permanently and exclusively locked to MAHI. Password recovery or resets NEVER grant admin access.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Verified Owner:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text("Bilal Khokhar (MAHI)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Owner ID:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text(JaranwalaRepository.IMMUTABLE_OWNER_UID, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Registered Email:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text(JaranwalaRepository.IMMUTABLE_OWNER_EMAIL, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Phone / رابطہ:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text("${JaranwalaRepository.IMMUTABLE_OWNER_PHONE} (0342-9348582)", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Biometric Hardware TEE:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text("Android Keystore AES-256", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("2FA Requirement:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text("Strictly Enforced (Biometric / Master Key)", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (ownerSecurityAlerts.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("owner_security_alerts_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFF87171))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Owner Security Notifications (${ownerSecurityAlerts.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            ownerSecurityAlerts.forEach { alert ->
                                Text(
                                    text = "• $alert",
                                    color = Color(0xFFFECACA),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Security Audit Logs",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${securityAuditLogs.size} Events",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            items(securityAuditLogs) { log ->
                val badgeColor = when (log.severity) {
                    "CRITICAL" -> Color(0xFFDC2626)
                    "HIGH" -> Color(0xFFD97706)
                    "LOW" -> Color(0xFF059669)
                    else -> Color(0xFF2563EB)
                }

                Card(
                    modifier = Modifier.fillMaxWidth().testTag("audit_log_${log.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = log.eventType.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Surface(
                                color = badgeColor,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = log.severity,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = log.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Status: ${log.status} • ${log.clientSignature}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = java.text.SimpleDateFormat("dd MMM, HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp)),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
}

@Composable
fun FareConfigEditor(
    repository: JaranwalaRepository,
    config: com.example.data.AdminConfig
) {
    var bikeBase by remember { mutableStateOf(config.bikeBaseFare.toString()) }
    var bikeKm by remember { mutableStateOf(config.bikePerKm.toString()) }
    var rickshawBase by remember { mutableStateOf(config.rickshawBaseFare.toString()) }
    var rickshawKm by remember { mutableStateOf(config.rickshawPerKm.toString()) }
    var carBase by remember { mutableStateOf(config.carBaseFare.toString()) }
    var carKm by remember { mutableStateOf(config.carPerKm.toString()) }
    var commission by remember { mutableStateOf(config.platformCommissionPercent.toString()) }

    var savedMessage by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    text = "Configure Live PKR Rates & Commission",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Jaranwala PKR Rates",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Bike Fares
            Text("Bike Ride (موٹر سائیکل سواری):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = bikeBase,
                    onValueChange = { bikeBase = it },
                    label = { Text("Base (PKR)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = bikeKm,
                    onValueChange = { bikeKm = it },
                    label = { Text("Per KM (PKR)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // Rickshaw Fares
            Text("Auto Rickshaw (چنگ چی / آٹو رکشہ):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rickshawBase,
                    onValueChange = { rickshawBase = it },
                    label = { Text("Base (PKR)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = rickshawKm,
                    onValueChange = { rickshawKm = it },
                    label = { Text("Per KM (PKR)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // Car Fares
            Text("Car Ride AC (کار / اے سی):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = carBase,
                    onValueChange = { carBase = it },
                    label = { Text("Base (PKR)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = carKm,
                    onValueChange = { carKm = it },
                    label = { Text("Per KM (PKR)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // Commission
            Text("Platform Commission (%):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            OutlinedTextField(
                value = commission,
                onValueChange = { commission = it },
                label = { Text("Platform Service Fee (%)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Button(
                onClick = {
                    repository.updateFares(
                        bikeBase = bikeBase.toIntOrNull() ?: 70,
                        bikeKm = bikeKm.toIntOrNull() ?: 25,
                        rickshawBase = rickshawBase.toIntOrNull() ?: 120,
                        rickshawKm = rickshawKm.toIntOrNull() ?: 35,
                        carBase = carBase.toIntOrNull() ?: 220,
                        carKm = carKm.toIntOrNull() ?: 60,
                        commission = commission.toIntOrNull() ?: 10
                    )
                    savedMessage = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_fares_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.PriceChange, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Rates & Update App Fares", fontWeight = FontWeight.Bold)
            }

            if (savedMessage) {
                Text(
                    text = "✓ Fare parameters updated successfully across Jaranwala Ride!",
                    color = Color(0xFF16A34A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AdminCashSettlementLedgerCard(
    rides: List<com.example.data.RideBooking>,
    weddingBookings: List<com.example.data.WeddingBooking>,
    commissionPercent: Int
) {
    val completedRides = rides.filter { it.status == com.example.data.RideStatus.COMPLETED }
    val grossRidesCash = completedRides.sumOf { it.estimatedFarePKR }
    val digitalWalletTotalPKR = completedRides
        .filter { it.paymentMethod.equals("JAZZCASH", true) || it.paymentMethod.equals("EASYPAISA", true) }
        .sumOf { it.estimatedFarePKR }
    val platformCommissionPKR = (grossRidesCash * commissionPercent) / 100
    val netCaptainPayoutPKR = (grossRidesCash - platformCommissionPKR).coerceAtLeast(0)
    val totalParcelCodPKR = completedRides.filter { it.isParcelDelivery }.sumOf { it.codAmountPKR }
    val confirmedWeddingsCash = weddingBookings
        .filter { !it.status.equals("CANCELLED", ignoreCase = true) }
        .sumOf { it.totalCostPKR }

    val captainBreakdown = completedRides
        .groupBy { it.driverName ?: it.driverId ?: "Assigned Captain" }
        .entries
        .sortedByDescending { entry -> entry.value.sumOf { it.estimatedFarePKR } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_cash_settlement_ledger"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cash Settlement & Revenue Ledger",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "$commissionPercent% Fee",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Rides Cash Collected", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₨ $grossRidesCash", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        Text("${completedRides.size} completed trips", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    color = Color(0xFFECFDF5),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("MAHI Owner Commission", fontSize = 10.sp, color = Color(0xFF065F46))
                        Text("₨ $platformCommissionPKR", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFF047857))
                        Text("Net Captain: ₨ $netCaptainPayoutPKR", fontSize = 10.sp, color = Color(0xFF065F46))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Wedding & Barat Revenue: ₨ $confirmedWeddingsCash",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFB45309)
                )
                Text(
                    text = "Parcel COD: ₨ $totalParcelCodPKR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF047857)
                )
            }
            if (digitalWalletTotalPKR > 0) {
                Text(
                    text = "Digital Wallets (JazzCash / Easypaisa): ₨ $digitalWalletTotalPKR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1D4ED8)
                )
            }

            if (captainBreakdown.isNotEmpty()) {
                androidx.compose.material3.HorizontalDivider()
                Text("Per-Captain Cash Settlement:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                captainBreakdown.take(5).forEach { (captainName, trips) ->
                    val capGross = trips.sumOf { it.estimatedFarePKR }
                    val capComm = (capGross * commissionPercent) / 100
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("$captainName (${trips.size} trips)", fontSize = 11.sp)
                        Text(
                            text = "Cash ₨ $capGross • Due ₨ $capComm",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

