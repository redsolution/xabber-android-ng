package com.xabber.presentation.application.fragments.chatlist.add

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentNewContactBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.chatlist.ChatListViewModel

class NewContactFragment : DetailBaseFragment(R.layout.fragment_new_contact) {
    private val binding by viewBinding(FragmentNewContactBinding::bind)
    private val viewModel: ChatListViewModel by viewModels()

    companion object {
        fun newInstance() = NewContactFragment()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeToolbarActions()
        initEditTexts()
    }

    private fun initializeToolbarActions() {
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.toolbar.setNavigationOnClickListener {
            navigator().goBack()
        }

        binding.btnAddContact.setOnClickListener {
            val name = binding.inputName.editText?.text.toString()
            val customName = binding.inputAlias.editText?.text.toString()
            viewModel.insertContactAndChat(name, customName)
         //   val owner = viewModel.getPrimaryAccount()
        //    if (owner != null) navigator().showChat(ChatParams(name, owner, name, null))
        }
    }

    private fun initEditTexts() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun afterTextChanged(p0: Editable?) {
                binding.btnAddContact.isEnabled = p0.toString().length >= 2
                if (p0.toString().length >= 2) binding.btnAddContact.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                else binding.btnAddContact.setTextColor(ContextCompat.getColor(requireContext(), R.color.grey_600))
            }
        }
        binding.inputName.editText?.addTextChangedListener(textWatcher)
        binding.inputName.setEndIconOnClickListener { }
    }

}
