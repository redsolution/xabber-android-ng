package com.xabber.presentation.application.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.databinding.BottomSheetTurnOffNotificationsBinding
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.presentation.AppConstants.TURN_OFF_NOTIFICATIONS_KEY
import io.realm.kotlin.Realm

class NotificationBottomSheet :
    BottomSheetDialogFragment(R.layout.bottom_sheet_turn_off_notifications) {
    private val binding by viewBinding(BottomSheetTurnOffNotificationsBinding::bind)
    private var behavior: BottomSheetBehavior<*>? = null
    val realm = Realm.open(defaultRealmConfig())

    companion object {
        fun newInstance(id: String?) = NotificationBottomSheet().apply {
            arguments = Bundle().apply {
                putString(TURN_OFF_NOTIFICATIONS_KEY, id)
            }
        }
    }

    override fun getTheme(): Int = R.style.Theme_NoWiredStrapInNavigationBar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            setupBottomSheet(it)
        }
        return dialog
    }

    private fun setupBottomSheet(dialogInterface: DialogInterface) {
        val bottomSheetDialog = dialogInterface as BottomSheetDialog
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
            ?: return

        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        behavior = BottomSheetBehavior.from(bottomSheet)
        behavior?.skipCollapsed = true
        behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        initActions()
    }

    private fun initActions() {
        with(binding) {
            rl15min.setOnClickListener {
                setMute(TimeMute.MIN15.time + System.currentTimeMillis())
                dismiss()
            }
            rl1hour.setOnClickListener {
                setMute(TimeMute.HOUR1.time + System.currentTimeMillis())
                dismiss()
            }
            rl2hour.setOnClickListener {
                setMute(TimeMute.HOUR2.time + System.currentTimeMillis())
                dismiss()
            }
            rl1day.setOnClickListener {
                setMute(TimeMute.DAY1.time + System.currentTimeMillis())
                dismiss()
            }
            rlForever.setOnClickListener {
                setMute(TimeMute.FOREVER.time + System.currentTimeMillis())
                dismiss()
            }
        }
    }

    private fun setMute(time: Long) {
        val id = arguments?.getString(TURN_OFF_NOTIFICATIONS_KEY) ?: ""
        realm.writeBlocking {
            val chat = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
            chat?.muteExpired = time
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

}

enum class TimeMute(val time: Long) {
    MIN15(900000), HOUR1(3600000), HOUR2(7200000), DAY1(86400000), FOREVER(315360000000)
}
