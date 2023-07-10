package com.xabber.presentation.application.fragments.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentAddAccountBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.account.AccountViewModel

class AddAccountFragment : DetailBaseFragment(R.layout.fragment_add_account) {
    private val binding by viewBinding(FragmentAddAccountBinding::bind)
    private val viewModel: AccountViewModel by viewModels()
    private var host = "@xabber.com"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addTextChangeListener()
        initializeAddAccountButton()
    }

    private fun addTextChangeListener() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun afterTextChanged(p0: Editable?) {
                binding.inputJid.error = null
                binding.btnAddAccount.isEnabled =
                    binding.inputJid.editText?.text.toString().length > 2 && binding.inputPassword.editText?.text.toString().length > 4
                if (binding.btnAddAccount.isEnabled) binding.btnAddAccount.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.white)
                )
                else binding.btnAddAccount.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.grey_600
                    )
                )
            }
        }
        binding.inputJid.editText?.addTextChangedListener(textWatcher)
        binding.inputPassword.editText?.addTextChangedListener(textWatcher)
    }

    private fun initializeAddAccountButton() {
        binding.btnAddAccount.setOnClickListener {
            val jid = binding.inputJid.editText?.text?.trim().toString()
            val password = binding.inputPassword.editText?.text?.trim().toString()
            if (viewModel.checkIsNameAvailable(jid, host)) {
                viewModel.addAccount(jid, jid, password = password, accountColor = requireContext().resources.getString(R.string.blue))
                navigator().showAccount(jid)
            } else {
                binding.inputJid.error = resources.getString(R.string.signup_username_error_subtitle)
            }
        }
    }

}
