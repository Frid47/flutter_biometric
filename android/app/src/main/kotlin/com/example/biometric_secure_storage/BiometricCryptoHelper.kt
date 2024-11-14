import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import kotlin.coroutines.suspendCoroutine

class BiometricCryptoHelper(private val activity: FragmentActivity) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        private const val KEY_SIZE = 256
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ENCRYPTION_BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val ENCRYPTION_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    }

    private fun createSecretKey(keyName: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            ENCRYPTION_ALGORITHM,
            ANDROID_KEYSTORE
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyName,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(ENCRYPTION_BLOCK_MODE)
            .setEncryptionPaddings(ENCRYPTION_PADDING)
            .setKeySize(KEY_SIZE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private fun getOrCreateKey(keyName: String): SecretKey {
        // Try to get existing key
        val existingKey = keyStore.getEntry(keyName, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createSecretKey(keyName)
    }

    suspend fun encrypt(
        keyName: String,
        data: String,
        promptInfo: BiometricPrompt.PromptInfo
    ): Map<String, String>? = suspendCoroutine { continuation ->
        try {
            val cipher = getCipher()
            val secretKey = getOrCreateKey(keyName)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val cryptoObject = BiometricPrompt.CryptoObject(cipher)

            val biometricPrompt = BiometricPrompt(
                activity,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        try {
                            val authenticatedCipher = result.cryptoObject?.cipher
                                ?: throw Exception("Cipher is null")

                            val encryptedBytes = authenticatedCipher.doFinal(
                                data.toByteArray(Charsets.UTF_8)
                            )

                            val encryptionResult = mapOf(
                                "encrypted" to Base64.encodeToString(encryptedBytes, Base64.DEFAULT),
                                "iv" to Base64.encodeToString(authenticatedCipher.iv, Base64.DEFAULT)
                            )

                            continuation.resumeWith(Result.success(encryptionResult))
                        } catch (e: Exception) {
                            e.printStackTrace()
                            continuation.resumeWith(Result.success(null))
                        }
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {
                        continuation.resumeWith(Result.success(null))
                    }

                    override fun onAuthenticationFailed() {
                        continuation.resumeWith(Result.success(null))
                    }
                }
            )

            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } catch (e: Exception) {
            e.printStackTrace()
            continuation.resumeWith(Result.success(null))
        }
    }

    suspend fun decrypt(
        keyName: String,
        encryptedData: String,
        iv: String,
        promptInfo: BiometricPrompt.PromptInfo
    ): String? = suspendCoroutine { continuation ->
        try {
            val cipher = getCipher()
            val secretKey = getOrCreateKey(keyName)

            val ivSpec = GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT))
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val cryptoObject = BiometricPrompt.CryptoObject(cipher)

            val biometricPrompt = BiometricPrompt(
                activity,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        try {
                            val authenticatedCipher = result.cryptoObject?.cipher
                                ?: throw Exception("Cipher is null")

                            val decryptedBytes = authenticatedCipher.doFinal(
                                Base64.decode(encryptedData, Base64.DEFAULT)
                            )

                            val decryptedString = String(decryptedBytes, Charsets.UTF_8)
                            continuation.resumeWith(Result.success(decryptedString))
                        } catch (e: Exception) {
                            e.printStackTrace()
                            continuation.resumeWith(Result.success(null))
                        }
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {
                        continuation.resumeWith(Result.success(null))
                    }

                    override fun onAuthenticationFailed() {
                        continuation.resumeWith(Result.success(null))
                    }
                }
            )

            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } catch (e: Exception) {
            e.printStackTrace()
            continuation.resumeWith(Result.success(null))
        }
    }

    fun deleteKey(keyName: String) {
        try {
            keyStore.deleteEntry(keyName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getCipher(): Cipher {
        return Cipher.getInstance(
            "$ENCRYPTION_ALGORITHM/$ENCRYPTION_BLOCK_MODE/$ENCRYPTION_PADDING"
        )
    }
}