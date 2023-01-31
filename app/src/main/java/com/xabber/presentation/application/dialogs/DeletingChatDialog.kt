package com.xabber.presentation.application.dialogs

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.text.bold
import androidx.fragment.app.DialogFragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentDialogStandartBinding
import com.xabber.presentation.AppConstants.CONTACT_NAME
import com.xabber.presentation.AppConstants.DELETING_CHAT_BUNDLE_KEY
import com.xabber.presentation.AppConstants.DELETING_CHAT_KEY
import com.xabber.presentation.AppConstants.DELETING_CHAT_NAME_KEY
import com.xabber.utils.setFragmentResult

class DeletingChatDialog : DialogFragment(R.layout.fragment_dialog_standart) {
    private val binding by viewBinding(FragmentDialogStandartBinding::bind)
    var name: String = "this contact"

    companion object {
        fun newInstance(_name: String) = DeletingChatDialog().apply {
            arguments = Bundle().apply {
                putString(DELETING_CHAT_NAME_KEY, _name)
                name = _name
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) { name =
            savedInstanceState.getString(CONTACT_NAME, "this contact") }
        binding.tvDialogTitle.text = resources.getString(R.string.dialog_delete_chat_title)
        val dialogMessage =
            SpannableStringBuilder().append(resources.getString(R.string.dialog_delete_chat_description))
                .bold { append(" $name") }.append("?")
                .append(resources.getString(R.string.chat_dialog_sub_message))
        binding.tvDialogDescription.text = dialogMessage
        binding.buttonDialogNegative.text =
            resources.getString(R.string.dialog_button_cancel)
        binding.buttonDialogPositive.text =
            resources.getString(R.string.dialog_button_delete)
        binding.buttonDialogNegative.setOnClickListener {
            setFragmentResult(DELETING_CHAT_KEY, bundleOf(DELETING_CHAT_BUNDLE_KEY to false))
            dismiss()
        }
        binding.buttonDialogPositive.setOnClickListener {
            setFragmentResult(DELETING_CHAT_KEY, bundleOf(DELETING_CHAT_BUNDLE_KEY to true))
            Log.d("iii", "Positive")
            dismiss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(CONTACT_NAME, name)
    }

}
