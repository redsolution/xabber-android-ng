package com.xabber.presentation.application.dialogs

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.text.bold
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.xabber.R
import com.xabber.databinding.FragmentDialogStandartBinding
import com.xabber.presentation.AppConstants
import com.xabber.utils.setFragmentResult

class DeletingContactDialog: DialogFragment() {
    private var _binding: FragmentDialogStandartBinding? = null
    private val binding get() = _binding!!

    private var contactName: String? = null

    companion object {
        fun newInstance(_contactName: String) = DeletingContactDialog().apply {
            arguments = Bundle().apply {
                putString("contactNameForDeletingChatDialog", _contactName)
                contactName = _contactName
            }

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (savedInstanceState != null) contactName =
            savedInstanceState.getString(AppConstants.CONTACT_NAME)
        _binding = FragmentDialogStandartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvDialogTitle.text = "Delete contact"
        val dialogMessage =
            SpannableStringBuilder().append("Are you sure you want to delete contact ")
                .bold { append(" $contactName") }.append("?")
                .append("\n\nYou will be able to exchange messages and subscription requests.")
        binding.tvDialogDescription.text = dialogMessage
        binding.buttonDialogNegative.text =
            resources.getString(R.string.dialog_button_cancel)
        binding.buttonDialogPositive.text =
            resources.getString(R.string.dialog_button_delete)
binding.checkboxDialog.isVisible = true
        binding.checkboxDialog.text = "Delete history message"
        setupButtons()
    }

    private fun setupButtons() {
        binding.buttonDialogNegative.setOnClickListener {
            setFragmentResult(
                AppConstants.DELETING_CONTACT_DIALOG_KEY,
                bundleOf(
                    AppConstants.DELETING_CONTACT_BUNDLE_KEY to false,
                )
            )
            dismiss()
        }
        binding.buttonDialogPositive.setOnClickListener {
            setFragmentResult(
                AppConstants.DELETING_CONTACT_DIALOG_KEY,
                bundleOf(
                    AppConstants.DELETING_CONTACT_BUNDLE_KEY to true,
                    AppConstants.DELETING_CONTACT_AND_CLEAR_HISTORY to binding.checkboxDialog.isChecked
                )
            )
            dismiss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(AppConstants.CONTACT_NAME, contactName)
    }
}
