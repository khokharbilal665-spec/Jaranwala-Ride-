package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppLanguage
import com.example.data.UserProfile
import kotlinx.coroutines.launch

/**
 * Prominent 1-tap Emergency SOS card for ProfileScreen that immediately places a call
 * to the user's saved emergency contact number.
 */
@Composable
fun ProfileSosEmergencyCard(
    savedEmergencyNumber: String,
    appLang: AppLanguage,
    onTriggerSosCall: (cleanDialNumber: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUrdu = appLang == AppLanguage.URDU
    val trimmedNumber = savedEmergencyNumber.trim()
    var showMissingNumberWarning by remember(trimmedNumber) { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile_sos_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
        border = BorderStroke(2.dp, Color(0xFFEF4444)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDC2626)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SOS",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isUrdu) "فوری ایمرجنسی کال (SOS)" else "Emergency SOS Call",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (trimmedNumber.isNotBlank()) {
                            if (isUrdu) "محفوظ نمبر: $trimmedNumber" else "Saved SOS Contact: $trimmedNumber"
                        } else {
                            if (isUrdu) "نیچے ایمرجنسی نمبر محفوظ کریں" else "No emergency contact saved yet"
                        },
                        color = Color(0xFFFECACA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("profile_sos_number_label")
                    )
                }
            }

            AnimatedVisibility(visible = showMissingNumberWarning && trimmedNumber.isBlank()) {
                Surface(
                    color = Color(0xFF450A0A),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag("profile_sos_warning")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUrdu) {
                                "پہلے نیچے ذاتی معلومات میں اپنا ایمرجنسی نمبر درج کر کے محفوظ کریں۔"
                            } else {
                                "Please save an emergency contact number below first to enable 1-tap SOS calling."
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (trimmedNumber.isNotBlank()) {
                        showMissingNumberWarning = false
                        val dialTarget = trimmedNumber.replace(Regex("[\\s\\-()]"), "")
                        onTriggerSosCall(dialTarget)
                    } else {
                        showMissingNumberWarning = true
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626),
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("btn_profile_sos_call")
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Trigger SOS Emergency Call",
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (trimmedNumber.isNotBlank()) {
                        if (isUrdu) "SOS فوری کال ($trimmedNumber)" else "SOS • CALL $trimmedNumber NOW"
                    } else {
                        if (isUrdu) "SOS ایمرجنسی کال" else "SOS • EMERGENCY CALL"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * Editable personal & safety profile card displaying the user's current Display Name,
 * Phone Number, and Saved Emergency Contact Number with validation and Firebase Firestore sync.
 */
@Composable
fun ProfileDetailsEditorCard(
    currentUser: UserProfile?,
    isFirebaseSynced: Boolean,
    appLang: AppLanguage,
    onSaveProfile: suspend (displayName: String, phoneNumber: String, emergencyContact: String) -> Result<UserProfile>,
    onOpenAuth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isUrdu = appLang == AppLanguage.URDU

    var displayName by remember(currentUser?.uid) {
        mutableStateOf(currentUser?.name ?: "")
    }
    var phoneNumber by remember(currentUser?.uid) {
        mutableStateOf(currentUser?.phoneNumber ?: "")
    }
    var emergencyContact by remember(currentUser?.uid) {
        mutableStateOf(currentUser?.emergencyContactNumber ?: "")
    }
    var isSaving by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isErrorFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(
        currentUser?.uid,
        currentUser?.name,
        currentUser?.phoneNumber,
        currentUser?.emergencyContactNumber
    ) {
        if (currentUser != null && !isSaving) {
            displayName = currentUser.name
            phoneNumber = currentUser.phoneNumber
            emergencyContact = currentUser.emergencyContactNumber
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile_edit_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Title + Firestore Sync Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isUrdu) "ذاتی معلومات اور ایمرجنسی رابطہ" else "Personal & Emergency Details",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isUrdu) "نام، فون نمبر اور ہنگامی رابطہ اپ ڈیٹ کریں" else "View & update name, phone & SOS contact",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isFirebaseSynced) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                    modifier = Modifier.testTag("profile_firestore_sync_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isFirebaseSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = if (isFirebaseSynced) "Firestore Synced" else "Local Session",
                            tint = if (isFirebaseSynced) Color(0xFF15803D) else Color(0xFFB45309),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isFirebaseSynced) "Firestore" else "Local",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFirebaseSynced) Color(0xFF15803D) else Color(0xFFB45309)
                        )
                    }
                }
            }

            if (currentUser == null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_unauthenticated_notice")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isUrdu)
                                "اپنا پروفائل اور ایمرجنسی نمبر فائر بیس پر محفوظ کرنے کے لیے سائن ان کریں۔"
                            else
                                "Sign in with Google or Phone to view and sync your profile and emergency contact to Firebase Firestore.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onOpenAuth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("btn_profile_signin_prompt"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (isUrdu) "لاگ ان / سائن ان کریں" else "Sign In to Manage Profile",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // Current Saved Summary Snapshot
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_summary_overview")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${if (isUrdu) "نام" else "Name"}: ${currentUser.name}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("profile_saved_name_text")
                        )
                        Text(
                            text = "${if (isUrdu) "فون" else "Phone"}: ${currentUser.phoneNumber.ifBlank { "Not set" }}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("profile_saved_phone_text")
                        )
                        Text(
                            text = "${if (isUrdu) "ایمرجنسی نمبر" else "Emergency Contact"}: ${currentUser.emergencyContactNumber.ifBlank { if (isUrdu) "محفوظ نہیں" else "Not set" }}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (currentUser.emergencyContactNumber.isNotBlank()) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("profile_saved_emergency_text")
                        )
                    }
                }

                // Editable Fields
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        if (it.length <= 100) {
                            displayName = it
                            feedbackMessage = null
                        }
                    },
                    label = { Text(if (isUrdu) "پورا نام (Display Name)" else "Display Name") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Display Name",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_profile_display_name")
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        if (it.length <= 30) {
                            phoneNumber = it
                            feedbackMessage = null
                        }
                    },
                    label = { Text(if (isUrdu) "موبائل نمبر (Phone Number)" else "Phone Number") },
                    placeholder = { Text("0301-8765432") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Phone Number",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_profile_phone_number")
                )

                OutlinedTextField(
                    value = emergencyContact,
                    onValueChange = {
                        if (it.length <= 30) {
                            emergencyContact = it
                            feedbackMessage = null
                        }
                    },
                    label = { Text(if (isUrdu) "محفوظ ایمرجنسی رابطہ نمبر" else "Saved Emergency Contact Number") },
                    placeholder = { Text("0342-9348582") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Emergency,
                            contentDescription = "Emergency Contact Number",
                            tint = Color(0xFFDC2626)
                        )
                    },
                    trailingIcon = {
                        if (emergencyContact.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    try {
                                        val cleanDial = emergencyContact.trim().replace(" ", "")
                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanDial")))
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.testTag("btn_dial_emergency_inline")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Dial Emergency Contact",
                                    tint = Color(0xFF15803D)
                                )
                            }
                        }
                    },
                    supportingText = {
                        Text(
                            if (isUrdu) "سفر کے دوران ہنگامی صورتحال میں فوری رابطے کے لیے"
                            else "Used for quick 1-tap SOS contact during active trips"
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_profile_emergency_contact")
                )

                // Feedback Banner
                if (feedbackMessage != null) {
                    Surface(
                        color = if (isErrorFeedback) MaterialTheme.colorScheme.errorContainer else Color(0xFFECFDF5),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isErrorFeedback) MaterialTheme.colorScheme.error else Color(0xFF10B981)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .testTag("profile_save_feedback")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isErrorFeedback) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isErrorFeedback) MaterialTheme.colorScheme.error else Color(0xFF047857),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = feedbackMessage.orEmpty(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isErrorFeedback) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF065F46)
                            )
                        }
                    }
                }

                // Save Button (48.dp touch target)
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        feedbackMessage = null
                        scope.launch {
                            val result = onSaveProfile(displayName, phoneNumber, emergencyContact)
                            isSaving = false
                            result.onSuccess {
                                isErrorFeedback = false
                                feedbackMessage = if (isUrdu) {
                                    "آپ کا نام، فون اور ایمرجنسی نمبر کامیابی سے محفوظ ہو گیا!"
                                } else if (isFirebaseSynced) {
                                    "Profile & emergency contact synced to Firebase Firestore."
                                } else {
                                    "Profile & emergency contact updated for current session."
                                }
                            }.onFailure { err ->
                                isErrorFeedback = true
                                feedbackMessage = err.localizedMessage ?: "Failed to update profile."
                            }
                        }
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_save_profile")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isUrdu) "محفوظ ہو رہا ہے..." else "Saving...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUrdu) "معلومات محفوظ کریں (Save)" else "Save Profile Changes",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (currentUser.emergencyContactNumber.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val cleanDial = currentUser.emergencyContactNumber.trim().replace(" ", "")
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanDial")))
                            } catch (_: Exception) {}
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_call_saved_emergency")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call Saved Emergency Contact",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUrdu) {
                                "ایمرجنسی نمبر پر کال کریں (${currentUser.emergencyContactNumber})"
                            } else {
                                "Call Emergency Contact (${currentUser.emergencyContactNumber})"
                            },
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
