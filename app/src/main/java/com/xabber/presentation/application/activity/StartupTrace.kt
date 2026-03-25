package com.xabber.presentation.application.activity

import android.os.SystemClock
import android.util.Log

class StartupTrace(
    private val tag: String,
    private val traceName: String,
    private val startMs: Long = SystemClock.elapsedRealtime()
) {
    private var lastStepMs: Long = startMs

    fun step(name: String) {
        val now = SystemClock.elapsedRealtime()
        Log.d(tag, StartupTraceFormatter.format(traceName, name, now - lastStepMs, now - startMs))
        lastStepMs = now
    }
}

object StartupTraceFormatter {
    fun format(traceName: String, step: String, deltaMs: Long, totalMs: Long): String {
        return "startup[$traceName] step=$step delta=${deltaMs}ms total=${totalMs}ms"
    }
}
