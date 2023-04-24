package com.xabber.presentation.application.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.SpannableStringBuilder
import androidx.core.text.bold
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.xabber.R
import com.xabber.data_base.dao.LastChatStorageItemDao
import com.xabber.data_base.defaultRealmConfig
import com.xabber.presentation.AppConstants.CHAT_ID
import com.xabber.presentation.AppConstants.DELETING_CHAT_NAME_KEY
import io.realm.kotlin.Realm
import kotlinx.coroutines.launch

class DeletingChatDialog : DialogFragment() {
    val realm = Realm.open(defaultRealmConfig())
    private val lastChatDao = LastChatStorageItemDao(realm)

    companion object {
        fun newInstance(name: String, id: String) = DeletingChatDialog().apply {
            arguments = Bundle().apply {
                putString(DELETING_CHAT_NAME_KEY, name)
                putString(CHAT_ID, id)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val name = arguments?.getString(DELETING_CHAT_NAME_KEY)
            ?: resources.getString(R.string.contact_name_default)
        val dialogMessage =
            SpannableStringBuilder().append(resources.getString(R.string.dialog_delete_chat_description))
                .bold { append(" $name") }.append("?")
                .append(resources.getString(R.string.chat_dialog_sub_message))
        val dialog = AlertDialog.Builder(context, R.style.MyAlertDialogStyle)
            .setTitle(R.string.dialog_delete_chat_title)
            .setMessage(dialogMessage)
            .setPositiveButton(resources.getString(R.string.dialog_button_delete)) { _, _ ->
                deleteChat()
                dismiss()
            }.setNegativeButton(resources.getString(R.string.dialog_chat_negative_button), null)
        return dialog.create()
    }

    private fun deleteChat() {
        val id = arguments?.getString(CHAT_ID)
        if (id != null)
            lifecycleScope.launch {
                lastChatDao.deleteItem(id)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

}
