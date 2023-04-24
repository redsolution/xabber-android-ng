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
        passwordStorage =
            PasswordStorageHelperSDK18()
        var isInitialized: Boolean? = false

        try {
            isInitialized = passwordStorage?.init(context)
        } catch (ex: Exception) {
            Log.e(tag, "PasswordStorage initialisation error:" + ex.message, ex)
        }
    }

    fun setData(jid: String, data: ByteArray?) {
        passwordStorage?.setData(jid, data ?: ByteArray(0))
    }

    fun getData(jid: String): String? {
        return if (passwordStorage?.getData(jid) == null) null
        else String(passwordStorage?.getData(jid)!!)
    }

    fun remove(jid: String) {
        passwordStorage?.remove(jid)
    }

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

        private var preferences: SharedPreferences? = null
        private var alias: String? = null

        override fun init(context: Context?): Boolean {
            preferences = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            alias = context?.getString(R.string.app_name)

            val ks: KeyStore?

            try {
                ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID_KEYSTORE)

                ks?.load(null)

                val privateKey: Key? = ks?.getKey(alias, null)
                if (privateKey != null && ks.getCertificate(alias) != null) {
                    val publicKey: PublicKey? = ks.getCertificate(alias).publicKey
                    if (publicKey != null) {
                        return true
                    }
                }
            } catch (ex: Exception) {
                return false
            }

            val end = GregorianCalendar()
            end.add(Calendar.YEAR, 10)

            val spec: AlgorithmParameterSpec?
            spec = KeyGenParameterSpec.Builder(alias ?: "", KeyProperties.PURPOSE_DECRYPT)
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
            } catch (e: Exception) {
                when (e) {
                    is NoSuchAlgorithmException, is InvalidAlgorithmParameterException, is NoSuchProviderException -> {
                        try {
                            ks?.deleteEntry(alias)
                        } catch (_: Exception) {
                        }
                    }
                }

            }

            try {
                val isHardwareBackedKeystoreSupported: Boolean
                val privateKey: Key = ks.getKey(alias, null)
                val keyFactory: KeyFactory =
                    KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
                val keyInfo: KeyInfo = keyFactory.getKeySpec(privateKey, KeyInfo::class.java)
                isHardwareBackedKeystoreSupported = keyInfo.isInsideSecureHardware

                Log.d(
                    tag,
                    "Hardware-Backed Keystore Supported: $isHardwareBackedKeystoreSupported"
                )
            } catch (_: Exception) {
            }

            return true
        }

        override fun setData(key: String?, data: ByteArray?) {
            var ks: KeyStore? = null
            try {
                ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID_KEYSTORE)

                ks.load(null)
                if (ks.getCertificate(alias) == null) return

                val publicKey: PublicKey? = ks.getCertificate(alias).publicKey

                if (publicKey == null) {
                    Log.d(tag, "Error: Public key was not found in Keystore")
                    return
                }

                val value: String = encrypt(publicKey, data)

                val editor: SharedPreferences.Editor? = preferences?.edit()
                editor?.putString(key, value)
                editor?.apply()
            } catch (e: Exception) {
                when (e) {
                    is NoSuchAlgorithmException, is InvalidKeyException, is NoSuchPaddingException,
                    is IllegalBlockSizeException, is BadPaddingException, is NoSuchProviderException,
                    is InvalidKeySpecException, is KeyStoreException, is CertificateException, is IOException -> {

                        try {
                            ks?.deleteEntry(alias)
                        } catch (e1: Exception) {
                            // Just ignore any errors here
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
                val privateKey: Key = ks.getKey(alias, null)
                return decrypt(privateKey, preferences?.getString(key, null))
            } catch (e: Exception) {
                //KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException
                // | UnrecoverableEntryException | InvalidKeyException | NoSuchPaddingException
                // | IllegalBlockSizeException | BadPaddingException | NoSuchProviderException
                try {
                    ks?.deleteEntry(alias)
                } catch (e1: Exception) {
                    // Just ignore any errors here
                }
            }
            return null
        }


        override fun remove(key: String?) {
            val editor: SharedPreferences.Editor? = preferences?.edit()
            editor?.remove(key)
            editor?.apply()
        }

        private fun encrypt(encryptionKey: PublicKey, data: ByteArray?): String {
            val cipher: Cipher = Cipher.getInstance(RSA_ECB_PKCS1_PADDING)
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
            val encrypted: ByteArray = cipher.doFinal(data)
            return Base64.encodeToString(encrypted, Base64.DEFAULT)
        }

        private fun decrypt(decryptionKey: Key, encryptedData: String?): ByteArray? {
            if (encryptedData == null) return null
            val encryptedBuffer: ByteArray = Base64.decode(encryptedData, Base64.DEFAULT)
            val cipher: Cipher = Cipher.getInstance(RSA_ECB_PKCS1_PADDING)
            cipher.init(Cipher.DECRYPT_MODE, decryptionKey)
            return cipher.doFinal(encryptedBuffer)
        }

    }

}
