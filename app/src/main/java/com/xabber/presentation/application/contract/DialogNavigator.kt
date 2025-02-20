package com.xabber.presentation.application.contract

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager



fun Fragment.dialogNavigator(): DialogNavigator {
    val parent = requireParentFragment()
    Log.d("DialogNavigator", "Parent fragment: ${parent::class.java.simpleName}")
    return if (parent is DialogNavigator) {
        parent
    } else {
        throw IllegalStateException("Parent fragment must implement DialogNavigator")
    }
}

interface DialogNavigator {
    fun goBack()
    fun showAccountSettingsPage()
    fun showProfileSettings()
    fun showAccount(jid: String)
}