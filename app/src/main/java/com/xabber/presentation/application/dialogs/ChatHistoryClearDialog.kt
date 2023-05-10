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
import com.xabber.data_base.dao.MessageStorageItemDao
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.presentation.AppConstants.CHAT_ID
import com.xabber.presentation.AppConstants.CLEAR_HISTORY_NAME_KEY
import io.realm.kotlin.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatHistoryClearDialog : DialogFragment() {
    private val realm = Realm.open(defaultRealmConfig())
    private val lastChatStorageItemDao = LastChatStorageItemDao(realm)
    private val messageStorageItemDao = MessageStorageItemDao(realm)

    companion object {
        fun newInstance(name: String, id: String) = ChatHistoryClearDialog().apply {
            arguments = Bundle().apply {
                putString(CLEAR_HISTORY_NAME_KEY, name)
                putString(CHAT_ID, id)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val name = arguments?.getString(CLEAR_HISTORY_NAME_KEY)
            ?: resources.getString(R.string.contact_name_default)
        val dialogMessage =
            SpannableStringBuilder().append(resources.getString(R.string.dialog_message_clear_history))
                .bold { append(" $name") }.append("?")
                .append(resources.getString(R.string.chat_dialog_sub_message))
        val dialog = AlertDialog.Builder(context, R.style.MyAlertDialogStyle)
            .setTitle(R.string.dialog_clear_history_chat_title)
            .setMessage(dialogMessage)
            .setPositiveButton(resources.getString(R.string.dialog_chat_positive_button)) { _, _ ->
                clearHistory()
                dismiss()
            }.setNegativeButton(resources.getString(R.string.dialog_chat_negative_button), null)
        return dialog.create()
    }

    private fun clearHistory() {
        val id = arguments?.getString(CHAT_ID) ?: ""
        lifecycleScope.launch(Dispatchers.IO) {
            realm.write {
                val chat = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                val opponent = chat?.jid
                val messages =
                    this.query(MessageStorageItem::class, "opponent = '$opponent'").find()
                delete(messages)
                chat?.lastMessage = null
                chat?.unread = 0
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

}
