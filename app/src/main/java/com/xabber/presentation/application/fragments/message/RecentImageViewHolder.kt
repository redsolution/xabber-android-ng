package com.xabber.presentation.application.fragments.message

import android.content.ContentResolver
import android.database.Cursor
import android.media.Image
import android.os.Build
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.widget.CheckBox
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.ItemRecentImageBinding
import java.lang.Exception
import java.util.ArrayList
import java.util.logging.LogManager

class RecentImageViewHolder(private val binding:ItemRecentImageBinding): RecyclerView.ViewHolder(binding.root) {
   private var image: ImageView = binding.recentImageItemImage
    private var checkBox: CheckBox = binding.recentImageItemCheckbox
    private val imagePaths = ArrayList<String>()

    fun getImage(): ImageView = image
    fun getCheckBox(): CheckBox = checkBox


}