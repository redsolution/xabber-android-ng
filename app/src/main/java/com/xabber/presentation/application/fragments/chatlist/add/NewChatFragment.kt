package com.xabber.presentation.application.fragments.chatlist.add

import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.fragment.app.DialogFragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentNewChatBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.showToast

class NewChatFragment : BaseFragment(R.layout.fragment_new_chat) {
    private val binding by viewBinding(FragmentNewChatBinding::bind)
    private var title: String? = null

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


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) {
            title = savedInstanceState.getString(AppConstants.NEW_CHAT_KEY)
        }
        setTitle()
        initButtons()
    }

    private fun setTitle() {
        if (title == null) {
            val quotes = getString(R.string.motivating_oneliner).split("\n")
            title = quotes.random().trim()
        }
        binding.tvToolbarTitle.text = title
        binding.tvToolbarTitle.isSelected = true
    }

    private fun initButtons() {
        with(binding) {
            rlAddContact.setOnClickListener {
                showToast("This feature is not implemented")
          //     navigator().showNewContact()
                }
            rlCreateGroup.setOnClickListener {
                showToast("This feature is not implemented")
                //navigator().showNewGroup(false) }
                rvCreateGroupIncognito.setOnClickListener {
                    showToast("This feature is not implemented")
                    // navigator().showNewGroup(true)
                }
            }
        }
    }

}
