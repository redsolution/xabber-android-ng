package com.xabber.presentation.application.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.data.xmpp.account.Account
import com.xabber.databinding.ItemAccountForPreferenceBinding
import com.xabber.presentation.application.fragments.account.ReorderAccountViewHolder
import java.util.*

class ReorderAccountAdapter(private val accountItems: ArrayList<Account>): RecyclerView.Adapter<ReorderAccountViewHolder>() {

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(accountItems, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(accountItems, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReorderAccountViewHolder =
   ReorderAccountViewHolder(
       ItemAccountForPreferenceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
   )

    override fun onBindViewHolder(holder: ReorderAccountViewHolder, position: Int) {
        holder.bind(accountItems[position])
    }

    override fun getItemCount(): Int = accountItems.size
}