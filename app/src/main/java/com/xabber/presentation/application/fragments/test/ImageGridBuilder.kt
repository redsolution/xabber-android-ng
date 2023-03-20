package com.xabber.presentation.application.fragments.test

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.xabber.R
import com.xabber.presentation.application.fragments.chat.FileManager

class ImageGridBuilder {
    fun inflateView(parent: ViewGroup, imageCount: Int): View {
        return LayoutInflater.from(parent.context)
            .inflate(getLayoutResource(imageCount), parent, false)
    }

    fun bindView(
        view: View,
        attachments: ArrayList<String?>,
        clickListener: View.OnClickListener?
    ) {
        if (attachments.size == 1) {
            val imageView = getImageView(view, 0)
            bindOneImage(attachments[0]!!, view, imageView)
            imageView.setOnClickListener(clickListener)
        } else {
            val tvCounter = view.findViewById<TextView>(R.id.tvCounter)
            var index = 0
            loop@ for (attachment in attachments) {
                if (index > 5) break@loop
                val imageView = getImageView(view, index)
                bindImage(attachment!!, view, imageView)
                imageView.setOnClickListener(clickListener)
                index++
            }
            if (tvCounter != null) {
                if (attachments.size > MAX_IMAGE_IN_GRID) {
                    tvCounter.text = StringBuilder("+").append(attachments.size - MAX_IMAGE_IN_GRID)
                    tvCounter.visibility = View.VISIBLE
                } else tvCounter.visibility = View.GONE
            }
        }
    }

    private fun bindImage(attachment: String, parent: View, imageView: ImageView) {
        Glide.with(parent.context)
            .load(attachment)
            .centerCrop()
            .placeholder(R.drawable.ic_recent_image_placeholder)
            .error(R.drawable.ic_recent_image_placeholder)
            .into(imageView)
    }

    private fun bindOneImage(attachment: String, parent: View, imageView: ImageView) {
        Glide.with(parent.context)
            .load(attachment)
            .into(imageView)
    }

    private fun getLayoutResource(imageCount: Int): Int {
        return when (imageCount) {
            1 -> R.layout.image_grid_1
            2 -> R.layout.image_grid_2
            3 -> R.layout.image_grid_3
            4 -> R.layout.image_grid_4
            5 -> R.layout.image_grid_5
            else -> R.layout.image_grid_6
        }
    }

    private fun getImageView(view: View, index: Int): ImageView {
        return when (index) {
            1 -> view.findViewById(R.id.ivImage1)
            2 -> view.findViewById(R.id.ivImage2)
            3 -> view.findViewById(R.id.ivImage3)
            4 -> view.findViewById(R.id.ivImage4)
            5 -> view.findViewById(R.id.ivImage5)
            else -> view.findViewById(R.id.ivImage0)
        }
    }

    companion object {
        private const val MAX_IMAGE_IN_GRID = 5
    }
}
