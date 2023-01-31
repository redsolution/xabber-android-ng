package com.xabber.presentation.application.fragments.chatlist.spec_notifications

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentSpecialNotificationsBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment

class SpecialNotificationsFragment : DetailBaseFragment(R.layout.fragment_special_notifications) {
    private val binding by viewBinding(FragmentSpecialNotificationsBinding::bind)

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            navigator().closeDetail()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(onBackPressedCallback)
        binding.toolbar.setNavigationOnClickListener { navigator().closeDetail() }
        with(binding) {
            relativeUse.setOnClickListener {
                switchUse.isChecked = !switchUse.isChecked
            }
            relativePreviewMessages.setOnClickListener {
                switchUse2.isChecked = !switchUse2.isChecked
            }
            relativeSound.setOnClickListener { }
            relativeVibration.setOnClickListener {
                val dialog = AlertDialog.Builder(context)
                dialog.setTitle("Vibration")


                dialog.setNegativeButton(
                    "Cancel"
                ) { dialogInterface, _ ->
                    dialogInterface.dismiss()
                }
                dialog.show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onBackPressedCallback.remove()
    }

}
