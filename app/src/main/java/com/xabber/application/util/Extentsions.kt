package com.xabber.application.util

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.xabber.R
import com.xabber.application.fragments.chat.ResourceStatus
import com.xabber.application.fragments.chat.RosterItemEntity
import com.xabber.data.dto.*

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

fun RosterItemEntity.getStatusIcon(): Int? =
    when (this) {
        RosterItemEntity.CONTACT -> null
        RosterItemEntity.PRIVATE_CHAT -> R.drawable.ic_badge_group_private_24
        RosterItemEntity.GROUP_CHAT -> R.drawable.ic_badge_group_public_24
        RosterItemEntity.BOT -> R.drawable.ic_badge_bot_24
        RosterItemEntity.SERVER -> R.drawable.ic_badge_server_24
        RosterItemEntity.INCOGNITO_CHAT -> R.drawable.ic_badge_group_incognito_24
        RosterItemEntity.ISSUE -> R.drawable.ic_badge_task_24
    }

fun ContactDto.getStatusColor(): Int? =
    when (this.status) {
        ResourceStatus.ONLINE -> R.color.green_700
        ResourceStatus.OFFLINE ->
            if (this.entity in listOf(
                    RosterItemEntity.GROUP_CHAT,
                    RosterItemEntity.INCOGNITO_CHAT,
                    RosterItemEntity.SERVER,
                    RosterItemEntity.PRIVATE_CHAT,
                    RosterItemEntity.ISSUE
                )
            )
                R.color.grey_500
            else
                R.color.transparent
        ResourceStatus.AWAY -> R.color.amber_700
        ResourceStatus.CHAT -> R.color.light_green_500
        ResourceStatus.DND -> R.color.red_700
        ResourceStatus.XA -> R.color.blue_700
        null -> null
    }

fun ChatDto.getStatusColor(): Int =
    when (this.status) {
        ResourceStatus.ONLINE -> R.color.green_700
        ResourceStatus.OFFLINE ->
            if (this.entity in listOf(
                    RosterItemEntity.GROUP_CHAT,
                    RosterItemEntity.INCOGNITO_CHAT,
                    RosterItemEntity.SERVER,
                    RosterItemEntity.PRIVATE_CHAT,
                    RosterItemEntity.ISSUE
                )
            )
                R.color.grey_500
            else
                R.color.transparent
        ResourceStatus.AWAY -> R.color.amber_700
        ResourceStatus.CHAT -> R.color.light_green_500
        ResourceStatus.DND -> R.color.red_700
        ResourceStatus.XA -> R.color.blue_700
    }