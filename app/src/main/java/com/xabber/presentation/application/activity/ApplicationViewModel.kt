package com.xabber.presentation.application.activity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

open class ApplicationViewModel : ViewModel() {
    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    fun setUnreadCount(count: Int) {
        _unreadCount.value = count
    }


    private val _chatListType = MutableLiveData<ChatListType>()
    val chatListType: LiveData<ChatListType> = _chatListType

    fun setChatListType(chatListType: ChatListType) {
        _chatListType.value = chatListType
    }


}