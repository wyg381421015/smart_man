package com.wyg.smart_man.ui

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.wyg.smart_man.model.ApiResponse
import com.wyg.smart_man.service.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Response


class GetViewModel : ViewModel() {
    private val _data = MutableLiveData<ApiResponse?>()
    val data: MutableLiveData<ApiResponse?> get() = _data
    private val TAG = "GetViewModel"

    private suspend fun fetchAndHandleResponse(
        apiCall: suspend () -> Response<JsonObject>,
        source: String,
        onSuccess: (JsonObject) -> Unit
    ) {
        try {
            Log.d(TAG, "Fetching data from API for source: $source")
            val response = apiCall()
            if (response.isSuccessful) {
                Log.d(TAG, "Data fetched successfully: ${response.body()}")
                response.body()?.let { jsonObject ->
                    try {
                        onSuccess(jsonObject)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing JSON: ${e.message}", e)
                        _data.postValue(ApiResponse.Error("JSON parsing error: ${e.message}"))
                    }
                } ?: run {
                    _data.postValue(ApiResponse.Error("Empty response body"))
                }
            } else {
                val errorBody = response.errorBody()?.use { it.string() }
                Log.e(TAG, "Error fetching data: $errorBody")
                _data.postValue(ApiResponse.Error("API error: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching data: ${e.message}", e)
            _data.postValue(ApiResponse.Error("Network error: ${e.message}"))
        }
    }

    fun fetchGestureData(type: Int) {
        Log.d(TAG, "fetchData called with type: $type")
        viewModelScope.launch(Dispatchers.IO) {
            fetchAndHandleResponse(
                apiCall = { RetrofitClient.apiService.fetchGestureData(type) },
                source = "fetchGestureData",
                onSuccess = { jsonObject ->
                    _data.postValue(ApiResponse.Success(jsonObject, "fetchGestureData"))
                }
            )
        }
    }
    fun fetchStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            fetchAndHandleResponse(
                apiCall = { RetrofitClient.apiService.fetchStatus() },
                source = "fetchStatus",
                onSuccess = { jsonObject ->
                    _data.postValue(ApiResponse.Success(jsonObject, "fetchStatus"))
                }
            )
        }
    }
    fun fetchWebInfo(type: Int) {
        Log.d(TAG, "fetchWebInfo called with type: $type")
        viewModelScope.launch(Dispatchers.IO) {
            fetchAndHandleResponse(
                apiCall = { RetrofitClient.apiService.fetchWebInfo(type) },
                source = "fetchWebInfo",
                onSuccess = { jsonObject ->
                    _data.postValue(ApiResponse.Success(jsonObject, "fetchWebInfo"))
                }
            )
        }
    }

//
//    fun fetchGestureData(type: Int) {
//        Log.d(TAG, "fetchData called with type: $type")
//        viewModelScope.launch(Dispatchers.IO) {
//            try {
//                Log.d(TAG, "Fetching gesture data from API...")
//                val response: Response<JsonObject> = RetrofitClient.apiService.fetchGestureData(type)
//                val apiSource = "fetchGestureData"
//
//                if (response.isSuccessful) {
//                    Log.d(TAG, "Data fetched successfully: ${response.body()}")
//                    response.body()?.let { jsonObject ->
//                        try {
//                            _data.postValue(ApiResponse.Success(jsonObject, apiSource))
//                        } catch (e: Exception) {
//                            Log.e(TAG, "Error parsing JSON: ${e.message}", e)
//                            _data.postValue(ApiResponse.Error("JSON parsing error: ${e.message}"))
//                        }
//                    } ?: run {
//                        _data.postValue(ApiResponse.Error("Empty response body"))
//                    }
//                } else {
//                    val errorBody = response.errorBody()?.use { it.string() }
//                    Log.e(TAG, "Error fetching data: $errorBody")
//                    _data.postValue(ApiResponse.Error("API error: $errorBody"))
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Error fetching data: ${e.message}", e)
//                _data.postValue(ApiResponse.Error("Network error: ${e.message}"))
//            }
//        }
//    }
//
//    fun fetchStatus() {
//        viewModelScope.launch(Dispatchers.IO) {
//            try {
//                Log.d(TAG, "Fetching status from API...")
//                val response: Response<JsonObject> = RetrofitClient.apiService.fetchStatus()
//                val apiSource = "fetchStatus" // 标识符
//
//                if (response.isSuccessful) {
//                    Log.d(TAG, "Data fetched successfully: ${response.body()}")
//                    response.body()?.let { jsonObject ->
//                        try {
//                            // 假设我们需要解析一个字段
//                            val step = jsonObject.get("step").asInt
//                            _data.postValue(ApiResponse.Success(jsonObject, apiSource))
//                        } catch (e: Exception) {
//                            Log.e(TAG, "Error parsing JSON: ${e.message}", e)
//                            _data.postValue(ApiResponse.Error("JSON parsing error: ${e.message}"))
//                        }
//                    } ?: run {
//                        _data.postValue(ApiResponse.Error("Empty response body"))
//                    }
//                } else {
//                    val errorBody = response.errorBody()?.use { it.string() }
//                    Log.e(TAG, "Error fetching data: $errorBody")
//                    _data.postValue(ApiResponse.Error("API error: $errorBody"))
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Error fetching data: ${e.message}", e)
//                _data.postValue(ApiResponse.Error("Network error: ${e.message}"))
//            }
//        }
//    }
//
//    fun fetchWebInfo(type: Int) {
//        Log.d(TAG, "fetchWebInfo called with type: $type")
//        viewModelScope.launch(Dispatchers.IO) {
//            try {
//                Log.d(TAG, "Fetching web info from API...")
//                val response: Response<JsonObject> = RetrofitClient.apiService.fetchWebInfo(type)
//                val apiSource = "fetchWebInfo" // 标识符
//
//                if (response.isSuccessful) {
//                    Log.d(TAG, "Data fetched successfully: ${response.body()}")
//                    response.body()?.let { jsonObject ->
//                        try {
//                            // 假设我们需要解析一个字段
//                            val message = jsonObject.get("message").asString
//                            _data.postValue(ApiResponse.Success(jsonObject, apiSource))
//                        } catch (e: Exception) {
//                            Log.e(TAG, "Error parsing JSON: ${e.message}", e)
//                            _data.postValue(ApiResponse.Error("JSON parsing error: ${e.message}"))
//                        }
//                    } ?: run {
//                        _data.postValue(ApiResponse.Error("Empty response body"))
//                    }
//                } else {
//                    val errorBody = response.errorBody()?.use { it.string() }
//                    Log.e(TAG, "Error fetching data: $errorBody")
//                    _data.postValue(ApiResponse.Error("API error: $errorBody"))
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Error fetching data: ${e.message}", e)
//                _data.postValue(ApiResponse.Error("Network error: ${e.message}"))
//            }
//        }
//    }
}
