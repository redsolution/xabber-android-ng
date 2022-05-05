package com.xabber.presentation.application.fragments.contacts

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.xabber.R
import com.xabber.databinding.FragmentContactBinding
import com.xabber.presentation.application.contract.navigator

class ContactsFragment : Fragment(R.layout.fragment_contact), ContactAdapter.Listener {

    private val viewModel = ContactsViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
       val binding = FragmentContactBinding.bind(view)
        val contactAdapter = ContactAdapter(this)
      binding.recyclerView.adapter = contactAdapter

        viewModel.contacts.observe(viewLifecycleOwner) {
            contactAdapter.submitList(it)
        }
    }

    override fun onAvatarClick() {
        navigator().showAccount()
    }

    override fun onContactClick() {
        navigator().showMessage("")
    }

    override fun editContact() {
       navigator().showEditContact("")
    }

    override fun deleteContact() {
        val alertDialog = AlertDialog.Builder(context)
        alertDialog.setTitle("Delete contact?")
        alertDialog.setMessage("Are you sure you want to delete the contact?")
        alertDialog.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()

        }

        alertDialog.setPositiveButton("Delete"){ dialog, _ ->
            dialog.dismiss()
val alertDialog = AlertDialog.Builder(context)
            alertDialog.setView(R.layout.dialog_block_contact)
        }
        alertDialog.show()
    }

    override fun blockContact() {

    }
}