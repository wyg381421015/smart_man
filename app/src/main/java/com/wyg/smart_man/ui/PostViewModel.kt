package com.wyg.smart_man.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.wyg.smart_man.model.Post

class PostViewModel : ViewModel() {
    private val _post = MutableLiveData<Post?>()
    val post: LiveData<Post?> get() = _post

//    fun fetchPostById(id: Int) {
//        viewModelScope.launch {
//            val response: Response<Post> = RetrofitInstance.api.getPostById(id)
//            if (response.isSuccessful) {
//                _post.value = response.body() // 更新 LiveData
//            } else {
//                _post.value = null // 处理错误情况
//            }
//        }
//    }
}
