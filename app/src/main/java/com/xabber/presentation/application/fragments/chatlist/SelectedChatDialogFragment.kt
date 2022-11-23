package com.xabber.presentation.application.fragments.chatlist

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.xabber.R
import com.xabber.presentation.AppConstants

class SelectedChatDialogFragment: DialogFragment() {

    companion object {
        fun newInstance(params: SelectedChatParams): SelectedChatDialogFragment {
            val args = Bundle().apply {
                putParcelable(AppConstants.SELECTED_CHAT_PARAMS_KEY, params)
            }
            val fragment = SelectedChatDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private fun getParams(): SelectedChatParams =
        requireArguments().getParcelable(AppConstants.QR_CODE_PARAMS)!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val dialog = Dialog(requireContext())
        val view = layoutInflater.inflate(R.layout.selected_chat_dialog,null)
        return view
    }
}