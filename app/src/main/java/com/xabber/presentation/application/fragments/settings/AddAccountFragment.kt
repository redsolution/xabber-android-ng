package com.xabber.presentation.application.fragments.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentAddAccountBinding
import com.xabber.models.xmpp.account.AccountViewModel
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment

class AddAccountFragment : DetailBaseFragment(R.layout.fragment_add_account) {
    private val binding by viewBinding(FragmentAddAccountBinding::bind)
    private val viewModel: AccountViewModel by viewModels()

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
                binding.btnAddAccount.isEnabled =
                    binding.inputJid.editText?.text.toString()
                        .isNotEmpty() && binding.inputPassword.editText?.text.toString()
                        .isNotEmpty()
            }
        }
        binding.inputJid.editText?.addTextChangedListener(textWatcher)
        binding.inputPassword.editText?.addTextChangedListener(textWatcher)
    }

    private fun initializeAddAccountButton() {
        binding.btnAddAccount.setOnClickListener {
            val jid = binding.inputJid.editText?.text.toString()
            viewModel.addAccount(jid)
           navigator().showAccount(jid)
        }
    }
}
