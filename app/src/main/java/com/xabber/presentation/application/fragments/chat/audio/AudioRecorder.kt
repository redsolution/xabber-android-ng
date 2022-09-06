package com.xabber.presentation.application.fragments.chat.audio

import android.media.MediaRecorder
import android.util.Log
import io.realm.internal.platform.appFilesDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class AudioRecorder {
    companion object {
        private val mediaRecorder = MediaRecorder()
        lateinit var file: File

        fun startRecord() = CoroutineScope(Dispatchers.IO).launch {
            try {
                createAudioFile()
                prepareAudioRecorder()
                mediaRecorder.start()
            } catch (e: Exception) {
                Log.d("erroraudio", e.message.toString())
            }
        }

        fun stopRecord(onSuccess: (file: File) -> Unit) {
            try {
                mediaRecorder.stop()
                onSuccess(file)
            } catch (e: Exception) {
                Log.d("erroraudio", e.message.toString())
            }
        }

        fun releaseRecorder() {}

        private fun createAudioFile() {
            file = File(appFilesDirectory())
            file.createNewFile()
        }

        private fun prepareAudioRecorder() {
            mediaRecorder.reset()
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
        }
    }

}
