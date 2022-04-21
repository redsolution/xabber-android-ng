package com.xabber.presentation.application.fragments.chat

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.method.DialerKeyListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xabber.R
import com.xabber.databinding.FragmentSpecialNotificationsBinding

class SpecialNotificationsFragment : Fragment() {
    private var binding : FragmentSpecialNotificationsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSpecialNotificationsBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.toolbar?.setNavigationIcon(R.drawable.ic_arrow_left)
        with (binding!!) {
            relativeUse.setOnClickListener {
                switchUse.isChecked = !switchUse.isChecked
            }
            relativePreviewMessages.setOnClickListener {
                switchUse2.isChecked = !switchUse2.isChecked
            }
            relativeSound.setOnClickListener {  }
            relativeVibration.setOnClickListener {
                val dialog = AlertDialog.Builder(context)
                dialog.setTitle("Vibration")


                dialog.setNegativeButton("Cancel", DialogInterface.OnClickListener { dialogInterface, i ->
                    dialogInterface.dismiss()
                })
                dialog.show()
            }
        }
    }
}