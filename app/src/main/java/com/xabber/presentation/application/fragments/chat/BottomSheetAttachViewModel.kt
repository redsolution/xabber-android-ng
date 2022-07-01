package com.xabber.presentation.application.fragments.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BottomSheetAttachViewModel: ViewModel() {
    private val _selectedImagesCount = MutableLiveData<Int>()
    val selectedImagesCount: LiveData<Int> = _selectedImagesCount

    fun setSelectedImagesCount(count: Int) {
        _selectedImagesCount.value = count
    }
}