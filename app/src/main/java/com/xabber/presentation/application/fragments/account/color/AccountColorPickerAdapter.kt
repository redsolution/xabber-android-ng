package com.xabber.presentation.application.fragments.account.color

import android.content.res.TypedArray
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.DialogColorPickerItemBinding
import com.xabber.presentation.application.fragments.chatlist.ChatListAdapter

class AccountColorPickerAdapter( private val listener: AccountColorPickerAdapter.Listener,
    private val nameList: Array<String>,
    private val colors: TypedArray,
    private val checked: Int,
) : RecyclerView.Adapter<AccountColorViewHolder>() {

    interface  Listener {
        fun onClick(color: Int)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountColorViewHolder {
        return AccountColorViewHolder(
            DialogColorPickerItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: AccountColorViewHolder, position: Int) {
        val colorName = nameList[position]
        val color: Int = colors.getResourceId(position, 0)
        holder.initColorItem(colorName, color, listener)
        holder.getColorRadioButton().isChecked = position == checked

    }

    override fun getItemCount(): Int = nameList.size

}
