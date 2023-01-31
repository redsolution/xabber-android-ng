package com.xabber.presentation.application.fragments.account.reorder

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.xmpp.account.Account
import com.xabber.databinding.FragmentReorderAccountBinding
import com.xabber.models.dto.AccountDto
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.contract.navigator
import io.realm.kotlin.Realm

class ReorderAccountsFragment : BaseFragment(R.layout.fragment_reorder_account) {
    private val binding by viewBinding(FragmentReorderAccountBinding::bind)
    private var reorderAccountAdapter: ReorderAccountAdapter? = null
    private var touchHelper: ItemTouchHelper? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbarActions()
        initReorderAccountList()
        binding.appbar.setPadding(0, DisplayManager.getHeightStatusBar(), 0, 0)
    }

    private fun initToolbarActions() {
        binding.toolbarReorderAccounts.setNavigationIcon(R.drawable.ic_close_white)
        binding.toolbarReorderAccounts.setNavigationOnClickListener { navigator().closeDetail() }
        binding.toolbarReorderAccounts.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.check -> {
                    saveOrderAccounts()
                    navigator().goBack()
                }
            }
            true
        }
    }

    private fun saveOrderAccounts() {

    }

    private fun initReorderAccountList() {

        binding.rvReorderAccounts.layoutManager = LinearLayoutManager(context)
        val realm = Realm.open(defaultRealmConfig())
        val account = realm.writeBlocking {
            this.query(AccountStorageItem::class).first().find()
        }
        val accountList = ArrayList<Account>()
        accountList.add(
            Account(
                account?.jid,
                account?.nickname,
                account?.jid,
               UiChanger.getAccountColor()!!,
                 1
            )
        )

        reorderAccountAdapter = ReorderAccountAdapter(accountList) { onStartDrag(it) }
        binding.rvReorderAccounts.adapter = reorderAccountAdapter
        val callback = SimpleItemTouchHelperCallback(reorderAccountAdapter)
        touchHelper = ItemTouchHelper(callback)
        touchHelper?.attachToRecyclerView(binding.rvReorderAccounts)
    }

    private fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        Log.d("drag", "$touchHelper")
        touchHelper?.startDrag(viewHolder)
    }

    override fun onDestroy() {
        super.onDestroy()
        onBackPressedCallback.remove()
        binding.rvReorderAccounts.adapter = null
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            navigator().closeDetail()
        }
    }

}
