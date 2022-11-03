package com.xabber.presentation.application.bottomsheet

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.databinding.BottomSheetTurnOffNotificationsBinding
import com.xabber.presentation.application.fragments.chatlist.SwitchNotifications
import com.xabber.presentation.application.fragments.chatlist.TimeMute
import com.xabber.utils.setFragmentResult

class NotificationBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetTurnOffNotificationsBinding? = null
    private val binding get() = _binding!!
    var behavior: BottomSheetBehavior<*>? = null

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
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        initActions()
    }

    @SuppressLint("SuspiciousIndentation")
    private fun initActions() {
        var result = 0L
        with(binding) {
            rl15min.setOnClickListener {
               result = TimeMute.MIN15.time
                setFragmentResult("requestKey", bundleOf("bundleKey" to result))
                dismiss()
            }
            rl1hour.setOnClickListener {
             result = TimeMute.HOUR1.time
                setFragmentResult("requestKey", bundleOf("bundleKey" to result))
                dismiss()
            }
            rl2hour.setOnClickListener {
                result = TimeMute.HOUR2.time
                setFragmentResult("requestKey", bundleOf("bundleKey" to result))
                dismiss()
            }
            rl1day.setOnClickListener {
                result = TimeMute.DAY1.time
                setFragmentResult("requestKey", bundleOf("bundleKey" to result))
                dismiss()
            }
            rlForever.setOnClickListener {
                result = TimeMute.FOREVER.time
                setFragmentResult("requestKey", bundleOf("bundleKey" to result))
                dismiss()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

}
