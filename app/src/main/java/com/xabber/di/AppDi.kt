package com.xabber.di

import android.os.Build
import androidx.annotation.RequiresApi
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.presentation.application.fragments.chat.ChatViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

@RequiresApi(Build.VERSION_CODES.O)
val dataModule = module {
    viewModel { (chatId: String) ->
        // Parse chatId to extract owner, opponent, and conversationType
        val parts = chatId.split("_")
        require(parts.size == 3) { "Invalid chatId format: $chatId. Expected format: opponent_owner_conversationType" }
        val opponent = parts[0]
        val owner = parts[1]
        val conversationType = ConversationType.fromRaw(parts[2])
        ChatViewModel(
            chatId = chatId,
            owner = owner,
            opponent = opponent,
            conversationType = conversationType
        )
    }
}