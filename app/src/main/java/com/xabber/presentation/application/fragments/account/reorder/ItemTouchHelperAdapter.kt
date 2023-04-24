package com.xabber.presentation.application.fragments.account.reorder

interface ItemTouchHelperAdapter {

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean
}
