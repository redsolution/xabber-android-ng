package com.xabber.presentation.application.fragments.chat

object Check {
    private var isSelectedMode = false

    fun setSelectedMode(mode: Boolean) {
        isSelectedMode = mode
    }

    fun getSelectedMode() : Boolean = isSelectedMode
}