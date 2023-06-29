package com.xabber.presentation.application.fragments.chat

sealed interface UiState {

    data class ResultList(
        val list: List<Any>
    ) : UiState

    object Loading : UiState

    data class Error(val t: Throwable) : UiState

}