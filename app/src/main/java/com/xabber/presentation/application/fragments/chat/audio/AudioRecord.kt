package com.xabber.presentation.application.fragments.chat.audio

import android.media.MediaRecorder
class AudioRecord {

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