package com.xabber.presentation.application.fragments.account

import android.content.res.TypedArray
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.DialogColorPickerItemBinding
import com.xabber.databinding.ItemRecentFileBinding
import com.xabber.presentation.application.fragments.chat.RecentFileVH

class AccountColorPickerAdapter(
    private val nameList: Array<String>,
    private val colors: TypedArray,
    private val checked: Int,
    private val onColorClick: (color: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        Log.d("ppp", "onCreate")
        return AccountColorViewHolder(
            DialogColorPickerItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
         Log.d("ppp", "colorName")
        val colorViewHolder = holder as AccountColorViewHolder
        val colorName = nameList[position]
        val color: Int = colors.getResourceId(position, 0)
        Log.d("ppp", "$colorName")
        colorViewHolder.initColorItem(colorName, color)
    }

    override fun getItemCount(): Int = nameList.size




}