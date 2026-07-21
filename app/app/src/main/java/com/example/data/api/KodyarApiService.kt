package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonClass
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.data.model.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface KodyarApiService {
    @GET("api/get-database")
    suspend fun getDatabase(): KodyarDatabaseResponse

    @GET("api/auth/me")
    suspend fun getMe(
        @Header("X-Session-Token") sessionToken: String
    ): KodyarResponse

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): KodyarResponse

    @POST("api/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): KodyarResponse

    @GET("api/repairs/list")
    suspend fun getRepairs(
        @Header("X-Session-Token") sessionToken: String
    ): KodyarResponse // KodyarResponse can have both 'repairs' or 'data' for orders

    @POST("api/repairs/create")
    suspend fun createRepair(
        @Header("X-Session-Token") sessionToken: String,
        @Body request: RepairRequest
    ): KodyarResponse

    @POST("api/payment/card-verify")
    suspend fun verifyCard(
        @Header("X-Session-Token") sessionToken: String,
        @Body request: CardVerifyRequest
    ): KodyarResponse

    @GET("api/free-views")
    suspend fun getFreeStatus(
        @Header("X-Session-Token") sessionToken: String
    ): KodyarResponse

    @POST("api/free-views")
    suspend fun useFree(
        @Header("X-Session-Token") sessionToken: String,
        @Body request: FreeUseRequest
    ): KodyarResponse

    @GET("api/subscription/plans")
    suspend fun getSubscriptionPlans(): KodyarPlansResponse
}

@JsonClass(generateAdapter = true)
data class LoginRequest(val phone: String, val password: String)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val phone: String,
    val password: String,
    val full_name: String,
    val role: String? = null,
    val city: String? = null,
    val categories: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class RepairRequest(val technician_id: String?, val description: String, val city: String)

@JsonClass(generateAdapter = true)
data class CardVerifyRequest(val card_holder: String, val track_number: String, val product_id: String)

@JsonClass(generateAdapter = true)
data class FreeUseRequest(val type: String)

object KodyarRetrofitClient {
    private const val BASE_URL = "https://kodyar24.ir/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val moshi = Moshi.Builder()
        .add(KodyarCityAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: KodyarApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(KodyarApiService::class.java)
    }
}
