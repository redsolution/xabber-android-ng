package com.xabber.presentation.application.fragments.chatlist

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentSpecialNotificationsBinding
import com.xabber.presentation.application.fragments.DetailBaseFragment

class SpecialNotificationsFragment : DetailBaseFragment(R.layout.fragment_special_notifications) {
private val binding by viewBinding(FragmentSpecialNotificationsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
                    "Cancel",
                    DialogInterface.OnClickListener { dialogInterface, i ->
                        dialogInterface.dismiss()
                    })
                dialog.show()
            }
        }
    }

}
