package com.xabber.presentation.application.fragments.chat.message

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentDialogStandartBinding
import com.xabber.presentation.AppConstants.CONTACT_NAME
import com.xabber.presentation.AppConstants.DELETING_MESSAGE_BUNDLE_KEY
import com.xabber.presentation.AppConstants.DELETING_MESSAGE_DIALOG_KEY
import com.xabber.presentation.AppConstants.DELETING_MESSAGE_FOR_ALL_BUNDLE_KEY
import com.xabber.presentation.AppConstants.DELETING_MESSAGE_NAME_KEY
import com.xabber.utils.setFragmentResult

class DeletingMessageDialog : DialogFragment(R.layout.fragment_dialog_standart) {
    private val binding by viewBinding(FragmentDialogStandartBinding::bind)
    private var name: String = "this contact"

    companion object {
        fun newInstance(_name: String) = DeletingMessageDialog().apply {
            arguments = Bundle().apply {
                putString(DELETING_MESSAGE_NAME_KEY, _name)
                name = _name
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) {
            name =
                savedInstanceState.getString(CONTACT_NAME, "this contact")
        }
        binding.checkboxDialog.isVisible = true
        setupDescription()
        setupButtons()
    }

    private fun setupDescription() {
        binding.tvDialogTitle.text = resources.getString(R.string.deleting_message_dialog_title)
        binding.tvDialogDescription.text =
            resources.getString(R.string.deleting_message_dialog_subtitle)
        val checkboxText =
            SpannableStringBuilder().append(resources.getString(R.string.deleting_message_dialog_checkbox_text))
                .append(" $name").append("?")
        binding.checkboxDialog.text = checkboxText
        binding.buttonDialogNegative.text =
            resources.getString(R.string.deleting_message_dialog_negative_button)
        binding.buttonDialogPositive.text =
            resources.getString(R.string.deleting_message_dialog_positive_button)
    }

    private fun setupButtons() {
        binding.buttonDialogNegative.setOnClickListener {
            setFragmentResult(
                DELETING_MESSAGE_DIALOG_KEY,
                bundleOf(
                    DELETING_MESSAGE_BUNDLE_KEY to false,
                )
            )
            dismiss()
        }
        binding.buttonDialogPositive.setOnClickListener {
            setFragmentResult(
                DELETING_MESSAGE_DIALOG_KEY,
                bundleOf(
                    DELETING_MESSAGE_BUNDLE_KEY to true,
                    DELETING_MESSAGE_FOR_ALL_BUNDLE_KEY to binding.checkboxDialog.isChecked
                )
            )
            dismiss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(CONTACT_NAME, name)
    }

}
