package com.xabber.presentation.application.dialogs

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentInterfaceBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.manage.MaskManager

class InterfaceDialog : DialogFragment(R.layout.fragment_interface) {
    private val binding by viewBinding(FragmentInterfaceBinding::bind)
    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val width = (resources.displayMetrics.widthPixels * 0.8).toInt() // 90% of screen width
            val height = (resources.displayMetrics.heightPixels * 0.95).toInt()
            dialog.window?.setLayout(width, height)
            dialog.window?.setGravity(Gravity.CENTER) // Center the dialog
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_interface, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvSubtitleAvatar.text = when (MaskManager.mask) {
            R.drawable.circle -> resources.getString(R.string.circle)
            R.drawable.ic_mask_hexagon -> resources.getString(R.string.hexagon)
            R.drawable.ic_mask_octagon -> resources.getString(R.string.octagon)
            R.drawable.ic_mask_pentagon -> resources.getString(R.string.pentagon)
            R.drawable.ic_mask_rounded -> resources.getString(R.string.rounded)
            R.drawable.ic_mask_squircle -> resources.getString(R.string.squircle)
            R.drawable.ic_mask_star -> resources.getString(R.string.star)
            else -> ""
        }
        binding.avatarSettings.setOnClickListener {
            val maskFrag = MaskDialog()
            maskFrag.show(childFragmentManager, "mask fragment")
//            navigator().showMaskSettings()
//            Toast.makeText(context, "Mode switched!", Toast.LENGTH_SHORT).show()
        }
        binding.chatSettings.setOnClickListener { navigator().showChatSettings() }
        binding.toolbar.navigationIcon = null
        binding.left.setOnClickListener { dismiss() }
    }

}
