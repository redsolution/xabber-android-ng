package com.xabber.presentation.application.fragments.account.reorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.dto.AccountDto
import com.xabber.utils.toAccountDto
import io.realm.kotlin.Realm
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ReorderAccountsViewModel : ViewModel() {
    private val realm = Realm.open(defaultRealmConfig())

    fun getAccounts(): List<AccountDto> {
        return try {
            realm.writeBlocking {
                val accountStorageItems = this.query(com.xabber.data_base.models.account.AccountStorageItem::class)
                    .sort("order", Sort.ASCENDING)
                    .find()
                accountStorageItems.map { it.toAccountDto() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun changeAccountOrder(accounts: List<AccountDto>) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                accounts.forEachIndexed { index, accountDto ->
                    val account =
                        this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "primary = '${accountDto.id}'")
                            .first().find()
                    account?.order = index
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }

}
