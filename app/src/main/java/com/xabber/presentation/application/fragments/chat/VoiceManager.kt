package com.xabber.presentation.application.fragments.chat

import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment
import com.xabber.presentation.XabberApplication
import com.xabber.service.RecordService
import java.io.File

object VoiceManager {
    private var tempOpusPath: String? = null

    fun startRecording(context: Context) {
        var tempOpusFile: File? = null
        try {
            tempOpusFile = FileManager.createTempOpusFile("tempOpusFile", context)
            Log.d("file", "${tempOpusFile.length()}")
            RecordService.record(context, tempOpusFile.path)
            deleteRecordedFile()
            tempOpusPath = tempOpusFile.path
        } catch (e: Exception) {

        }
    }

    fun stopRecording(deleteTempFile: Boolean): Boolean {
        RecordService.stopRecording(XabberApplication.newInstance().baseContext);
        if (deleteTempFile)
            deleteRecordedFile();
        return true;

        return false;
    }


    private fun deleteRecordedFile() {
        if (tempOpusPath != null) {
            FileManager.deleteTempFile(tempOpusPath)
        }
        tempOpusPath = null
    }

}