package com.xabber.presentation.application.fragments.contacts

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.databinding.QuestionaryContactFragmentBinding
import com.xabber.model.dto.ContactDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.utils.mask.MaskPrepare

class ContactProfileFragment : DetailBaseFragment(R.layout.questionary_contact_fragment) {
    private val binding by viewBinding(QuestionaryContactFragmentBinding::bind)

    companion object {
        fun newInstance(contactDto: ContactDto): ContactProfileFragment {
            val args =
                Bundle().apply { putParcelable(AppConstants.PARAMS_CONTACT_ACCOUNT, contactDto) }
            val fragment = ContactProfileFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private fun getContact(): ContactDto =
        requireArguments().getParcelable(AppConstants.PARAMS_CONTACT_ACCOUNT)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changeUiWidthData()
        initToolbarActions()
    }

    private fun changeUiWidthData() {
        loadAvatar()
        defineColor()
        binding.toolbar.title = getContact().userName
        binding.tvJid.text = getContact().jid
        binding.tvName.text = getContact().name
        binding.tvSurname.text = getContact().surname
        binding.tvFullName.text = getContact().userName
        binding.tvMail.text = getContact().jid
    }

    private fun loadAvatar() {
        val maskedDrawable =
            MaskPrepare.getDrawableMask(resources, getContact().avatar, UiChanger.getMask().size176)
        Glide.with(this).load(maskedDrawable).into(binding.imContactAvatar)
    }

    private fun defineColor() {
        binding.appbar.setBackgroundResource(getContact().color)
    }

    private fun initToolbarActions() {
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.toolbar.setNavigationOnClickListener { navigator().closeDetail() }
    }

}
