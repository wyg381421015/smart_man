package com.wyg.smart_man.model

import com.google.gson.JsonObject

//data class ApiResponse(
//    val status: String,
//    val gesture_type: Int,
//    val action: String
//)

sealed class ApiResponse {
    data class Success(val data: JsonObject, val source: String) : ApiResponse()
    data class Error(val message: String) : ApiResponse()
}