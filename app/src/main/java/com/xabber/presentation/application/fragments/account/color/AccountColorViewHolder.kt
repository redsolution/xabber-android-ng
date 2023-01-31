package com.xabber.presentation.application.fragments.account.color

import android.view.View
import android.widget.RadioButton
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.DialogColorPickerItemBinding

class AccountColorViewHolder(
    private val binding: DialogColorPickerItemBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun initColorItem(colorName: String, color: Int, listener: AccountColorPickerAdapter.Listener) {
        binding.colorItem.text = colorName
        binding.colorItemVisual.setImageResource(color)
        binding.colorItem.setOnClickListener {
            listener.onClick(color)
        }
    }

    fun getColorRadioButton(): RadioButton = binding.colorItem

}
