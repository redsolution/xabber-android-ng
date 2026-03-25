package com.xabber.presentation.application.activity

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupTraceFormatterTest {

    @Test
    fun `format produces stable startup trace message`() {
        assertEquals(
            "startup[ApplicationActivity] step=initialize delta=12ms total=34ms",
            StartupTraceFormatter.format(
                traceName = "ApplicationActivity",
                step = "initialize",
                deltaMs = 12,
                totalMs = 34
            )
        )
    }
}
