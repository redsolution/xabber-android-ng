package com.xabber.remote

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.osmdroid.util.GeoPoint
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

object NominatimRetrofitModule {

    private const val BASE_URL = "https://nominatim.openstreetmap.org/"

    private val client = OkHttpClient().newBuilder()
        .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .client(client)
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: NominatimApi = retrofit.create(NominatimApi::class.java)

    interface NominatimApi {
        @GET("reverse?format=jsonv2")
        suspend fun fromLonLat(
            @Query("lon") lon: Double,
            @Query("lat") lat: Double,
            @Query("accept-language") language: String,
        ): Place

        @GET("search?format=jsonv2")
        suspend fun search(@Query("q") searchString: String): List<Place>
    }
}

@Parcelize
data class Place(
    val display_name: String,
    val lon: Double,
    val lat: Double,
    val address: Address? = null,
) : Parcelable

fun Place.toGeoPoint() = GeoPoint(lat, lon)

val Place.prettyName: String
    get() = address?.prettyAddress?.takeIf { it.isNotEmpty() } ?: display_name

@Parcelize
data class Address(
    val house_number: String? = null,
    val road: String? = null,
    val state: String? = null,
    val neighbourhood: String? = null,
    val allotments: String? = null,
    val village: String? = null,
    val city: String? = null,
    val country: String? = null,
) : Parcelable

private val Address.prettyAddress: String
    get() = listOfNotNull(
        road,
        house_number?.takeIf { !road.isNullOrEmpty() },
        neighbourhood,
        allotments,
        village,
        city,
        state,
        country?.takeIf { road.isNullOrEmpty() }
    ).joinToString(separator = ", ")