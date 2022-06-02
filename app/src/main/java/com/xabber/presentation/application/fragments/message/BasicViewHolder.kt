package com.xabber.presentation.application.fragments.message

import android.os.Build
import android.text.Layout
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data.dto.MessageDto
import com.xabber.presentation.application.util.StringUtils

abstract class BasicViewHolder(view: View) : RecyclerView.ViewHolder(view) {


    @RequiresApi(Build.VERSION_CODES.N)
    open fun bind(message: MessageDto, isNeedTail: Boolean = true, needDay: Boolean = true) {


    }
}