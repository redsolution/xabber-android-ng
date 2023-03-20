package com.xabber.presentation.application.fragments.chat

import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.xabber.databinding.ItemImageFromGalleryBinding
import java.util.ArrayList

class GalleryVH(private val binding: ItemImageFromGalleryBinding): BaseImageVH(binding.root) {
    private var image: ImageView = binding.galleryImage
    private var checkBox: CheckBox = binding.recentImageItemCheckbox
    private val imagePaths = ArrayList<String>()
    private var cl: FrameLayout = binding.frame
    private var tv: TextView = binding.tvDate

    fun getImage(): ImageView = image
    fun getCheckBox(): CheckBox = checkBox
    fun getCl(): FrameLayout = cl
    fun getTv():TextView = tv

}