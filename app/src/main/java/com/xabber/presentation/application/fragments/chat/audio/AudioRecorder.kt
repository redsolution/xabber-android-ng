package com.xabber.presentation.application.fragments.chat.audio

import android.media.MediaRecorder
import io.reactivex.rxjava3.subjects.PublishSubject

class AudioRecorder {
    private val currentPlayingAttachmentId: String? = null
    private var recorder: MediaRecorder? = null
    private var outputFile: String? = null

    fun startRecord(outputPath: String) {
        stopRecord() // Остановить предыдущую запись, если она уже запущена

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputPath)

            try {
                prepare()
                start()
                outputFile = outputPath
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopRecord() {
        recorder?.apply {
            try {
                stop()
                reset()
                release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        recorder = null
    }

    fun getRecordedFilePath(): String? {
        return outputFile
    }



}
object PublishAudioProgress {
    private var subject: PublishSubject<AudioInfo>? = null
    fun updateAudioProgress(
        currentPosition: Int,
        duration: Int,
        attachmentIdHash: Int,
        resultCode: Int,
        timestamp: Long?
    ) {
        subject!!.onNext(
            AudioInfo(
                currentPosition,
                duration,
                attachmentIdHash,
                resultCode,
                timestamp
            )
        )
    }

    init {
        createSubject()
    }

    private fun createSubject() {
        subject = PublishSubject.create()
    }

    fun subscribeForProgress(): PublishSubject<AudioInfo>? {
        return subject
    }

    class AudioInfo internal constructor(
        val currentPosition: Int,
        val duration: Int,
        val attachmentIdHash: Int,
        val resultCode: Int,
        val timestamp: Long?
    )
}
