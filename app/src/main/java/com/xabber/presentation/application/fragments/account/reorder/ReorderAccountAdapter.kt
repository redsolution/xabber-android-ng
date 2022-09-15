package com.xabber.presentation.application.fragments.account.reorder

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.MotionEventCompat
import androidx.recyclerview.widget.RecyclerView
import com.xabber.model.xmpp.account.Account
import com.xabber.databinding.ItemAccountForReorderBinding
import java.util.*

class ReorderAccountAdapter(
    private val accountItems: ArrayList<Account>,
    private val onStartDrag: (holder: ReorderAccountViewHolder) -> Unit
) :
    RecyclerView.Adapter<ReorderAccountViewHolder>(), ItemTouchHelperAdapter {
    var accountList = ArrayList<Account>()

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        Log.d("drag", "$fromPosition, $toPosition")
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
        Log.d("drag", "$fromPosition, $toPosition")
        return true
    }

    fun setAccountItems(newAccountList: ArrayList<Account>) {
         accountList = newAccountList
        accountList.sortedBy { it.order }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReorderAccountViewHolder =
        ReorderAccountViewHolder(
            ItemAccountForReorderBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ReorderAccountViewHolder, position: Int) {
        holder.bind(accountItems[position])
        holder.getImAnchor().setOnTouchListener { _, event ->
            if (MotionEventCompat.getActionMasked(event) ==
                MotionEvent.ACTION_DOWN
            ) {
                onStartDrag(holder)
            }
            false
        }
    }

    override fun getItemCount(): Int = accountItems.size
}