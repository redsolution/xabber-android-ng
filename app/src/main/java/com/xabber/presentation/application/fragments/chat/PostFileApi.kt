package com.xabber.presentation.application.fragments.chat

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface PostFileApi {
    @Multipart
    @Headers("Content-Type: application/json")
    @POST("upload")
    suspend fun uploadFile(
        @Header("Authorization") authToken: String,
        @Part file: MultipartBody.Part,
        @Part("media_type") mediaType: String
    ): Call<ResponseBody>
}
