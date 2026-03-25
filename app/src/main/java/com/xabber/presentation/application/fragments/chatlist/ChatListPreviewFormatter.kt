package com.xabber.presentation.application.fragments.chatlist

object ChatListPreviewFormatter {
    private val htmlTagRegex = Regex("<[^>]*>")

    fun format(rawBody: String): String {
        if (rawBody.isBlank()) return rawBody

        return rawBody
            .replace("<br>", "\n", ignoreCase = true)
            .replace("<br/>", "\n", ignoreCase = true)
            .replace("<br />", "\n", ignoreCase = true)
            .replace(htmlTagRegex, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim()
    }
}
