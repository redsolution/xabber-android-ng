package com.xabber.presentation.onboarding.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.xabber.R
import java.io.IOException
import java.security.*
import java.security.cert.CertificateException
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.InvalidKeySpecException
import java.util.*
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException

class PasswordStorageHelper(context: Context) {
    private val tag = "PasswordStorageHelper"
    private val PREFS_NAME = "SecureData"

    private var passwordStorage: PasswordStorageInterface? = PasswordStorageHelperSDK18()

    init {
        passwordStorage = PasswordStorageHelperSDK18()
        var isInitialized: Boolean? = false

        try {
            isInitialized = passwordStorage?.init(context)
            Log.d(tag, "PasswordStorage initialization: ${if (isInitialized == true) "successful" else "failed"}")
        } catch (ex: Exception) {
            Log.e(tag, "PasswordStorage initialization error: ${ex.message}", ex)
        }
    }

    fun setData(jid: String, data: ByteArray?) {
        Log.d(tag, "Attempting to store data for JID: $jid")
        passwordStorage?.setData(jid, data ?: ByteArray(0))
    }

    fun getData(jid: String): String? {
        Log.d(tag, "Attempting to retrieve data for JID: $jid")
        val data = passwordStorage?.getData(jid)
        if (data == null) {
            Log.w(tag, "No data found for JID: $jid")
            // Debug: Check SharedPreferences directly
            val prefs = preferences ?: run {
                Log.e(tag, "SharedPreferences not initialized")
                return null
            }
            val encrypted = prefs.getString(jid, null)
            Log.d(tag, "Raw SharedPreferences entry for $jid: ${if (encrypted != null) "exists" else "null"}")
            return null
        }
        val result = String(data)
        Log.d(tag, "Successfully retrieved data for JID: $jid")
        return result
    }

    fun remove(jid: String) {
        Log.d(tag, "Removing data for JID: $jid")
        passwordStorage?.remove(jid)
    }

    private var preferences: SharedPreferences? = null

    private interface PasswordStorageInterface {
        fun init(context: Context?): Boolean
        fun setData(key: String?, data: ByteArray?)
        fun getData(key: String?): ByteArray?
        fun remove(key: String?)
    }

    private inner class PasswordStorageHelperSDK18 : PasswordStorageInterface {

        private val KEY_ALGORITHM_RSA: String = "RSA"
        private val KEYSTORE_PROVIDER_ANDROID_KEYSTORE: String = "AndroidKeyStore"
        private val RSA_ECB_PKCS1_PADDING: String = "RSA/ECB/PKCS1Padding"
        private var alias: String? = null

        override fun init(context: Context?): Boolean {
            preferences = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            alias = context?.getString(R.string.app_name)
            Log.d(tag, "Initializing KeyStore with alias: $alias")

            val ks: KeyStore?

            try {
                ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID_KEYSTORE)
                ks.load(null)

                val privateKey: Key? = ks.getKey(alias, null)
                if (privateKey != null && ks.getCertificate(alias) != null) {
                    val publicKey: PublicKey? = ks.getCertificate(alias).publicKey
                    if (publicKey != null) {
                        Log.d(tag, "Existing key pair found in KeyStore")
                        return true
                    }
                }
            } catch (ex: Exception) {
                Log.e(tag, "KeyStore load error: ${ex.message}", ex)
                return false
            }

            val end = GregorianCalendar()
            end.add(Calendar.YEAR, 10)

            val spec: AlgorithmParameterSpec = KeyGenParameterSpec.Builder(
                alias ?: "",
                KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT
            )
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .build()

            val kpGenerator: KeyPairGenerator
            try {
                kpGenerator = KeyPairGenerator.getInstance(
                    KEY_ALGORITHM_RSA,
                    KEYSTORE_PROVIDER_ANDROID_KEYSTORE
                )
                kpGenerator.initialize(spec)
                kpGenerator.generateKeyPair()
                Log.d(tag, "Generated new key pair")
            } catch (e: Exception) {
                Log.e(tag, "Key pair generation error: ${e.message}", e)
                when (e) {
                    is NoSuchAlgorithmException, is InvalidAlgorithmParameterException, is NoSuchProviderException -> {
                        try {
                            ks?.deleteEntry(alias)
                            Log.d(tag, "Deleted invalid KeyStore entry")
                        } catch (e1: Exception) {
                            Log.e(tag, "Error deleting KeyStore entry: ${e1.message}", e1)
                        }
                    }
                }
                return false
            }

            try {
                val privateKey: Key = ks.getKey(alias, null)
                val keyFactory: KeyFactory = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
                val keyInfo: KeyInfo = keyFactory.getKeySpec(privateKey, KeyInfo::class.java)
                val isHardwareBacked = keyInfo.isInsideSecureHardware
                Log.d(tag, "Hardware-Backed Keystore Supported: $isHardwareBacked")
            } catch (e: Exception) {
                Log.w(tag, "Error checking hardware-backed keystore: ${e.message}", e)
            }

            return true
        }

        override fun setData(key: String?, data: ByteArray?) {
            var ks: KeyStore? = null
            try {
                ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID_KEYSTORE)
                ks.load(null)
                if (ks.getCertificate(alias) == null) {
                    Log.e(tag, "No certificate found for alias: $alias")
                    return
                }

                val publicKey: PublicKey? = ks.getCertificate(alias).publicKey
                if (publicKey == null) {
                    Log.e(tag, "Public key not found in KeyStore")
                    return
                }

                val value: String = encrypt(publicKey, data)
                Log.d(tag, "Encrypted data for key: $key")

                val editor: SharedPreferences.Editor? = preferences?.edit()
                editor?.putString(key, value)
                editor?.apply()
                Log.d(tag, "Stored encrypted data in SharedPreferences for key: $key")
            } catch (e: Exception) {
                Log.e(tag, "Error storing data for key: $key, ${e.message}", e)
                when (e) {
                    is NoSuchAlgorithmException, is InvalidKeyException, is NoSuchPaddingException,
                    is IllegalBlockSizeException, is BadPaddingException, is NoSuchProviderException,
                    is InvalidKeySpecException, is KeyStoreException, is CertificateException, is IOException -> {
                        try {
                            ks?.deleteEntry(alias)
                            Log.d(tag, "Deleted KeyStore entry due to error")
                        } catch (e1: Exception) {
                            Log.e(tag, "Error deleting KeyStore entry: ${e1.message}", e1)
                        }
                    }
                }
            }
        }

        override fun getData(key: String?): ByteArray? {
            var ks: KeyStore? = null
            try {
                ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID_KEYSTORE)
                ks.load(null)
                val privateKey: Key = ks.getKey(alias, null) ?: run {
                    Log.e(tag, "Private key not found for alias: $alias")
                    return null
                }
                val encryptedData = preferences?.getString(key, null)
                if (encryptedData == null) {
                    Log.w(tag, "No encrypted data found in SharedPreferences for key: $key")
                    return null
                }
                val decrypted = decrypt(privateKey, encryptedData)
                Log.d(tag, "Decrypted data for key: $key")
                return decrypted
            } catch (e: Exception) {
                Log.e(tag, "Error retrieving data for key: $key, ${e.message}", e)
                try {
                    ks?.deleteEntry(alias)
                    Log.d(tag, "Deleted KeyStore entry due to error")
                } catch (e1: Exception) {
                    Log.e(tag, "Error deleting KeyStore entry: ${e1.message}", e1)
                }
                return null
            }
        }

        override fun remove(key: String?) {
            val editor: SharedPreferences.Editor? = preferences?.edit()
            editor?.remove(key)
            editor?.apply()
            Log.d(tag, "Removed SharedPreferences entry for key: $key")
        }

        private fun encrypt(encryptionKey: PublicKey, data: ByteArray?): String {
            if (data == null || data.isEmpty()) {
                Log.w(tag, "No data provided for encryption")
                return ""
            }
            val cipher: Cipher = Cipher.getInstance(RSA_ECB_PKCS1_PADDING)
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
            val encrypted: ByteArray = cipher.doFinal(data)
            return Base64.encodeToString(encrypted, Base64.DEFAULT)
        }

        private fun decrypt(decryptionKey: Key, encryptedData: String?): ByteArray? {
            if (encryptedData.isNullOrEmpty()) {
                Log.w(tag, "No encrypted data provided for decryption")
                return null
            }
            val encryptedBuffer: ByteArray = Base64.decode(encryptedData, Base64.DEFAULT)
            val cipher: Cipher = Cipher.getInstance(RSA_ECB_PKCS1_PADDING)
            cipher.init(Cipher.DECRYPT_MODE, decryptionKey)
            return cipher.doFinal(encryptedBuffer)
        }
    }
}