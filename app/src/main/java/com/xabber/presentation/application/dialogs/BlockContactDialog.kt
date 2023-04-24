package com.xabber.presentation.application.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.SpannableStringBuilder
import androidx.core.text.bold
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.xabber.R
import com.xabber.data_base.dao.RosterStorageItemDao
import com.xabber.data_base.defaultRealmConfig
import com.xabber.presentation.AppConstants
import io.realm.kotlin.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BlockContactDialog : DialogFragment() {
    private val realm = Realm.open(defaultRealmConfig())
    private val rosterStorageItemDao = RosterStorageItemDao(realm)

    companion object {
        fun newInstance(contactName: String, contactId: String) = BlockContactDialog().apply {
            arguments = Bundle().apply {
                putString(AppConstants.CONTACT_NAME, contactName)
                putString(AppConstants.CONTACT_ID, contactId)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val name = arguments?.getString(AppConstants.CONTACT_NAME)
            ?: resources.getString(R.string.contact_name_default)
        val dialogMessage =
            SpannableStringBuilder().append(resources.getString(R.string.dialog_block_message_part_1))
                .bold { append(" $name") }.append("?")
                .append(resources.getString(R.string.dialog_block_message_part_2))
        val dialog = AlertDialog.Builder(context, R.style.MyAlertDialogStyle)
            .setTitle(R.string.dialog_block_title)
            .setMessage(dialogMessage)
            .setPositiveButton(
                R.string.dialog_block_positive_button
            ) { _, _ ->
                blockContact()
                dismiss()
            }.setNegativeButton(R.string.dialog_button_cancel, null)
        return dialog.create()
    }

    private fun blockContact() {
        val id = arguments?.getString(AppConstants.CONTACT_ID)
        if (id != null)
            lifecycleScope.launch(Dispatchers.IO) {
                rosterStorageItemDao.setBlocked(id, true)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

}
