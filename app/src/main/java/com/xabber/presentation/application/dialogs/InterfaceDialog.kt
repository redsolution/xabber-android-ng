package com.xabber.presentation.application.dialogs

import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentInterfaceBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.presentation.application.manage.MaskManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.dto.AccountDto
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.toAccountDto
import io.realm.kotlin.Realm

class InterfaceDialog : DialogFragment(R.layout.fragment_interface) {
    private val binding by viewBinding(FragmentInterfaceBinding::bind)
    private val realm = Realm.open(defaultRealmConfig())

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val widthDp = DisplayManager.getWidthDp()
            val orientation = resources.configuration.orientation
            if (widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) {
                val width = (resources.displayMetrics.widthPixels * 0.8).toInt() // 90% of screen width
                val height = (resources.displayMetrics.heightPixels * 0.95).toInt()
                dialog.window?.setLayout(width, height)
                dialog.window?.setGravity(Gravity.CENTER) // Center the dialog
            }
            if ((widthDp > 800 && orientation == Configuration.ORIENTATION_LANDSCAPE)) {
                val width = (resources.displayMetrics.widthPixels * 0.48).toInt() // 90% of screen width
                val height = (resources.displayMetrics.heightPixels * 0.97).toInt()
                dialog.window?.setLayout(width, height)
                dialog.window?.setGravity(Gravity.CENTER) // Center the dialog
            }

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
        }
        binding.chatSettings.setOnClickListener {
            val chatSettings = ChatSettingsDialog()
            chatSettings.show(childFragmentManager, "mask fragment")
        }
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.toolbar.setNavigationOnClickListener{dismiss()}

        // Set the toolbar color based on the current account's theme
        setupToolbarColor()
    }

    private fun setupToolbarColor() {
        val account = getPrimaryAccount()
        val colorKey = account?.colorKey ?: resources.getString(R.string.blue) // Default to blue if no account
        val colorRes = ColorManager.convertColorNameToId(colorKey)
        defineColor(colorRes)
    }

    private fun defineColor(colorRes: Int) {
        binding.toolbar.setBackgroundColor(
            ResourcesCompat.getColor(
                resources,
                colorRes,
                requireContext().theme
            )
        )
    }

    private fun getPrimaryAccount(): AccountDto? {
        var accountDto: AccountDto? = null
        val realmAccounts = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
        val primaryAccount = realmAccounts.minByOrNull { it.order }
        if (primaryAccount != null) {
            accountDto = primaryAccount.toAccountDto()
        }
        return accountDto
    }



    override fun onDestroy() {
        super.onDestroy()
        realm.close() // Clean up Realm instance
    }
}