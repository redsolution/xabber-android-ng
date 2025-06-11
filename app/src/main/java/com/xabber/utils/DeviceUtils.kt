package com.xabber.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass.Device
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

object DeviceUtils {
    private const val TAG = "DeviceUtils"

    fun getDeviceName(context: Context?): String {
        if (context == null) {
            Log.e(TAG, "Context is null, cannot retrieve device name")
            return getFallbackDeviceName()
        }

        try {
            // API Level 25+: Use Settings.Global.DEVICE_NAME
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val deviceName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                if (!deviceName.isNullOrEmpty()) {
                    Log.d(TAG, "Retrieved device name from Settings.Global: $deviceName")
                    return deviceName
                } else {
                    Log.w(TAG, "Settings.Global.DEVICE_NAME is null or empty")
                }
            } else {
                Log.d(TAG, "API level < 25, skipping Settings.Global.DEVICE_NAME")
            }
            // Fallback: Try BluetoothAdapter
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    if (bluetoothAdapter != null) {
                        if (bluetoothAdapter.isEnabled) {
                            val btName = bluetoothAdapter.name
                            if (!btName.isNullOrEmpty()) {
                                Log.d(TAG, "Retrieved device name from BluetoothAdapter: $btName")
                                return btName
                            } else {
                                Log.w(TAG, "BluetoothAdapter name is null or empty")
                            }
                        } else {
                            Log.w(TAG, "Bluetooth is disabled, cannot retrieve name")
                        }
                    } else {
                        Log.w(TAG, "No BluetoothAdapter available")
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "Bluetooth permission denied: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "BluetoothAdapter error: ${e.message}", e)
                }
            } else {
                Log.w(TAG, "BLUETOOTH permission not granted")
            }

            // Fallback: Use Build properties
            return getFallbackDeviceName(context)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error retrieving device name: ${e.message}", e)
            return getFallbackDeviceName(context)
        }
    }

    private fun getFallbackDeviceName(context: Context? = null): String {
        // Try Build properties
        try {
            val model = Build.MODEL.takeIf { it.isNotBlank() } ?: "Unknown Model"
            val device = Build.DEVICE.takeIf { it.isNotBlank() && it != model } ?: ""
            val product = Build.PRODUCT.takeIf { it.isNotBlank() && it != model && it != device } ?: ""
            val fallbackName = listOf(model, device, product).filter { it.isNotBlank() }.joinToString(" ")
            Log.d(TAG, "Using fallback device name from Build: $fallbackName")
            return fallbackName
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving Build properties: ${e.message}", e)
        }

        // Last resort
        Log.w(TAG, "All methods failed, returning default device name")
        return "XabberAndroid"
    }
}