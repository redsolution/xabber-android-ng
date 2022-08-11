package com.xabber.presentation.application.fragments.account

interface ItemTouchHelperAdapter {

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean
}