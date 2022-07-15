package com.xabber.presentation.application.activity

import android.content.Context
import com.xabber.data.xmpp.account.Account

object UiChanger {
    private var mainAccount: Account? = null
    private var mask: Mask? = null
    private var accountColor: Int? = null

    fun getMask(): Mask = mask!!

    fun setMask(newMask: Mask) {
        mask = newMask
    }


    fun getMaskedDrawable(context: Context, size: Int) {

    }
    fun getMainAccount(): Account = mainAccount!!

    fun setMainAccount(newMainAccount: Account) {
        mainAccount = newMainAccount
    }

    fun getAccountColor(): Int? = accountColor

    fun setAccountColor(color: Int) {
        accountColor = color
    }
}