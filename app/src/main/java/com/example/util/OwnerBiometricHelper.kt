package com.example.util

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

enum class BiometricStatus(val message: String, val urduMessage: String) {
    AVAILABLE("Fingerprint sensor ready", "فنگر پرنٹ سینسر فعال ہے"),
    NOT_ENROLLED("Fingerprint supported, but no prints enrolled in device settings", "ڈیوائس میں کوئی فنگر پرنٹ رجسٹرڈ نہیں"),
    NO_HARDWARE("Device has no fingerprint sensor", "ڈیوائس میں فنگر پرنٹ سینسر نہیں ہے"),
    HW_UNAVAILABLE("Fingerprint hardware is busy or unavailable", "فنگر پرنٹ سینسر فی الوقت دستیاب نہیں")
}

object OwnerBiometricHelper {

    private const val KEY_NAME = "MAHI_OWNER_KEYSTORE_SECRET"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun checkBiometricStatus(context: Context): BiometricStatus {
        return try {
            val biometricManager = BiometricManager.from(context)
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            when (biometricManager.canAuthenticate(authenticators)) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HW_UNAVAILABLE
                else -> BiometricStatus.NO_HARDWARE
            }
        } catch (_: Throwable) {
            BiometricStatus.NO_HARDWARE
        }
    }

    private fun getOrCreateKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_NAME)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val builder = KeyGenParameterSpec.Builder(
                    KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(false) // Allows prompt crypto binding without immediate device lock requirement
                keyGenerator.init(builder.build())
                keyGenerator.generateKey()
            } else {
                keyStore.getKey(KEY_NAME, null) as? SecretKey
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun createCryptoObject(): BiometricPrompt.CryptoObject? {
        return try {
            val key = getOrCreateKey() ?: return null
            val cipher = Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            BiometricPrompt.CryptoObject(cipher)
        } catch (_: Throwable) {
            null
        }
    }

    fun promptOwnerFingerprint(
        activity: FragmentActivity,
        title: String = "JARANWALA RIDE — Owner Verification",
        subtitle: String = "Touch fingerprint sensor to verify MAHI Owner Identity",
        negativeText: String = "Use Owner Master Key",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errorCode, errString)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        try {
            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setDescription("Required second factor for verified Owner Administrator access")
                .setNegativeButtonText(negativeText)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()

            val cryptoObject = createCryptoObject()
            if (cryptoObject != null) {
                biometricPrompt.authenticate(promptInfo, cryptoObject)
            } else {
                biometricPrompt.authenticate(promptInfo)
            }
        } catch (e: Throwable) {
            onError(-1, e.message ?: "Biometric prompt initialization failed")
        }
    }
}
