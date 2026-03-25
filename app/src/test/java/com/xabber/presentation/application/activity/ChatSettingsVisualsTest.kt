package com.xabber.presentation.application.activity

import com.xabber.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSettingsVisualsTest {

    @Test
    fun `gradientDrawable falls back to blue for unknown values`() {
        assertEquals(R.drawable.gradient_blue, ChatSettingsVisuals.gradientDrawable(0))
        assertEquals(R.drawable.gradient_blue, ChatSettingsVisuals.gradientDrawable(7))
        assertEquals(R.drawable.gradient_blue, ChatSettingsVisuals.gradientDrawable(99))
    }

    @Test
    fun `patternDrawable maps configured chat patterns`() {
        assertEquals(R.drawable.aliens_repeat, ChatSettingsVisuals.patternDrawable(1))
        assertEquals(R.drawable.flowers_repeat, ChatSettingsVisuals.patternDrawable(4))
        assertEquals(R.drawable.summer_repeat, ChatSettingsVisuals.patternDrawable(6))
    }
}
