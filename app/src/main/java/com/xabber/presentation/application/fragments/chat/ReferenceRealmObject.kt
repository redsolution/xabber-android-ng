package com.xabber.presentation.application.fragments.chat

import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.*

class ReferenceRealmObject {
    object Fields {
        const val UNIQUE_ID = "uniqueId"
        const val TITLE = "title"
        const val FILE_PATH = "filePath"
        const val FILE_URL = "fileUrl"
        const val FILE_SIZE = "fileSize"
        const val IS_IMAGE = "isImage"
        const val IS_VOICE = "isVoice"
        const val IMAGE_WIDTH = "imageWidth"
        const val IMAGE_HEIGHT = "imageHeight"
        const val DURATION = "duration"
        const val MIME_TYPE = "mimeType"
        const val IS_GEO = "isGeo"
    }

    @PrimaryKey
    val uniqueId: String
    var title: String? = null
    var fileUrl: String? = null

    /**
     * If message "contains" file with local file path
     */
    var filePath: String? = null

    /**
     * If message contains URL to image (and may be drawn as image)
     */
    var isImage = false

    /**
     * If message contains URL to a voice-recording file
     */
    var isVoice = false
    var isGeo = false
    var imageWidth: Int? = null
    var imageHeight: Int? = null
    var fileSize: Long? = null
    var mimeType: String? = null

    /** Duration in seconds  */
    var duration: Long? = null
    var longitude = 0.0
    var latitude = 0.0

    init {
        uniqueId = UUID.randomUUID().toString()
    }
}