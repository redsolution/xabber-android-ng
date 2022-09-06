package com.xabber.presentation.application.fragments.contacts

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.transition.Transition
import android.view.View
import androidx.core.content.ContextCompat
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.xabber.R
import com.xabber.databinding.FragmentContactBinding
import com.xabber.model.dto.ContactDto
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.utils.blur.BlurTransformation
import com.xabber.utils.mask.MaskPrepare

class ContactsFragment : BaseFragment(R.layout.fragment_contact), ContactAdapter.Listener {
    private val binding by viewBinding(FragmentContactBinding::bind)
    private val viewModel = ContactsViewModel()
    private var contactAdapter: ContactAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAvatarWithMask()
        initContactList()
        subscribeViewModel()

    }

    private fun loadAvatarWithMask() {
        val maskedDrawable =
            MaskPrepare.getDrawableMask(resources, R.drawable.img, UiChanger.getMask().size32)
        binding.imAvatar.setImageDrawable(maskedDrawable)
    }

    private fun initContactList() {
        contactAdapter = ContactAdapter(this)
        binding.recyclerView.adapter = contactAdapter
        contactAdapter!!.submitList(viewModel.contacts.value)
    }

    private fun subscribeViewModel() {
        viewModel.contacts.observe(viewLifecycleOwner) {
            contactAdapter!!.submitList(it)
        }
    }

    override fun onAvatarClick(contactDto: ContactDto) {

        navigator().showContactAccount(contactDto)
    }

    override fun onContactClick(chatParams: ChatParams) {
            navigator().showChat(chatParams)
    }

    override fun editContact() {
        //   navigator().showContactAccount(con)
    }

    override fun deleteContact() {
        val alertDialog = AlertDialog.Builder(context)
        alertDialog.setTitle("Delete contact?")
        alertDialog.setMessage("Are you sure you want to delete the contact?")
        alertDialog.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        alertDialog.setPositiveButton("Delete") { dialog, _ ->
            dialog.dismiss()
            val alert = AlertDialog.Builder(context)
            alert.setView(R.layout.dialog_block_contact)
        }
        alertDialog.show()
    }

    override fun blockContact() {
    }

    override fun onDestroy() {
        super.onDestroy()
        contactAdapter = null
    }
}
