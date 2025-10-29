package com.xabber.di

import android.os.Build
import androidx.annotation.RequiresApi
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.presentation.application.fragments.chat.viewmodel.ChatViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

@RequiresApi(Build.VERSION_CODES.O)
val dataModule = module {
    viewModel { (chatId: String) ->
        try {
            val parts = chatId.split("_")
            require(parts.size == 3) { "Invalid chatId format: $chatId" }

            val opponent = parts[0]
            val owner = parts[1]
            val rawType = parts[2]

            val conversationType = when (rawType) {
                "urn:xabber:chat" -> ConversationType.Regular
                "https://xabber.com/protocol/groups" -> ConversationType.Group
                "https://xabber.com/protocol/channels" -> ConversationType.Channel
                "urn:xmpp:omemo:2" -> ConversationType.Omemo
                "urn:xmpp:omemo:1" -> ConversationType.Omemo1
                "eu.siacs.conversations.axolotl" -> ConversationType.Axolotl
                else -> {
                    ConversationType.Regular
                }
            }


            ChatViewModel(chatId, owner, opponent, conversationType)
        } catch (e: Exception) {
            // 🚨 EMERGENCY FALLBACK
            val opponent = chatId.split("_").firstOrNull() ?: "fallback@jabber.com"
            ChatViewModel(chatId, "igor.boldin@redsolution.com", opponent, ConversationType.Regular)
        }
    }
}