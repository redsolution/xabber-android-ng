package com.xabber.presentation.application.dialogs

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.text.bold
import androidx.fragment.app.DialogFragment
import com.xabber.R
import com.xabber.databinding.FragmentDialogStandartBinding
import com.xabber.presentation.AppConstants
import com.xabber.utils.setFragmentResult

class BlockContactDialog : DialogFragment() {
    private var _binding: FragmentDialogStandartBinding? = null
    private val binding get() = _binding!!
    private var contactName: String? = null

    companion object {
        fun newInstance(_contactName: String) = BlockContactDialog().apply {
            arguments = Bundle().apply {
                putString("contactName", _contactName)
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

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvDialogTitle.text = "Block this contact"
        val dialogMessage =
            SpannableStringBuilder().append("Are you sure you want to block ")
                .bold { append(" $contactName") }.append("?")
                .append("\nYou will not be able to exchange messages and presence information with this contact.")
        binding.tvDialogDescription.text = dialogMessage
        binding.buttonDialogNegative.text =
            resources.getString(R.string.dialog_chat_negative_button)
        binding.buttonDialogPositive.text =
            "Block"

        binding.buttonDialogNegative.setOnClickListener {
            setFragmentResult(
                AppConstants.BLOCK_CONTACT,
                bundleOf(AppConstants.BLOCK_CONTACT_BUNDLE_KEY to false)
            )
            dismiss()
        }
        binding.buttonDialogPositive.setOnClickListener {
            setFragmentResult(
                AppConstants.BLOCK_CONTACT,
                bundleOf(AppConstants.BLOCK_CONTACT_BUNDLE_KEY to true)
            )
            dismiss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(AppConstants.CONTACT_NAME, contactName)
    }

}
