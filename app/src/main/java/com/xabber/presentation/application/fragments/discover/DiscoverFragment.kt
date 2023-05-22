package com.xabber.presentation.application.fragments.discover

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.databinding.FragmentDiscoverBinding
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.utils.showToast

class DiscoverFragment : BaseFragment(R.layout.fragment_discover) {
    private val binding by viewBinding(FragmentDiscoverBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.chat_background)
        val colorMatrix = ColorMatrix().apply { setScale(1f, 1f, 1f, 1f) }
        val filter = ColorMatrixColorFilter(colorMatrix)
        val drawable = BitmapDrawable(resources, bitmap).apply { setColorFilter(filter) }
         //   binding.tvDiscover.setColorFilter(Color.,  PorterDuff.Mode.MULTIPLY)
       // val list = ColorStateList
Glide.with(binding.tvDiscover).load("content://media/external/images/media/411797").into(binding.tvDiscover)
    }

}
