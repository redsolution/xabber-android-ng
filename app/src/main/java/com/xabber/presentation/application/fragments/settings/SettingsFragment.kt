package com.xabber.presentation.application.fragments.settings

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.data.xmpp.account.Account
import com.xabber.databinding.FragmentSettingsBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.account.AccountAdapter

class SettingsFragment : BaseFragment(R.layout.fragment_settings) {
    private val binding by viewBinding(FragmentSettingsBinding::bind)
    private var accountAdapter: AccountAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbarSettings.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.add_account -> {

                }
                R.id.swap -> {
                    navigator().showReorderAccountsFragment()
                }
//                R.id.circle -> {
//                    UiChanger.setMask(Mask.Circle)
//                    updateMaskAvatar()
//                }
//                R.id.hexagon -> {
//                    UiChanger.setMask(Mask.Hexagon)
//                    updateMaskAvatar()
//                }
//                R.id.octagon -> {
//                    UiChanger.setMask(Mask.Octagon)
//                    updateMaskAvatar()
//                }
//                R.id.pentagon -> {
//                    UiChanger.setMask(Mask.Pentagon)
//                    updateMaskAvatar()
//                }
//                R.id.rounded -> {
//                    UiChanger.setMask(Mask.Rounded)
//                    updateMaskAvatar()
//                }
//                R.id.squirсle -> {
//                    UiChanger.setMask(Mask.Squircle)
//                    updateMaskAvatar()
//                }
//                R.id.star -> {
//                    UiChanger.setMask(Mask.Star)
//                    updateMaskAvatar()
//                }
            }
            true
        }
        fillAccountList()


    }

    private fun fillAccountList() {
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
        binding.rvAccounts.layoutManager = LinearLayoutManager(context)
        accountAdapter = AccountAdapter(accountList) {
            navigator().showAccount(it)
        }
        binding.rvAccounts.adapter = accountAdapter
    }

    override fun onDestroy() {
        super.onDestroy()
        accountAdapter = null
    }


}