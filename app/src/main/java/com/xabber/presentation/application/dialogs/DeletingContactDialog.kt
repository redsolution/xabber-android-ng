package com.xabber.presentation.application.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.SpannableStringBuilder
import androidx.core.text.bold
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.presentation.AppConstants
import io.realm.kotlin.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeletingContactDialog : DialogFragment() {
    val realm = Realm.open(defaultRealmConfig())

    companion object {
        fun newInstance(contactName: String, contactId: String) = DeletingContactDialog().apply {
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
            SpannableStringBuilder().append(resources.getString(R.string.dialog_deleting_contact_message_part_1))
                .bold { append(" $name") }.append("?")
                .append(resources.getString(R.string.dialog_deleting_contact_message_part_2))
        val dialog = AlertDialog.Builder(context, R.style.MyAlertDialogStyle)
            .setTitle(R.string.dialog_deleting_contact_title)
            .setMessage(dialogMessage)
            .setPositiveButton(resources.getString(R.string.dialog_button_delete)) { _, _ ->
                deleteContact()
                dismiss()
            }.setNegativeButton(resources.getString(R.string.dialog_button_cancel), null)
        return dialog.create()
    }

    private fun deleteContact() {
        val id = arguments?.getString(AppConstants.CONTACT_ID) ?: ""
        lifecycleScope.launch(Dispatchers.IO) {
            realm.write {
                val rosterStorageItem =
                    this.query(RosterStorageItem::class, "primary = '$id'").first().find()
                if (rosterStorageItem != null) rosterStorageItem.isDeleted = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

}
