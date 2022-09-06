package com.xabber.presentation.application.activity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.presentation.application.fragments.chatlist.ChatListType

open class ApplicationViewModel : ViewModel() {
    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private val _chatListType = MutableLiveData<ChatListType>()
    val chatListType: LiveData<ChatListType> = _chatListType

    fun setUnreadCount(count: Int) {
        _unreadCount.value = count
    }

    fun setChatListType(chatListType: ChatListType) {
        _chatListType.value = chatListType
    }

}
