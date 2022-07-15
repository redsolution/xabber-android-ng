package com.xabber.presentation.application.fragments.account

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.data.xmpp.account.Account
import com.xabber.databinding.FragmentReorderAccountBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.ReorderAccountAdapter
import com.xabber.presentation.application.contract.navigator

class ReorderAccountsFragment : BaseFragment(R.layout.fragment_reorder_account) {
    private val binding by viewBinding(FragmentReorderAccountBinding::bind)
    private val reorderAccountAdapter: ReorderAccountAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbarActions()
        initReorderAccountList()
    }

    private fun initToolbarActions() {
        binding.toolbarReorderAccounts.setNavigationIcon(R.drawable.ic_close)
        binding.toolbarReorderAccounts.setNavigationOnClickListener { navigator().closeDetail() }
        binding.toolbarReorderAccounts.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.check -> {
                    saveOrderAccounts()
                }
            }
            true
        }
    }
    private fun saveOrderAccounts(){

    }

    private fun initReorderAccountList() {
        binding.rvReorderAccounts.layoutManager = LinearLayoutManager(context)
           val accountList = ArrayList<Account>()
        accountList.add(
            Account(
                "Natalia Barabanshikova",
                "natalia.barabanshikova@redsolution.com",
                "Natalia Barabanshikova",
                R.color.blue_100,
                R.drawable.img
            )
        )
        accountList.add(
            Account(
                "Natalia Barabanshikova",
                "nata@xmpp.ru",
                "Nataly",
                R.color.red_600,
                R.drawable.girl
            )
        )
        binding.rvReorderAccounts.adapter = ReorderAccountAdapter(accountList)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.rvReorderAccounts.adapter = null
    }
}