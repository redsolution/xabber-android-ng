package com.xabber.presentation.application.fragments.chat

import android.media.MediaMetadataRetriever
import android.util.LruCache

object HttpFileUploadManager {

    // Cache voice durations to avoid repeated MediaMetadataRetriever I/O
    private val voiceLengthCache = LruCache<String, Long>(64)

    fun getVoiceLength(filePath: String): Long {
        voiceLengthCache.get(filePath)?.let { return it }
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(filePath)
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            var duration: Long = 0
            if (dur != null) {
                duration = Math.round((java.lang.Long.valueOf(dur) / 1000).toFloat()).toLong()
            }
            voiceLengthCache.put(filePath, duration)
            return duration
        } finally {
            try { mmr.release() } catch (_: Exception) { }
        }
    }
}

class ProgressData(
    val fileCount: Int, val progress: Int, val error: String, val isCompleted: Boolean,
    val messageId: String?
) {

    fun error(): String {
        return error
    }
}
