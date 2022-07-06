package com.xabber.presentation.application.fragments.chat

import android.widget.CheckBox
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.ItemRecentFileBinding

class RecentFileVH(private val binding: ItemRecentFileBinding) : RecyclerView.ViewHolder(binding.root) {
        private var image: ImageView = binding.imageFile
    private var checkBox: CheckBox = binding.imChecked
    private val imagePaths = ArrayList<String>()

    fun getImage(): ImageView = image
    fun getCheckBox(): CheckBox = checkBox
}