package com.xabber.presentation.application.fragments.chat

import okhttp3.RequestBody
import retrofit2.http.Part


data class UploadFileModel(@Part ("file") val file: RequestBody): java.io.Serializable
