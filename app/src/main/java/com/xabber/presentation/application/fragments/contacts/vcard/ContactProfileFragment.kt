package com.xabber.presentation.application.fragments.contacts.vcard

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.QuestionaryContactFragmentBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.utils.parcelable

class ContactProfileFragment : DetailBaseFragment(R.layout.questionary_contact_fragment) {
    private val binding by viewBinding(QuestionaryContactFragmentBinding::bind)
    private val viewModel: ContactAccountViewModel by viewModels()

    companion object {
        fun newInstance(params: ContactAccountParams): ContactProfileFragment {
            val args =
                Bundle().apply { putParcelable(AppConstants.PARAMS_CONTACT_ACCOUNT, params) }
            val fragment = ContactProfileFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private fun getParams(): ContactAccountParams =
        requireArguments().parcelable(AppConstants.PARAMS_CONTACT_ACCOUNT)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changeUiWidthData()
        initToolbarActions()
        binding.tvJid.isSelected = true
        binding.tvDescription.isSelected = true
        binding.tvMail.isSelected = true
    }

    private fun changeUiWidthData() {
        loadAvatar()
        defineColor()
        val contact = viewModel.getContact(getParams().id)
        binding.toolbar.title = contact.customNickName
        binding.tvJid.text = contact.jid
        val name = contact.nickName?.split(" ")
        binding.tvName.text = name!![0]
        binding.tvSurname.text = name[1]
        binding.tvFullName.text = contact.nickName
        binding.tvMail.text = contact.jid
    }

    private fun loadAvatar() {
        val avatar =
            if (getParams().avatar != null) getParams().avatar else R.drawable.ic_photo_white

    }

    private fun defineColor() {
        val color = R.color.blue_500
        binding.appbar.setBackgroundResource(color)
    }

    private fun initToolbarActions() {
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.toolbar.setNavigationOnClickListener { navigator().goBack() }
    }

}
