package com.xabber.presentation.application.fragments.contacts

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.databinding.FragmentContactBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.MaskChanger

class ContactsFragment : BaseFragment(R.layout.fragment_contact), ContactAdapter.Listener {
    private val binding by viewBinding(FragmentContactBinding::bind)
    private val viewModel = ContactsViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imAvatar.setBackgroundResource(MaskChanger.getMask().size32)

        val contactAdapter = ContactAdapter(this)
      //  Glide.with(binding.imAvatar).load(R.drawable.img).centerInside().into(binding.imAvatar)

        binding.recyclerView.adapter = contactAdapter

        viewModel.contacts.observe(viewLifecycleOwner) {
            contactAdapter.submitList(it)
        }

        movieRecyclerView()
    }

    override fun onAvatarClick() {
        navigator().showAccount()
    }

    override fun onContactClick(userName: String) {
        navigator().showMessage(userName)
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

        alertDialog.setPositiveButton("Delete") { dialog, _ ->
            dialog.dismiss()
            val alertDialog = AlertDialog.Builder(context)
            alertDialog.setView(R.layout.dialog_block_contact)
        }
        alertDialog.show()
    }

    override fun blockContact() {

    }

    @SuppressLint("ClickableViewAccessibility")
    fun movieRecyclerView() {
//        var flag = false
//        val dX = FloatArray(2)
//        val dY = FloatArray(2)
//        binding.recyclerView.setOnTouchListener { view, motionEvent ->
//            when (motionEvent.action) {
//                MotionEvent.ACTION_DOWN -> {
//                    dX[0] = view.x
//                    dY[0] = view.y
//                    dX[1] = motionEvent.getRawX()
//                    dY[1] = motionEvent.getRawY()
//
//                }
//                MotionEvent.ACTION_MOVE -> {
//                    if (motionEvent.getRawY() + dY[0] > 0) {
//                        if (motionEvent.getRawY() > dY[1]) {
//                            view.animate().y(motionEvent.getRawY() - dY[1]).setDuration(0).start()
//                            binding.buttonArchive.isVisible = true
//                            flag = false
//                        }
//                    }
//                    if (motionEvent.getRawY() < dY[1]) {
//
//                        if (binding.buttonArchive.isVisible) binding.buttonArchive.isVisible = false
//                        flag = true
//                    }
//
//                }
//                MotionEvent.ACTION_UP -> {
//
//                }
//                else -> {
//                    false
//                }
//            }
//            true
//        }

    }

}
