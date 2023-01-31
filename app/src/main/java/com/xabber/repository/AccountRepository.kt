package com.xabber.repository

import android.util.Log
import com.google.gson.GsonBuilder
import com.xabber.models.dto.HostListDto
import com.xabber.models.dto.XabberAccountDto
import com.xabber.remote.AccountService
import io.reactivex.rxjava3.core.Single
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class AccountRepository {

    private val accountClient = Retrofit.Builder()
        .baseUrl("https://api.dev.xabber.com/api/v2/")
        .client(OkHttpClient().newBuilder().addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            Log.d("AccountRepository", request.toString())
            Log.d("AccountRepository", response.toString())
            response
        }.build())
        .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
        .addConverterFactory(
            GsonConverterFactory.create(
                GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
            )
        )
        .build()

    private val accountService = accountClient.create(AccountService::class.java)

    fun getHostList(): Single<HostListDto> = accountService.getHostList()

    fun checkIfNameAvailable(map: Map<String, String>): Single<Any> =
        accountService.checkIfNameAvailable(map)

    fun registerAccount(map: Map<String, String>): Single<XabberAccountDto> =
        accountService.registerAccount(map)
}