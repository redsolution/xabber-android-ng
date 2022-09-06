package com.xabber.presentation.application.fragments.account

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.model.xmpp.account.Account
import com.xabber.databinding.ItemAccountForPreferenceBinding

class AccountAdapter(
    private val accountList: ArrayList<Account>,
    private val onAccountClick: (Account) -> Unit
) :
    RecyclerView.Adapter<AccountViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder = AccountViewHolder(
            ItemAccountForPreferenceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), onAccountClick
        )

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) =
        holder.bind(accountList[position])


    override fun getItemCount(): Int = accountList.size

}