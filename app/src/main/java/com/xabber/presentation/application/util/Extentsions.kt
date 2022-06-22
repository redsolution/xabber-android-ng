package com.xabber.presentation.application.util

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.xabber.R
import com.xabber.data.dto.*


fun Fragment.isPermissionGranted(permission: String) : Boolean {
    return ContextCompat.checkSelfPermission(activity as AppCompatActivity, permission) == PackageManager.PERMISSION_GRANTED
}

fun Fragment.setFragmentResultListener(
    requestKey: String,
    listener: ((resultKey: String, bundle: Bundle) -> Unit)
) {
    parentFragmentManager.setFragmentResultListener(requestKey, this, listener)
}

fun Fragment.setFragmentResult(
    requestKey: String,
    result: Bundle
) = parentFragmentManager.setFragmentResult(requestKey, result)

fun AppCompatActivity.requestCameraPermissionIfNeeded(requestCode: Int) =
    this.checkAndRequestPermission(
        Manifest.permission.CAMERA,
        requestCode
    )

fun AppCompatActivity.requestFileReadPermissionIfNeeded(requestCode: Int) =
    this.checkAndRequestPermission(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        requestCode
    )

fun AppCompatActivity.checkAndRequestPermission(permission: String, requestCode: Int): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        return true
    }

    if (this.checkPermission(permission)) {
        return true
    } else {
        this.requestPermissions(arrayOf(permission), requestCode)
    }
    return false
}

fun AppCompatActivity.checkPermission(permission: String): Boolean {
    val permissionCheck = ContextCompat.checkSelfPermission(application, permission)
    return permissionCheck == PackageManager.PERMISSION_GRANTED
}


fun getRandomColor(): Int {
    return AccountColor.values().toList().shuffled().first().colorId
}

