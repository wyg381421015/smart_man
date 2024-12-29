package com.wyg.smart_man.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import android.util.Pair
import com.wyg.smart_man.protocol.ProtocolHandler
import com.wyg.smart_man.socket.ClientLastly
import com.wyg.smart_man.utils.MsgConstants
import com.wyg.smart_man.utils.ProtocolCommand
import com.wyg.smart_man.utils.UiConstants
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SocketClientService : Service() {

    private var clientOmni: ClientLastly? = null
    private var clientLaser: ClientLastly? = null
    private var connectStatus = false

    private var serviceMessenger: Messenger?=null
    private var activityMessenger:Messenger?=null

    private val protocolHandler = ProtocolHandler()
    private var isAutoMode = true
    private var isSending = false
    private var isScheduled = false

    @SuppressLint("HandlerLeak")
    private inner class ActivityHandler : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MsgConstants.MSG_ACTIVITY -> {
                    activityMessenger = msg.replyTo
                    parseMessage(msg)
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private val clientHandler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            // 确保 msg.obj 是 Pair 类型
            if (msg.obj is Pair<*, *>) {
                val pair = msg.obj as Pair<*, *>
                val content = pair.first as? String // 尝试将第一个元素转换为 String
                val clientName = pair.second as? String // 尝试将第二个元素转换为 String

                // 检查 content 和 clientName 是否为 null
                if (content != null && clientName != null) {
                    when (clientName) {
                        "client_omni" -> handleClientOmniData(msg, content)
                        "client_laser" -> { /* 处理 ClientLaser 的数据 */ }
                    }
                } else {
                    Log.e(TAG, "内容或客户端名称为 null")
                }
            } else {
                Log.e(TAG, "msg.obj 不是 Pair 类型")
            }
        }
    }

    private fun parseMessage(msg: Message) {
        when (msg.what) {
            MsgConstants.MSG_ACTIVITY -> {
                // 从消息中获取 Bundle
                val bundle = msg.data

                // 如果数据不为空，则解析每个键值对
                bundle?.let { // 如果需要循环处理，使用 allKeys 方法
                    loop@ for (key in it.keySet()) { // 遍历所有键
                        when (key) {
                            UiConstants.CONNECT -> {
                                val ip = it.getString(key)
                                val port = it.getString("port")

                                // 使用安全调用运算符
                                ip?.let { nonNullIp ->
                                    port?.let { nonNullPort ->
                                        tcpClientConnect(nonNullIp, nonNullPort.toInt())
                                    }
                                }
                                break@loop
                            }
                            UiConstants.AUTO_CONTROL->{
                                setAutoMode()
                            }
                            UiConstants.MANUAL_CONTROL->{
                                setManaulMode()
                            }
                            UiConstants.OMNI_PARAMS->{
                                val vx = it.getFloat("Vx")
                                val vy = it.getFloat("Vy")
                                val omega = it.getFloat("omega")

                                val parameters = ByteBuffer.allocate(12).apply {
                                    order(ByteOrder.LITTLE_ENDIAN) // 设置字节顺序为小端
                                    clear() // 确保缓冲区处于初始状态
                                    putFloat(vx)
                                    putFloat(vy)
                                    putFloat(omega)
                                }.array()
                                sendCommand(ProtocolCommand.MANUAL_CONTROL_SPEED, parameters)
                                break@loop
                            }
                        }
                    }
                } ?: run {
                    Log.d(TAG,"No data found in Bundle")
                }
            }
            else -> {
                Log.d(TAG,"Unknown message type: ${msg.what}")
            }
        }
    }

    private fun sendMessageToActivity(params: Map<String, Any>) {
        // 发送消息到 Service
        val msg = Message.obtain(null, MsgConstants.MSG_SERVICE)
        // 使用 Bundle 存储多个参数
        msg.data = Bundle().apply {
            for ((key, value) in params) {
//                putString(key, value) // 将每个键值对放入 Bundle
                when (value) {
                    is String -> putString(key, value)           // 添加字符串
                    is Int -> putInt(key, value)                 // 添加整数
                    is Float -> putFloat(key, value)             // 添加浮点数
                    is Double -> putDouble(key, value)           // 添加双精度浮点数
                    is Long -> putLong(key, value)               // 添加长整形
                    is Byte -> putByte(key, value)               // 添加字节
                    is Boolean -> putBoolean(key, value)         // 添加布尔值
                    is Char -> putChar(key, value)               // 添加字符
                    is Bundle -> putBundle(key, value)           // 添加 Bundle（如果需要）
                    else -> throw IllegalArgumentException("Unsupported type: ${value.javaClass.name}")
                }
            }
        }

        try {
            activityMessenger?.send(msg)
//            Log.d(TAG, "Message sent successfully.")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to send message: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onCreate() {
        Log.d(TAG,"onCreate")
        super.onCreate()
    }

    override fun onBind(intent: Intent): IBinder {
//        binder = Messenger(handler).binder as MyBinder
        serviceMessenger = Messenger(ActivityHandler())
        Log.d(TAG,"onBind")
        return serviceMessenger!!.binder
    }

    private fun tcpClientConnect(ip:String, port:Int){
        // 清理任何之前的连接
        if (clientOmni != null) {
            Log.d(TAG,"已连接，请先断开")
            return // 如果当前已经建立连接，阻止重连
        }
        try {
            clientOmni = clientOmni ?: ClientLastly("client_omni", clientHandler, ip, port).apply { Thread(this).start() }
//                clientLaser = clientLaser ?: ClientLastly("client_laser", handler, ipOmni, PORT_LASER).apply { Thread(this).start() }
        } catch (e: Exception) {
            Log.e(TAG, "连接失败: ${e.message}")
            clientOmni = null // 将 clientOmni 设置为 null 以允许重试
        }
    }

//    inner class MainRunnable : Runnable {
//        override fun run() {
//            if (!isSending) return
//
//            // 网络请求处理
//            thread {
//                try {
//                    sendManualCommand()
//                } catch (e: IOException) {
//                    Log.e("MainActivity", "发送手动命令失败: ${e.message}")
//                }
//            }
//
////            commandSender.postDelayed(this, COMMAND_CONTROL_SEND_INTERVAL) // 每5秒发送一次
//        }
//    }
    private fun setAutoMode(){
        Thread {
            if (clientOmni != null)
            {
                val parameters = byteArrayOf(0x00)

                sendCommand(ProtocolCommand.AUTO_OR_MANUAL, parameters)
            }
        }.start()
    }
    private fun setManaulMode(){

        Thread {
            if (clientOmni != null)
            {
                val parameters = byteArrayOf(0x01)

                sendCommand(ProtocolCommand.AUTO_OR_MANUAL, parameters)
            }
        }.start()
    }
    private fun startManualControlTask(command: String, parameters: ByteArray){
        var isRunning = true

        Thread {
            while (isRunning) {
                if (clientOmni != null)
                {
                    // 执行你想要的周期性任务
                    sendCommand(ProtocolCommand.MANUAL_CONTROL_SPEED,parameters)
                }

                // 睡眠，设置周期时间（例如每 5 秒执行一次）
                try {
                    Thread.sleep(5000) // 休眠 5 秒
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt() // 处理中断
                }
            }
        }.start()
    }
    private fun startQueryBatteryInfoPeriodicTask(){
        var isRunning = true

        Thread {
            while (isRunning) {
                if (clientOmni != null)
                {
                    // 执行你想要的周期性任务
                    sendQueryBatteryInfoCommand()
                }

                // 睡眠，设置周期时间（例如每 5 秒执行一次）
                try {
                    Thread.sleep(5000) // 休眠 5 秒
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt() // 处理中断
                }
            }
        }.start()
    }

    private fun sendQueryBatteryInfoCommand()
    {
        val parameters = byteArrayOf()

        sendCommand(ProtocolCommand.QUERY_BATTERYINFO, parameters)
    }

    private fun handleClientOmniData(msg: Message, content: String) {
        when (msg.arg1) {
            MsgConstants.CLIENT_ARG -> handleClientArg(content)
            MsgConstants.CLIENT_INFO -> handleClientInfo(content)
            else -> Log.d(TAG, "handle error, unknown arg1=${msg.arg1}")
        }
    }

    private fun handleClientArg(content: String) {
        when (content) {
            "ok" -> {
                connectStatus = true
                startQueryBatteryInfoPeriodicTask()
            }
            "fail" -> {
                connectStatus = false
                clientOmni = null
                clientLaser = null
            }
            "disconnected" ->{
                connectStatus = false
                clientOmni = null
                clientLaser = null
            }
        }
        val msg = mapOf(
            UiConstants.CONNECT to content
        )
        sendMessageToActivity(msg)
    }

    private fun handleClientInfo(response: String) {
//        Log.d(TAG, "Received response: $response") // 输出接收到的响应

        val payloads = protocolHandler.handleResponse(response)

        if (!payloads.isNullOrEmpty()) {
            Log.d(TAG, "Number of payloads: ${payloads.size}") // 输出有效 payload 数量

            for (payload in payloads) { // 遍历所有有效的负载
                // 检查 payload 的长度
                if (payload.size < 10) {
                    Log.e(TAG, "Payload size too short: ${payload.size}")
                    continue // 继续到下一个 payload
                }

                // 输出当前 payload 的字节内容
//                Log.d("MainActivity", "Processing payload: ${payload.joinToString(", ") { "0x${it.toString(16).padStart(2, '0')}" }}")
                Log.d(TAG, "Processing payload: ${payload.joinToString(", ") { "0x${(it.toInt() and 0xFF).toString(16).padStart(2, '0')}" }}")

                // 示例：使用payload进行数据提取和处理
                when (payload[7]) {
                    0x03.toByte() -> {
                        val faultStatus = (payload[8].toInt() and 0x01)
                        val msg = mapOf(
                            UiConstants.ALARM_INFO to faultStatus.toString()
                        )
                        sendMessageToActivity(msg)
//                        Log.e(TAG,"fault-status = $faultStatus")
                    }
                    0x08.toByte() -> {

                    }
                    0x0a.toByte() -> {

                    }
                    0x11.toByte() -> {

                    }
                    0x12.toByte() -> {
                        val batSoc = (payload[8].toInt() and 0xFF) shl 24 or
                                (payload[9].toInt() and 0xFF) shl 16 or
                                (payload[10].toInt() and 0xFF) shl 8 or
                                (payload[11].toInt() and 0xFF)

                        val socValue = (batSoc / 100).toString()
                        val msg = mapOf(
                            UiConstants.BASE_SOC to socValue
                        )
                        sendMessageToActivity(msg)

                        Log.d(TAG, "Battery SOC Value: $socValue")
                        Log.d(TAG, "Raw Battery SOC Bytes: " +
                                "0x${payload[8].toString(16).padStart(2, '0')} " +
                                "0x${payload[9].toString(16).padStart(2, '0')} " +
                                "0x${payload[10].toString(16).padStart(2, '0')} " +
                                "0x${payload[11].toString(16).padStart(2, '0')}")
                    }
                    else -> {
                        Log.e(TAG, "Unknown command in payload: 0x${payload[7].toString(16)}")
                    }
                }
            }
        } else {
            Log.e(TAG, "Invalid response received or no payloads found")
        }
    }

    private fun sendCommand(command: String, parameters: ByteArray) {
        if (clientOmni == null) {
            return
        }

        val frame = protocolHandler.frameData(command, parameters)
        if (frame.isEmpty()) {
            return
        }

        try {
//            val frameStr = frame.joinToString("") { String.format("%02X", it) }
            clientOmni?.sendHex(frame)
        } catch (e: Exception) {
            Log.e(TAG, "发送命令失败: ${e.message}")
        }
    }

    // 扩展 ByteArray，以便进行 hex 打印
    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02X".format(it) }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        const val TAG = "SocketClientService"
    }

}