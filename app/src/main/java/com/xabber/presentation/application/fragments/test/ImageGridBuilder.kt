package com.xabber.presentation.application.fragments.test

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target.SIZE_ORIGINAL
import com.xabber.R
import com.xabber.dto.MessageReferenceDto
import com.xabber.presentation.application.fragments.chat.MessageChanger
import com.xabber.utils.dp

class ImageGridBuilder {
    fun inflateView(parent: ViewGroup, imageCount: Int): View {
        return LayoutInflater.from(parent.context)
            .inflate(getLayoutResource(imageCount), parent, false)
    }

    fun bindView(
        view: View,
        attachments: ArrayList<MessageReferenceDto>,
        clickListener: View.OnClickListener?
    ) {
        if (attachments.size == 1) {

            val cardView = view.findViewById<CardView>(R.id.cv_message_image)
            cardView.radius =
                if (MessageChanger.cornerValue > 4) (MessageChanger.cornerValue - 4).dp.toFloat() else 1.dp.toFloat()
            val imageView = getImageView(view, 0)

            bindOneImage(attachments[0], view, imageView)
            imageView.setOnClickListener(clickListener)
        } else {
            val radius =
                if (MessageChanger.cornerValue > 4) (MessageChanger.cornerValue - 4).dp.toFloat() else 1.dp.toFloat()
           val cardView1 = view.findViewById<CardView>(R.id.cardview_1)
            val cardView2 = view.findViewById<CardView>(R.id.cardview_2)
              if (cardView1 != null)  cardView1.radius = radius
          if (cardView2 != null) cardView2.radius = radius
            val tvCounter = view.findViewById<TextView>(R.id.tvCounter)
            var index = 0
            loop@ for (attachment in attachments) {
                if (index > 5) break@loop
                val imageView = getImageView(view, index)
                bindImage(attachment, view, imageView)
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

    private fun bindImage(attachment: MessageReferenceDto, parent: View, imageView: ImageView) {
        Glide.with(parent.context)
            .load(attachment.uri)
            .placeholder(R.drawable.ic_recent_image_placeholder)
            .error(R.drawable.ic_recent_image_placeholder)
            .into(imageView)
    }

    private fun bindOneImage(attachment: MessageReferenceDto, parent: View, imageView: ImageView) {
        Log.d("fff", "uri = ${attachment.uri}")
        Glide.with(parent.context)
            .load(attachment.uri)
            .override(1000, 1200)
            // .placeholder(R.drawable.ic_recent_image_placeholder)
            .error(R.drawable.ic_recent_image_placeholder)
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
