package com.xabber.presentation.application.fragments.chat

import android.widget.CheckBox
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.ItemRecentImageBinding
import java.util.ArrayList

class RecentImageViewHolder(private val binding:ItemRecentImageBinding): RecyclerView.ViewHolder(binding.root) {
   private var image: ImageView = binding.recentImageItemImage
    private var checkBox: CheckBox = binding.recentImageItemCheckbox
    private val imagePaths = ArrayList<String>()

    fun getImage(): ImageView = image
    fun getCheckBox(): CheckBox = checkBox


}