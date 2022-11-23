package com.xabber.presentation.application.dialogs

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.text.bold
import androidx.fragment.app.DialogFragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentDialogStandartBinding
import com.xabber.presentation.AppConstants.CLEAR_HISTORY_BUNDLE_KEY
import com.xabber.presentation.AppConstants.CLEAR_HISTORY_KEY
import com.xabber.presentation.AppConstants.CLEAR_HISTORY_NAME_KEY
import com.xabber.presentation.AppConstants.CONTACT_NAME
import com.xabber.utils.setFragmentResult

class ChatHistoryClearDialog : DialogFragment(R.layout.fragment_dialog_standart) {
    private val binding by viewBinding(FragmentDialogStandartBinding::bind)
    private var name: String = "this contact"

    companion object {
        fun newInstance(_name: String) = ChatHistoryClearDialog().apply {
            arguments = Bundle().apply {
                putString(CLEAR_HISTORY_NAME_KEY, _name)
                name = _name
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) name =
            savedInstanceState.getString(CONTACT_NAME, "this contact")
        binding.tvDialogTitle.text = resources.getString(R.string.dialog_clear_history_chat_title)
        val dialogMessage =
            SpannableStringBuilder().append(resources.getString(R.string.dialog_message_clear_history))
                .bold { append(" $name") }.append("?")
                .append(resources.getString(R.string.chat_dialog_sub_message))
        binding.tvDialogDescription.text = dialogMessage
        binding.buttonDialogNegative.text =
            resources.getString(R.string.dialog_chat_negative_button)
        binding.buttonDialogPositive.text =
            resources.getString(R.string.dialog_chat_positive_button)

        binding.buttonDialogNegative.setOnClickListener {
            setFragmentResult(CLEAR_HISTORY_KEY, bundleOf(CLEAR_HISTORY_BUNDLE_KEY to false))
            dismiss()
        }
        binding.buttonDialogPositive.setOnClickListener {
            setFragmentResult(CLEAR_HISTORY_KEY, bundleOf(CLEAR_HISTORY_BUNDLE_KEY to true))
            dismiss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(CONTACT_NAME, name)
    }

}
