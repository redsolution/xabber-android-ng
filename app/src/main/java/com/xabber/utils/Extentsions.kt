package com.xabber.utils

import android.app.Activity
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
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
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.AccountDto
import com.xabber.dto.AvatarDto
import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageReferenceDto
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

fun AppCompatActivity.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun AppCompatActivity.showToast(message: Int) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Activity.lockScreenRotation(isLock: Boolean) {
    requestedOrientation =
        if (isLock) {
            val display: Display? = if (SDK_INT >= Build.VERSION_CODES.R) {
                this.display
            } else {
                windowManager.defaultDisplay
            }
            var rotation = 0
            when (display?.rotation) {
                Surface.ROTATION_0 -> rotation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                Surface.ROTATION_90 -> rotation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                Surface.ROTATION_180 -> rotation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                Surface.ROTATION_270 -> rotation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            }
            rotation
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

fun spToPxFloat(sp: Float, context: Context): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        sp,
        context.resources.displayMetrics
    )
}

fun RecyclerView.partSmoothScrollToPosition(targetItem: Int) {
    layoutManager?.apply {
        val maxScroll = 6
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

// Mapping
fun LastChatsStorageItem.toChatListDto(): ChatListDto =
    ChatListDto(
        id = primary,
        owner = owner,
        opponentJid = jid,
        opponentNickname = "",
        customNickname = if (rosterItem != null) rosterItem!!.customNickname else "",
        lastMessageBody = if (lastMessage == null) "" else if (lastMessage!!.body.isNotEmpty()) lastMessage!!.body else if (lastMessage!!.references.isNotEmpty()) { if (lastMessage!!.references[0].isAudioMessage) "Voice message" else if (lastMessage!!.references[0].isGeo) "Location" else "${lastMessage!!.references[0].fileName}" }else "",
        lastMessageDate = if (lastMessage == null || draftMessage != null) messageDate else lastMessage!!.date,
        lastMessageState = if (lastMessage?.state_ == 5 || lastMessage == null) MessageSendingState.None else MessageSendingState.Read,
        isArchived = isArchived,
        isSynced = isSynced,
        draftMessage = draftMessage,
        hasAttachment = false,
        isSystemMessage = false,
        isMentioned = false,
        muteExpired = muteExpired,
        pinnedDate = pinnedPosition,
        status = ResourceStatus.Online,
        entity = RosterItemEntity.Contact,
        unread = if (unread <= 0) "" else unread.toString(),
        lastPosition = lastPosition,
        drawableId = avatar,
        isHide = false,
        lastMessageIsOutgoing = if (lastMessage != null) lastMessage!!.outgoing else false,
        isGroup = conversationType_ == ConversationType.Group.rawValue
    )

fun com.xabber.data_base.models.account.AccountStorageItem.toAccountDto() =
    AccountDto(
        id = primary,
        jid = jid,
        order = order,
        nickname = username,
        enabled = enabled,
        statusMessage = statusMessage,
        colorKey = colorKey,
        hasAvatar = hasAvatar
    )

fun com.xabber.data_base.models.avatar.AvatarStorageItem.toAvatarDto() =
    AvatarDto(
        id = primary,
        owner = owner,
        jid = jid,
        uploadUrl = uploadUrl,
        fileUri = fileUri,
        image96 = image96,
        image128 = image128,
        image192 = image192,
        image384 = image384,
        image512 = image512
    )

fun MessageReferenceStorageItem.toMessageReferenceDto() =
    MessageReferenceDto(
        id = primary,
        uri = uri,
        mimeType = mimeType,
        size = fileSize,
        fileName = fileName,
        isGeo = isGeo,
        latitude = latitude,
        longitude = longitude,
        isVoiceMessage = isAudioMessage
    )
