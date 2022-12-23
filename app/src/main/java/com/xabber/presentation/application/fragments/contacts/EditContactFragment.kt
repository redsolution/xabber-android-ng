package com.xabber.presentation.application.fragments.contacts

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentEditContactBinding
import com.xabber.model.dto.ContactDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.activity.UiChanger.getMask
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.utils.mask.MaskPrepare
import com.xabber.utils.mask.MaskedDrawableBitmapShader
import com.xabber.utils.parcelable

class EditContactFragment : DetailBaseFragment(R.layout.fragment_edit_contact) {
    private val binding by viewBinding(FragmentEditContactBinding::bind)
    private var av = 0
    private var colorContact = 0
    private val viewModel: ContactAccountViewModel by viewModels()

    companion object {
        fun newInstance(params: ContactAccountParams): EditContactFragment {
            val args =
                Bundle().apply { putParcelable(AppConstants.EDIT_CONTACT_PARAMS, params)
                }
            val fragment = EditContactFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private fun getParams(): ContactAccountParams =
        requireArguments().parcelable(AppConstants.PARAMS_CONTACT_ACCOUNT)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changeUiWithData()
        initToolbarActions()
        initActions()
    }

    private fun initToolbarActions() {
        binding.editContactToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.editContactToolbar.setNavigationOnClickListener { navigator().closeDetail() }
        binding.editContactToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.check -> {
                    viewModel.setCustomNickName(binding.etName.text.toString())
                    // сохранить изменения в базе
                    navigator().closeDetail()
                }
            }; true
        }
    }

    private fun changeUiWithData() {
        binding.appbar.setBackgroundResource(getParams().color)
        val name = viewModel.getContact(getParams().id).customNickName
        binding.etName.setText(name)
        val maskedDrawable = MaskPrepare.getDrawableMask(resources, getParams().avatar, getMask().size48)
        binding.imAvatarEditContact.setImageDrawable(maskedDrawable)
    }

    private fun initActions() {
        binding.linDeleteContact.setOnClickListener {
        }
    }

}
