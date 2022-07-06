package com.xabber.presentation.application.activity

object MaskChanger {
    private var mask : Mask? = null

    fun getMask(): Mask = mask!!

    fun setMask(newMask: Mask) {
        mask = newMask
    }
}