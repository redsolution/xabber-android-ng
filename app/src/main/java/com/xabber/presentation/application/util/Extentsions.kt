package com.xabber.presentation.application.util

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.Surface
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.xabber.R
import com.xabber.data.dto.*
import com.xabber.presentation.onboarding.fragments.signup.emoji.EmojiTypeDto


fun Fragment.isPermissionGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(
        activity as AppCompatActivity,
        permission
    ) == PackageManager.PERMISSION_GRANTED
}

fun Fragment.askUserForOpeningAppSettings() {
    val appSettingsIntent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", requireActivity().packageName, null)
    )
    if (requireActivity().packageManager.resolveActivity(
            appSettingsIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        ) != null
    ) {
        val dialog = AlertDialog.Builder(requireContext())
        dialog.setTitle(R.string.dialog_title_permission_denied)
            .setMessage(R.string.offer_to_open_settings)
            .setPositiveButton(R.string.dialog_button_open) { _, _ ->
                startActivity(appSettingsIntent)
            }
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
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

fun List<EmojiTypeDto>.toMap(): Map<String, List<List<String>>> {
    val map = this.associate {
        it.name to it.list
    }

    return map
}

