package com.xabber.utils

import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import android.util.TypedValue
import android.view.Display
import android.view.Surface
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.presentation.onboarding.fragments.signup.emoji.EmojiTypeDto


fun Fragment.showToast(message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

fun Fragment.showToast(message: Int) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

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
            .setNegativeButton(R.string.dialog_button_cancel) { dialogInterface: DialogInterface, _ ->
                dialogInterface.dismiss()
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


fun List<EmojiTypeDto>.toMap(): Map<String, List<List<String>>> {
    val map = this.associate {
        it.name to it.list
    }
    return map
}

fun AppCompatActivity.lockScreenRotation(isLock: Boolean) {
    this.requestedOrientation =
        if (isLock) {
            val display: Display = this.windowManager.defaultDisplay
            val rotation = display.rotation
            val size = Point()
            display.getSize(size)
            if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
                if (size.x > size.y) {
                    if (rotation == Surface.ROTATION_0) {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    }
                } else {
                    if (rotation == Surface.ROTATION_0) {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    }
                }
            } else {
                if (size.x > size.y) {
                    if (rotation == Surface.ROTATION_90) {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    }
                } else {
                    if (rotation == Surface.ROTATION_90) {
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
            }
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
}

val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()

val Int.px: Float
    get() = ((this - 0.5f) / Resources.getSystem().displayMetrics.density)

fun AppCompatActivity.hideSoftKeyboard(view: View) {
    val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

fun Drawable.getBitmap(): Bitmap {
    val bitmap: Bitmap = Bitmap.createBitmap(
        intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

fun dipToPx(dip: Float, context: Context): Int {
    return dipToPxFloat(dip, context).toInt()
}

fun dipToPxFloat(dip: Float, context: Context): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dip,
        context.resources.displayMetrics
    )
}

fun spToPxFloat(sp: Float, context: Context): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        sp,
        context.resources.displayMetrics
    )
}


fun RecyclerView.partSmoothScrollToPosition(targetItem: Int) {
    layoutManager?.apply {
        val maxScroll = 15
        when (this) {
            is LinearLayoutManager -> {
                val topItem = findFirstVisibleItemPosition()
                val distance = topItem - targetItem
                val anchorItem = when {
                    distance > maxScroll -> targetItem + maxScroll
                    distance < maxScroll -> targetItem - maxScroll
                    else -> topItem
                }
                if (anchorItem != topItem) scrollToPosition(anchorItem)
                post {
                    smoothScrollToPosition(targetItem)
                }
            }
            else -> smoothScrollToPosition(targetItem)
        }
    }
}

inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? = when {
    SDK_INT >= 33 -> getParcelable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelable(key) as? T
}
