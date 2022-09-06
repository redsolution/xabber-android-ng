package com.xabber.presentation.custom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

class ClickSpan(val url: String, val type: String, private val context: Context) :
    ClickableSpan() {

    override fun onClick(view: View) {
        if (TYPE_HYPERLINK == type) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(browserIntent)
        }
    }

    override fun updateDrawState(ds: TextPaint) {
        if (TYPE_HYPERLINK == type) ds.isUnderlineText = true
        ds.color = ds.linkColor
    }

    companion object {
        const val TYPE_HYPERLINK = "hyperlink"
        const val TYPE_MENTION = "mention"
    }
}
