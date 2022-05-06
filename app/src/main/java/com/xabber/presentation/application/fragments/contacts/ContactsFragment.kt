package com.xabber.presentation.application.fragments.contacts

import android.app.AlertDialog
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.xabber.R
import com.xabber.data.util.dp
import com.xabber.databinding.FragmentContactBinding
import com.xabber.presentation.application.contract.navigator

class ContactsFragment : Fragment(R.layout.fragment_contact), ContactAdapter.Listener {

    private val viewModel = ContactsViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
       val binding = FragmentContactBinding.bind(view)
        val contactAdapter = ContactAdapter(this)


        val widthDp =
            (Resources.getSystem().displayMetrics.widthPixels / Resources.getSystem().displayMetrics.density).toInt()

        if (widthDp >= 600f) {


         binding.containerContacts.updateLayoutParams<LinearLayout.LayoutParams> {
             this.width = 300.dp

            }
            binding.containerDetails.updateLayoutParams<LinearLayout.LayoutParams> {
             this.width = widthDp - binding.containerContacts.width

         }
        }
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
