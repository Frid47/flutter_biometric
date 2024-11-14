package com.example.biometric_secure_storage

import BiometricCryptoHelper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : FlutterFragmentActivity() {
    private val CHANNEL = "com.your.app/biometric_crypto"
    private lateinit var biometricCryptoHelper: BiometricCryptoHelper
    private val mainScope = CoroutineScope(Dispatchers.Main)

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        biometricCryptoHelper = BiometricCryptoHelper(this)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "encryptWithBiometric" -> {
                    val data = call.argument<String>("data")
                    if (data == null) {
                        result.error("INVALID_ARGUMENT", "Data cannot be null", null)
                        return@setMethodCallHandler
                    }

                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(call.argument<String>("promptTitle") ?: "Authentication Required")
                        .setSubtitle(call.argument<String>("promptSubtitle") ?: "Please authenticate to continue")
                        .setNegativeButtonText("Cancel")
                        .build()

                    mainScope.launch {
                        val encryptionResult = biometricCryptoHelper.encrypt(
                            "biometric_key",
                            data,
                            promptInfo
                        )
                        result.success(encryptionResult)
                    }
                }

                "decryptWithBiometric" -> {
                    val encryptedData = call.argument<String>("encryptedData")
                    val iv = call.argument<String>("iv")
                    if (encryptedData == null || iv == null) {
                        result.error("INVALID_ARGUMENT", "Encrypted data and IV cannot be null", null)
                        return@setMethodCallHandler
                    }

                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(call.argument<String>("promptTitle") ?: "Authentication Required")
                        .setSubtitle(call.argument<String>("promptSubtitle") ?: "Please authenticate to continue")
                        .setNegativeButtonText("Cancel")
                        .build()

                    mainScope.launch {
                        val decryptedData = biometricCryptoHelper.decrypt(
                            "biometric_key",
                            encryptedData,
                            iv,
                            promptInfo
                        )
                        result.success(decryptedData)
                    }
                }

                "removeFromKeystore" -> {
                    biometricCryptoHelper.deleteKey("biometric_key")
                    result.success(null)
                }

                else -> result.notImplemented()
            }
        }
    }
}
