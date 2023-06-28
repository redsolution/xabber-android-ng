package com.xabber.presentation.application.fragments.chat

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import io.reactivex.rxjava3.subjects.PublishSubject

object HttpFileUploadManager {
    private val progressSubscribe: PublishSubject<ProgressData> =
        PublishSubject.create()
    private var isUploading = false


   fun uploadFile(
        fileUris: List<Uri>,
        context: Context
    ) {
        if (isUploading) {
            progressSubscribe.onNext(
                ProgressData(
                    0,
                    0,
                    "Uploading already started",
                    false,
                    null
                )
            )
            return
        }
    }

    fun getVoiceLength(filePath: String): Long {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(filePath)
        val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        var duration: Long = 0
        if (dur != null) {
            duration = Math.round((java.lang.Long.valueOf(dur) / 1000).toFloat()).toLong()
        }
        return duration
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
