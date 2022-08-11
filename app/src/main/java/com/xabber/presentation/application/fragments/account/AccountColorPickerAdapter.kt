package com.xabber.presentation.application.fragments.account

import android.content.res.TypedArray
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.DialogColorPickerItemBinding

class AccountColorPickerAdapter(
    private val nameList: Array<String>,
    private val colors: TypedArray,
    private val checked: Int,
    private val onColorClick: (color: Int) -> Unit
) : RecyclerView.Adapter<AccountColorViewHolder>() {

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
        holder.initColorItem(colorName, color)
        holder.getColorRadioButton().isChecked = position == checked
        holder.itemView.setOnClickListener {
            onColorClick(color)
        }
    }

    override fun getItemCount(): Int = nameList.size

}
