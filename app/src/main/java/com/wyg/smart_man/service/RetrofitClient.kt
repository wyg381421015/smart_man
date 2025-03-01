package com.wyg.smart_man.service

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private var retrofit: Retrofit? = null

    fun setBaseUrl(baseUrl: String) {

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // 打印请求和响应的详细信息
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()


        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
        Log.d("RetrofitClient", "Retrofit baseUrl set to: $baseUrl")
    }

    fun getRetrofitInstance(): Retrofit {
        return retrofit ?: throw IllegalStateException("Retrofit instance is not initialized")
    }

    val apiService: ApiService
        get() = getRetrofitInstance().create(ApiService::class.java)
}