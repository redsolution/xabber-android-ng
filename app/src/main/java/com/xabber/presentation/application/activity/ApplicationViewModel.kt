package com.xabber.presentation.application.activity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ApplicationViewModel : ViewModel() {
    private val _showUnread = MutableLiveData<Boolean>()
    val showUnread: LiveData<Boolean> = _showUnread


    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

     fun setShowUnreadValue(showUnread: Boolean) {
        _showUnread.value = showUnread
    }

  fun setUnreadCount(count: Int) {
        _unreadCount.value = count
    }


}