package com.xabber.presentation.application.fragments.contacts

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.data.dto.ContactDto
import com.xabber.databinding.FragmentEditContactBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.MaskedDrawableBitmapShader
import com.xabber.presentation.application.activity.UiChanger.getMask
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.util.AppConstants

class EditContactFragment : BaseFragment(R.layout.fragment_edit_contact) {
    private val binding by viewBinding(FragmentEditContactBinding::bind)

    companion object {
        fun newInstance(contactDto: ContactDto?): EditContactFragment {
            val args =
                Bundle().apply { putParcelable(AppConstants.EDIT_CONTACT_PARAMS, contactDto) }
            val fragment = EditContactFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private fun getContact(): ContactDto? =
        requireArguments().getParcelable(AppConstants.EDIT_CONTACT_PARAMS)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbarActions()
        changeUiWithData()
    }

    private fun initToolbarActions() {
        binding.editContactToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.editContactToolbar.setNavigationOnClickListener { navigator().closeDetail() }
        binding.editContactToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.check -> {
                    // сохранить изменения в базе
                }
            }; true
        }
    }

    private fun changeUiWithData() {
        if (getContact() != null) {
            binding.etName.setText(getContact()?.userName)
            val mPictureBitmap = BitmapFactory.decodeResource(resources, getContact()!!.avatar)
            val mMaskBitmap =
                BitmapFactory.decodeResource(resources, getMask().size48).extractAlpha()
            val maskedDrawable = MaskedDrawableBitmapShader()
            maskedDrawable.setPictureBitmap(mPictureBitmap)
            maskedDrawable.setMaskBitmap(mMaskBitmap)
            binding.imAvatarEditContact.setImageDrawable(maskedDrawable)
        }
    }

}
