package com.xabber.di

import com.xabber.presentation.application.fragments.chat.ChatViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val dataModule = module {

    viewModel { (chatId: String) ->
        ChatViewModel(chatId = chatId)
    }

}



