package com.xabber.presentation.onboarding.fragments.signup.emoji

import android.content.res.Resources
import android.graphics.Paint
import android.util.Log
import androidx.core.graphics.PaintCompat
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xabber.R
import com.xabber.presentation.application.util.toMap
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringWriter

class EmojiKeyboardViewModel : ViewModel() {

    fun getEmojiMap(resources: Resources): Map<Int, List<String>> {
        val ins: InputStream = resources.openRawResource(R.raw.emojis)
        val writer = StringWriter()
        val buffer = CharArray(1024)
        runCatching {
            val reader = BufferedReader(InputStreamReader(ins, "UTF-8"))
            var n: Int = reader.read(buffer)
            while (n != -1) {
                writer.write(buffer, 0, n)
                n = reader.read(buffer)
            }
        }.also {
            ins.close()
        }.onFailure {
            Log.e(this::class.java.simpleName, it.stackTraceToString())
        }

        val jsonString = writer.toString()
        val collectionType = object : TypeToken<List<EmojiTypeDto>>() {}.type
        val dataset: List<EmojiTypeDto> =
            Gson().fromJson(jsonString, collectionType)

        val resultMap: Map<Int, List<String>> =
            dataset.toMap()
                .map {
                    val key = EmojiKeyboardBottomSheet.emojiTypes[it.key]!!
                    val value = it.value.map { list -> list[0] }
                        .filter { PaintCompat.hasGlyph(Paint(), it) }
                    key to value
                }
                .toMap()

        return resultMap
    }
}