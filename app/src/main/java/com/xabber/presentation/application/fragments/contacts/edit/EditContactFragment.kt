package com.xabber.presentation.application.fragments.contacts.edit

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentEditContactBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.activity.UiChanger.getMask
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.DeletingContactDialog
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.contacts.vcard.ContactAccountParams
import com.xabber.presentation.application.fragments.contacts.vcard.ContactAccountViewModel
import com.xabber.utils.mask.MaskPrepare
import com.xabber.utils.parcelable
import com.xabber.utils.setFragmentResultListener
import com.xabber.utils.showToast

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
        requireArguments().parcelable(AppConstants.EDIT_CONTACT_PARAMS)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changeUiWithData()
        initToolbarActions()
        initActions()

        binding.linDeleteContact.setOnClickListener {
            val dialog = DeletingContactDialog.newInstance(viewModel.getContact(getParams().id).customNickName!!)
            navigator().showDialogFragment(dialog, "")
            setFragmentResultListener(AppConstants.DELETING_CONTACT_DIALOG_KEY) { _, bundle ->
                val result = bundle.getBoolean(AppConstants.DELETING_CONTACT_BUNDLE_KEY)
                val clearHistory = bundle.getBoolean(AppConstants.DELETING_CONTACT_AND_CLEAR_HISTORY)
                if (result)  {viewModel.deleteContact(getParams().id, clearHistory)
                navigator().goBack() }
            }
        }
    }

    private fun initToolbarActions() {
        binding.editContactToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.editContactToolbar.setNavigationOnClickListener { navigator().goBack() }
        binding.editContactToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.check -> {
                    if (binding.etName.text.isNullOrEmpty()) showToast("Field can not be empty")
                    else {
                        viewModel.setCustomNickName(getParams().id, binding.etName.text.toString())
                        // сохранить изменения в базе
                        navigator().goBack()
                    }
                }
            }; true
        }
    }

    private fun changeUiWithData() {
        val color = getParams().color
        val avatar = if (getParams().avatar != null) getParams().avatar!! else R.drawable.ic_photo_white
        binding.appbar.setBackgroundResource(color!!)
        val name = viewModel.getContact(getParams().id).customNickName
        binding.etName.setText(name)
        val maskedDrawable = MaskPrepare.getDrawableMask(resources, avatar, getMask().size48)
        binding.imAvatarEditContact.setImageDrawable(maskedDrawable)
    }

    private fun initActions() {
        binding.linDeleteContact.setOnClickListener {
        }
    }

}
