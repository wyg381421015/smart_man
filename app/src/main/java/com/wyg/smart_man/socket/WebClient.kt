package com.wyg.smart_man.socket

import android.os.Handler
import android.util.Log
import okhttp3.*

/**
 * WebSocket客户端
 *
 * @param handler 用于与 UI 线程通信的处理器
 * @param serverUrl WebSocket 服务器 URL
 */
class WebClient(private val handler: Handler, private val serverUrl: String) {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket connections opened")
            this@WebClient.webSocket = webSocket
            // 发送一个连接成功的消息到 UI 线程
            val msg = handler.obtainMessage()
            msg.arg1 = CLIENT_ARG
            msg.obj = "ok"
            handler.sendMessage(msg)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Received message: $text")
            val msg = handler.obtainMessage()
            msg.arg1 = CLIENT_INFO
            msg.obj = text
            handler.sendMessage(msg)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket error: ${t.message}")
            close()
        }

        fun onClose(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $reason")
            close()
        }
    }

    fun start() {
        Request.Builder().url(serverUrl).build().also { request ->
            client.newWebSocket(request, listener)
            client.dispatcher.executorService.shutdown() // 关闭 dispatcher 的线程池
        }
    }

    fun send(data: String) {
        webSocket?.send(data) ?: Log.e(TAG, "WebSocket is not connected, cannot send data")
    }

    fun close() {
        webSocket?.close(1000, null)
        webSocket = null
        Log.d(TAG, "WebSocket closed.")
    }

    companion object {
        private const val TAG = "websocket_client"
        const val CLIENT_ARG = 0x13
        const val CLIENT_INFO = 0x14
    }
}
