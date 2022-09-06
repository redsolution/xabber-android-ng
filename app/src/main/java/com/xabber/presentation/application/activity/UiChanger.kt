package com.xabber.presentation.application.activity

import com.xabber.model.xmpp.account.Account
import com.xabber.utils.mask.Mask

object UiChanger {
    private var isTablet = false
    private var mainAccount: Account? = null
    private var mask: Mask? = null
    private var accountColor: Int? = null

    fun getMask(): Mask = mask ?: Mask.Circle

    fun setMask(newMask: Mask) {
        mask = newMask
    }

    fun getMainAccount(): Account = mainAccount!!

    fun setMainAccount(newMainAccount: Account) {
        mainAccount = newMainAccount
    }

    fun getAccountColor(): Int? = accountColor

    fun setAccountColor(color: Int) {
        accountColor = color
    }

    fun isTablet(): Boolean = isTablet

    fun setTablet(tablet: Boolean) {
        isTablet = tablet
    }

}