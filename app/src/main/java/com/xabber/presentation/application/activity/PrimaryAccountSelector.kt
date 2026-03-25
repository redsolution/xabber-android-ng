package com.xabber.presentation.application.activity

object PrimaryAccountSelector {
    fun selectPrimaryAccountId(accounts: Collection<Pair<String, Int>>): String? {
        return accounts
            .asSequence()
            .filter { it.first.isNotBlank() }
            .minByOrNull { it.second }
            ?.first
    }
}
