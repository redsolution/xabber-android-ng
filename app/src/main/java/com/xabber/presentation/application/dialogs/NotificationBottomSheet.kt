package com.xabber.presentation.application.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.BottomSheetTurnOffNotificationsBinding
import com.xabber.presentation.AppConstants.TURN_OFF_NOTIFICATIONS_BUNDLE_KEY
import com.xabber.presentation.AppConstants.TURN_OFF_NOTIFICATIONS_KEY
import com.xabber.utils.setFragmentResult

class NotificationBottomSheet :
    BottomSheetDialogFragment(R.layout.bottom_sheet_turn_off_notifications) {
    private val binding by viewBinding(BottomSheetTurnOffNotificationsBinding::bind)
    private var behavior: BottomSheetBehavior<*>? = null

    companion object {
        fun newInstance() = NotificationBottomSheet()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener {
            setupBottomSheet(it, savedInstanceState)
        }
        return dialog
    }

    private fun setupBottomSheet(dialogInterface: DialogInterface, savedInstanceState: Bundle?) {
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

//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//        if (savedInstanceState != null) behavior?.state = BottomSheetBehavior.STATE_EXPANDED
//
//    }

    private fun initActions() {
        with(binding) {
            rl15min.setOnClickListener {
                setFragmentResult(
                    TURN_OFF_NOTIFICATIONS_KEY,
                    bundleOf(TURN_OFF_NOTIFICATIONS_BUNDLE_KEY to TimeMute.MIN15.time)
                )
                dismiss()
            }
            rl1hour.setOnClickListener {
                setFragmentResult(
                    TURN_OFF_NOTIFICATIONS_KEY,
                    bundleOf(TURN_OFF_NOTIFICATIONS_BUNDLE_KEY to TimeMute.HOUR1.time)
                )
                dismiss()
            }
            rl2hour.setOnClickListener {
                setFragmentResult(
                    TURN_OFF_NOTIFICATIONS_KEY,
                    bundleOf(TURN_OFF_NOTIFICATIONS_BUNDLE_KEY to TimeMute.HOUR2.time)
                )
                dismiss()
            }
            rl1day.setOnClickListener {

                setFragmentResult(
                    TURN_OFF_NOTIFICATIONS_KEY,
                    bundleOf(TURN_OFF_NOTIFICATIONS_BUNDLE_KEY to TimeMute.DAY1.time)
                )
                dismiss()
            }
            rlForever.setOnClickListener {
                setFragmentResult(
                    TURN_OFF_NOTIFICATIONS_KEY,
                    bundleOf(TURN_OFF_NOTIFICATIONS_BUNDLE_KEY to TimeMute.FOREVER.time)
                )
                dismiss()
            }
        }
    }
}

enum class TimeMute(val time: Long) {
    MIN15(900000), HOUR1(3600000), HOUR2(7200000), DAY1(86400000), FOREVER(315360000000)
}
