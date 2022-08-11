package com.xabber.presentation

import android.app.Application

class XabberApplication : Application() {

    companion object {
        fun newInstance() = XabberApplication()
    }
}
