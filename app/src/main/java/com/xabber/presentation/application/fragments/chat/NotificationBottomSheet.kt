package com.xabber.presentation.application.fragments.chat

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.data.util.dp
import com.xabber.databinding.BottomSheetAvatarBinding
import com.xabber.databinding.BottomSheetTurnOffNotificationsBinding
import com.xabber.presentation.onboarding.fragments.signup.EmojiAvatarBottomSheet

class NotificationBottomSheet : BottomSheetDialogFragment() {
    private var binding: BottomSheetTurnOffNotificationsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        binding = BottomSheetTurnOffNotificationsBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { setupBottomSheet(it) }
        return dialog
    }

    private fun setupBottomSheet(dialogInterface: DialogInterface) {
        val bottomSheetDialog = dialogInterface as BottomSheetDialog
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
            ?: return
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        val behavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(
            bottomSheet
        )
        bottomSheet.updateLayoutParams {
            this.height = 180.dp
        }
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding!!) {
            tv15min.setOnClickListener {
                Toast.makeText(
                    context, "Notifications are disabled for 15 minutes", Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
            tv1hour.setOnClickListener {
                Toast.makeText(
                    context, "Notifications are disabled for 1 hour", Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
            tv1day.setOnClickListener {
                Toast.makeText(
                    context, "Notifications are disabled for 2 hours", Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
            tv2days.setOnClickListener {
                Toast.makeText(
                    context, "Notifications are disabled for 1 day", Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
            tvForever.setOnClickListener {
                Toast.makeText(
                    context, "Notifications are disabled for forever", Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
        }
    }
}