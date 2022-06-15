package com.xabber.presentation.application.fragments.message

import android.content.Context
import com.xabber.presentation.RecordService
import java.io.File
import java.lang.Exception

object VoiceManager {
    private var tempOpusPath: String? = null

    fun startRecording(context: Context) {
        var tempOpusFile: File? = null
        try {
            tempOpusFile = FileManager.createTempOpusFile("tempOpusFile")
            RecordService.record(context, tempOpusFile.path)
            deleteRecordedFile()
            tempOpusPath = tempOpusFile.path
        } catch (e: Exception) {

        }
    }

   private fun deleteRecordedFile() {
        if (tempOpusPath != null) {
             FileManager.deleteTempFile(tempOpusPath)
        }
        tempOpusPath = null;
    }

}