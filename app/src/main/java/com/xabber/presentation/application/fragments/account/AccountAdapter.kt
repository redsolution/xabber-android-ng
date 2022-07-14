package com.xabber.presentation.application.fragments.account

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.data.xmpp.account.Account
import com.xabber.databinding.ItemAccountForPreferenceBinding

class AccountAdapter(private val accountList: ArrayList<Account>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return AccountViewHolder(
            ItemAccountForPreferenceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val colorViewHolder = holder as AccountViewHolder
        colorViewHolder.initAccount(accountList[position])
    }

    override fun getItemCount(): Int = accountList.size

}