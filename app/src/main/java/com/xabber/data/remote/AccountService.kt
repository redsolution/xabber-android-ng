package com.xabber.data.remote

import com.xabber.data.HostListDto
import com.xabber.data.dto.XabberAccountDto
import io.reactivex.rxjava3.core.Single
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface AccountService {

    // TODO("Добавить параметор локали")
    @GET("accounts/xmpp/hosts/")
    fun getHostList(): Single<HostListDto>

    /**
     * @param[params] containing:
     * 1) username
     * 2) host
     * 3) captcha key
     */
    @Headers("Content-Type: application/json")
    @POST("accounts/account/exist/")
    @JvmSuppressWildcards
    fun checkIfNameAvailable(@Body params: Map<String, String>): Single<Any>

    /**
     * @param[params] containing:
     * 1) username
     * 2) host
     * 3) password
     * 4) captcha key
     */
    @Headers("Content-Type: application/json")
    @POST("accounts/signup/")
    @JvmSuppressWildcards
    fun registerAccount(@Body params: Map<String, String>): Single<XabberAccountDto>
}