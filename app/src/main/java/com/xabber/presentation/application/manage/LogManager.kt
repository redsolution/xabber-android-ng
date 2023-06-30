package com.xabber.presentation.application.manage

import android.util.Log
import com.xabber.BuildConfig

object LogManager {
    private const val TAG = "LogManager"
    private val isDebuggable: Boolean = BuildConfig.DEBUG

    fun d(message: String) {
        if (isDebuggable) {
            Log.d(TAG, message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (isDebuggable) {
            Log.e(TAG, message, throwable)
        }
    }
}