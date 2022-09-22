package com.xabber.presentation.application.fragments.chat.geo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Looper
import com.xabber.presentation.XabberApplication
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class GeolocationThumbnailRepository(private val context: Context) {
    private val thumbCreator: GeolocationThumbnailCreator by lazy {
        GeolocationThumbnailCreator(context)
    }

    private val dir = File(context.filesDir.path, DIRECTORY_PATH).apply {
        if (!exists()) {
            this.mkdirs()
        }
    }


        fun modifyReferenceAndSave(
           lon: Double, lat: Double, bitmapWidth: Int, bitmapHeight: Int, bitmapFilePath: String
        ) {

                //        val bitmap = BitmapFactory.decodeFile(file.path)
                  //      modifyReferenceAndSave(lon, lat, bitmap.width, bitmap.height, file.path)
                    }



    fun removeIfExists(lon: Double, lat: Double, pointerColor: Int) {
        try {
           XabberApplication.newInstance().run {
                File(dir, createName(lon, lat)).delete()
            }
        } catch (ex: Exception) {
            //ignore
        }
    }

    private fun writeToDisk(bitmap: Bitmap, name: String) {
        XabberApplication.newInstance().run  {
            BufferedOutputStream(FileOutputStream(File(dir, name))).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 50, it)
                it.flush()
            }
        }
    }

    private fun createName(lon: Double, lat: Double) =
        "${lon}_${lat}.JPEG"

    private companion object {
        private const val DIRECTORY_PATH = "geolocation_thumbnails"
    }

}
