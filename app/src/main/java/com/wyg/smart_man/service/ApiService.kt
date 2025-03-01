package com.wyg.smart_man.service

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    // 获取手势控制数据
    @GET("robot/control/gesture")
    suspend fun fetchGestureData(@Query("type") type: Int): Response<JsonObject>

    // 获取状态信息
    @GET("robot/control/status")
    suspend fun fetchStatus(): Response<JsonObject>

    // 切换接口
    @GET("robot/web/switch")
    suspend fun fetchWebInfo(@Query("type") type: Int): Response<JsonObject>
}
