package com.xabber.xmpp.messages.message_archive

data class Page(
    var minIndex: Int,
    var maxIndex: Int
) {
    private var isLocked: Boolean = false

    fun prevPage(callback: () -> Unit) {
        if (isLocked) return
        isLocked = true
        callback.invoke()
    }

    fun nextPage(callback: () -> Unit) {
        if (isLocked) return
        isLocked = true
        callback.invoke()
    }

    fun setCustomPage(newMinIndex: Int, callback: () -> Unit) {
        minIndex = newMinIndex
        callback.invoke()
    }

    fun unlock() {
        isLocked = false
    }
}