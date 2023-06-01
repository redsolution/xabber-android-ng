package com.xabber.presentation.application.fragments.chat

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import com.google.android.material.shape.CornerTreatment
import com.google.android.material.shape.ShapeAppearanceModel

class RoundedCornerOutlineProvider(private val radius: Float) : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radius)
    }
}
