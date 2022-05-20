package com.xabber.presentation.application.fragments.chatlist

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.databinding.BottomSheetTurnOffNotificationsBinding

class NotificationBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetTurnOffNotificationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetTurnOffNotificationsBinding.inflate(inflater, container, false)
        return binding.root
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

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            rl15min.setOnClickListener {
                Toast.makeText(
                    context, "Notifications are disabled for 15 minutes", Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
           rl1hour.setOnClickListener {
                Toast.makeText(
                    context, "Notifications are disabled for 1 hour", Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
            rl2hour.setOnClickListener {
                Toast.makeText(
                    context, "Notifications are disabled for 2 hours", Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
            rl1day.setOnClickListener {
                Toast.makeText(
                    context, "Notifications are disabled for 1 day", Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
            rlForever.setOnClickListener {
                Toast.makeText(
                    context, "Notifications are disabled for forever", Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}