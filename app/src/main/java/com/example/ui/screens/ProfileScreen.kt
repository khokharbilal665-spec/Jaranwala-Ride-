package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppLanguage
import com.example.data.JaranwalaRepository
import com.example.ui.components.ProfileDetailsEditorCard
import com.example.ui.components.ProfileSosEmergencyCard
import com.google.firebase.auth.FirebaseAuth

import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import com.example.data.SavedPlace
import com.example.data.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repository: JaranwalaRepository = JaranwalaRepository.getInstance(),
    onOpenAuth: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appLang by repository.appLanguage.collectAsState()
    val currentUser by repository.currentUser.collectAsState()
    val currentRide by repository.currentPassengerRide.collectAsState()
    val savedPlaces by repository.savedPlaces.collectAsState()
    var showAddPlaceDialog by remember { mutableStateOf(false) }
    var showTestingGuide by remember { mutableStateOf(false) }

    if (onBack != null) {
        BackHandler { onBack() }
    }

    // Subscribe to real-time Firestore profile document when authenticated
    DisposableEffect(currentUser?.uid, currentUser?.isGuest) {
        val user = currentUser
        val reg = if (user != null && !user.isGuest) {
            repository.listenToUserProfile(user.uid, context)
        } else {
            null
        }
        onDispose { reg?.remove() }
    }

    val fbUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Throwable) { null }
    val isFirebaseSynced = currentUser != null && !currentUser!!.isGuest && fbUid == currentUser!!.uid

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (appLang == AppLanguage.URDU) "صارف پروفائل" else "My Profile",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (appLang == AppLanguage.URDU) "ذاتی معلومات اور ایمرجنسی رابطہ" else "Personal Details & Emergency Contact",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("btn_back_profile")
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("profile_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Prominent 1-Tap Emergency SOS Button (immediately dials saved emergency contact)
            item {
                ProfileSosEmergencyCard(
                    savedEmergencyNumber = currentUser?.emergencyContactNumber ?: "",
                    appLang = appLang,
                    onTriggerSosCall = { dialNumber ->
                        currentRide?.let { activeRide ->
                            repository.triggerEmergencyAlert(
                                ride = activeRide,
                                reason = "Profile SOS call triggered to $dialNumber"
                            )
                        }
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialNumber"))
                            )
                        } catch (_: Exception) {}
                    }
                )
            }

            // Editable User Display Name, Phone Number & Saved Emergency Contact (Firestore Synced)
            item {
                ProfileDetailsEditorCard(
                    currentUser = currentUser,
                    isFirebaseSynced = isFirebaseSynced,
                    appLang = appLang,
                    onSaveProfile = { name, phone, emergency ->
                        repository.saveUserProfileDetails(
                            displayName = name,
                            phoneNumber = phone,
                            emergencyContactNumber = emergency,
                            context = context
                        )
                    },
                    onOpenAuth = onOpenAuth
                )
            }

            // MAHI Founder & Brand Tribute Card (Preserved & Prominent)
        item {
            Spacer(modifier = Modifier.height(4.dp))
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
                        // Founder Avatar / Official App Emblem
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .border(3.dp, Color(0xFFF59E0B), CircleShape)
                                .background(Color(0xFF0A2E22)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_jaranwala_ride_logo),
                                contentDescription = "Created by MAHI Emblem",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Jaranwala Ride",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified Official",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "Your City, Your Ride • آپ کا شہر، آپ کی سواری",
                            color = Color(0xFFFDE68A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Owner: Bilal Khokhar (MAHI) • بانی و مالک",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Phone / رابطہ: +92 342 9348582 (0342-9348582)",
                            color = Color(0xFF6EE7B7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Created by MAHI Badge
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
                            text = "Dedicated ride booking, auto-rickshaw network & royal wedding convoy service tailored for the proud citizens of Jaranwala, Faisalabad District, Punjab.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // Active User & Authentication Card (Passenger & Driver Login)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_account_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF064E3B), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (currentUser != null) currentUser!!.name else "Guest User (مہمان صارف)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = if (currentUser != null) "+92 ${currentUser!!.phoneNumber}" else "Not Logged In (لاگ ان نہیں ہے)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (currentUser != null) {
                            Surface(
                                color = if (currentUser!!.role == UserRole.DRIVER) Color(0xFF0284C7) else Color(0xFF059669),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = currentUser!!.role.urduTitle,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenAuth,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_open_login_screen"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentUser == null) "لاگ ان / رجسٹریشن" else "Switch / نیا اکاؤنٹ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (currentUser != null) {
                            OutlinedButton(
                                onClick = { repository.signOut(context) },
                                modifier = Modifier.testTag("btn_sign_out"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("لاگ آؤٹ", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Saved Places Section (Home, Work, Frequent Locations)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("saved_places_card"),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = "Saved Places",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "محفوظ مقامات (Saved Places)" else "Saved Places",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "گھر یا دفتر محفوظ کریں تاکہ تیزی سے سواری بک ہو" else "Store frequent spots for faster 1-tap booking",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = { showAddPlaceDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_add_saved_place")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (appLang == AppLanguage.URDU) "نیا مقام" else "Add Place", fontSize = 12.sp)
                        }
                    }

                    if (savedPlaces.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "ابھی کوئی مقام محفوظ نہیں ہے۔ گھر یا دفتر شامل کریں!" else "No saved places yet. Add 'Home' or 'Work' for faster ride booking!",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            savedPlaces.forEachIndexed { index, place ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .testTag("saved_place_item_$index")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val icon = when (place.iconType.uppercase()) {
                                                    "HOME" -> Icons.Default.Home
                                                    "WORK" -> Icons.Default.Work
                                                    "COLLEGE" -> Icons.Default.School
                                                    "SHOP" -> Icons.Default.Storefront
                                                    else -> Icons.Default.Place
                                                }
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = place.label,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = place.label,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    if (place.urduLabel.isNotBlank()) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = place.urduLabel,
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontWeight = FontWeight.SemiBold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = place.address,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { repository.removeSavedPlace(place.id) },
                                            modifier = Modifier.testTag("btn_delete_place_${place.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete ${place.label}",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (appLang == AppLanguage.URDU)
                                "💡 اشارہ: محفوظ مقامات سواری بکنگ اسکرین پر ۱-کلک پر دستیاب ہوتے ہیں۔"
                            else
                                "💡 Tip: Saved locations appear directly on the Ride Booking screen for instant 1-tap booking.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Language & Localization Options
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Language Preference (زبان کا انتخاب):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    AppLanguage.values().forEach { lang ->
                        val isSelected = appLang == lang
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { repository.setLanguage(lang) }
                                .padding(12.dp)
                                .testTag("lang_option_${lang.name.lowercase()}"),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(lang.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(lang.urduLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Testing & Demo Guide Button
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTestingGuide = true }
                    .testTag("testing_guide_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFD97706), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Science, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Interactive Testing & Demo Guide", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("How to test Passenger, Driver, Barat & Admin features", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Jaranwala Safety & Emergency Helpline Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Emergency, contentDescription = null, tint = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Jaranwala Emergency Helplines (ہنگامی رابطے):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    // Rescue 1122
                    EmergencyRow(
                        title = "Rescue 1122 Jaranwala",
                        urdu = "ریسکیو 1122 ایمبولینس",
                        number = "1122",
                        tag = "emergency_1122",
                        onDial = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1122")))
                            } catch (_: Exception) {}
                        }
                    )

                    // Police 15
                    EmergencyRow(
                        title = "Punjab Police Emergency",
                        urdu = "پولیس ہیلپ لائن 15",
                        number = "15",
                        tag = "emergency_15",
                        onDial = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:15")))
                            } catch (_: Exception) {}
                        }
                    )

                    // Jaranwala City Police Station
                    EmergencyRow(
                        title = "City Police Station Jaranwala",
                        urdu = "تھانہ سٹی جرانوالہ",
                        number = "041-4312222",
                        tag = "emergency_police_station",
                        onDial = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0414312222")))
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }

        // About & Creator Disclosure
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("about_app_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("Jaranwala Ride v1.0.0", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Owner & Founder / بانی و مالک: Bilal Khokhar (MAHI)", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text("Owner Phone / رابطہ نمبر: +92 342 9348582 (0342-9348582)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    Text("Owner Email: khokharbilal665@gmail.com", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Map Engine: OpenStreetMap (Complete Pakistan & Village Coverage)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Headquarters: Jaranwala, District Faisalabad, Punjab, Pakistan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Created with passion by Bilal Khokhar (MAHI) for Jaranwala, Pakistan.", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    }

    if (showTestingGuide) {
        AlertDialog(
            onDismissRequest = { showTestingGuide = false },
            title = { Text("How to Run & Test Jaranwala Ride", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Passenger Mode: Select pickup (e.g. Committee Chowk) and destination (Railway Station). Pick Bike, Rickshaw or Car. Tap 'Book Ride'. The app simulates searching, matching with a local captain, arrival, trip progression, cash payment, and rating.", fontSize = 12.sp)
                    Text("2. Driver Mode: Toggle the Online switch. When online, incoming requests from the Passenger tab will alert on the radar with Accept/Decline buttons. Accept to update trip status and view live PKR earnings.", fontSize = 12.sp)
                    Text("3. Wedding & Barat Service: Choose between Single Bridal Car (Prado TX, Civic, Audi) or Multi-Car Barat Convoy. Complete the booking form with validation to generate an instant quote in PKR.", fontSize = 12.sp)
                    Text("4. Admin Panel: Enter your Administrator Passcode. Access driver approvals, edit live PKR base fares and commissions, and resolve customer tickets.", fontSize = 12.sp)
                    Text("5. Map & GPS: Tap 'Google Maps' on the map card to open real live GPS navigation in Jaranwala.", fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTestingGuide = false },
                    modifier = Modifier.testTag("close_guide_btn")
                ) {
                    Text("Understood!")
                }
            }
        )
    }

    if (showAddPlaceDialog) {
        var labelText by remember { mutableStateOf("Home") }
        var addressText by remember { mutableStateOf("Committee Chowk, Jaranwala") }
        var selectedIconType by remember { mutableStateOf("HOME") }
        var inputError by remember { mutableStateOf<String?>(null) }

        val iconOptions = listOf(
            Triple("HOME", "Home", "گھر"),
            Triple("WORK", "Work", "دفتر"),
            Triple("COLLEGE", "College", "کالج"),
            Triple("SHOP", "Shop", "دکان"),
            Triple("FAVORITE", "Favorite", "پسندیدہ")
        )

        AlertDialog(
            onDismissRequest = { showAddPlaceDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (appLang == AppLanguage.URDU) "نیا مقام محفوظ کریں" else "Save New Place",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (appLang == AppLanguage.URDU)
                            "گھر یا دفتر محفوظ کریں تاکہ اگلی بار صرف ۱-کلک پر سواری بک ہو سکے۔"
                        else
                            "Save frequent locations like Home or Work for instant 1-tap ride booking.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Type Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        iconOptions.take(4).forEach { (type, name, urdu) ->
                            val isSel = selectedIconType == type
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedIconType = type
                                        labelText = name
                                        addressText = when (type) {
                                            "HOME" -> "Committee Chowk, Jaranwala"
                                            "WORK" -> "Katchery Bazar, Jaranwala"
                                            "COLLEGE" -> "Government Degree College, Jaranwala"
                                            "SHOP" -> "Grain Market, Mandi Road"
                                            else -> "Jaranwala Railway Station"
                                        }
                                    }
                                    .testTag("type_chip_${type.lowercase()}"),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (appLang == AppLanguage.URDU) urdu else name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Label Input
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = {
                            labelText = it
                            inputError = null
                        },
                        label = { Text(if (appLang == AppLanguage.URDU) "نام / لیبل (Label)" else "Location Label (e.g. Home, Work)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_saved_place_label"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Address Input
                    OutlinedTextField(
                        value = addressText,
                        onValueChange = {
                            addressText = it
                            inputError = null
                        },
                        label = { Text(if (appLang == AppLanguage.URDU) "پتہ (Address in Jaranwala)" else "Address / Landmark in Jaranwala") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_saved_place_address"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Quick Landmark Suggestions
                    Text(
                        text = if (appLang == AppLanguage.URDU) "مشہور مقامات سے چنیں:" else "Quick Suggestions:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(repository.jaranwalaLocations.take(5)) { loc ->
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { addressText = loc.name },
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = if (appLang == AppLanguage.URDU) loc.urduName else loc.name,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (inputError != null) {
                        Text(
                            text = inputError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (labelText.isBlank() || addressText.isBlank()) {
                            inputError = "Please enter both label and address"
                            return@Button
                        }
                        repository.addSavedPlace(
                            label = labelText.trim(),
                            address = addressText.trim(),
                            iconType = selectedIconType
                        )
                        showAddPlaceDialog = false
                    },
                    modifier = Modifier.testTag("btn_confirm_add_place")
                ) {
                    Text(if (appLang == AppLanguage.URDU) "محفوظ کریں" else "Save Place")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAddPlaceDialog = false },
                    modifier = Modifier.testTag("btn_cancel_add_place")
                ) {
                    Text(if (appLang == AppLanguage.URDU) "منسوخ" else "Cancel")
                }
            }
        )
    }
}

@Composable
fun EmergencyRow(
    title: String,
    urdu: String,
    number: String,
    tag: String,
    onDial: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onDial() }
            .padding(10.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(urdu, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(number, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}
