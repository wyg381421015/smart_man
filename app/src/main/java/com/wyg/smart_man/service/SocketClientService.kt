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
import com.wyg.smart_man.protocol.LaserProtocolHandler
import com.wyg.smart_man.protocol.OmniProtocolHandler
import com.wyg.smart_man.socket.ClientLastly
import com.wyg.smart_man.utils.LaserProtocolCommand
import com.wyg.smart_man.utils.MsgConstants
import com.wyg.smart_man.utils.OmniProtocolCommand
import com.wyg.smart_man.utils.ParamsConstants
import com.wyg.smart_man.utils.UiConstants
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SocketClientService : Service() {

    private var clientOmni: ClientLastly? = null
    private var clientLaser: ClientLastly? = null
    private var connectStatus = false

    private var serviceMessenger: Messenger?=null
    private var activityMessenger:Messenger?=null

    private val omniProtocolHandler = OmniProtocolHandler()
    private val laserProtocolHandler = LaserProtocolHandler()
    private var xCoordinate = 0.0f
    private var yCoordinate = 0.0f
    private var theta = 0.0f
    private var destinationAck = false
    private var startdestinationAck = false
    private var stopdestinationAck = false
    private var omniSpeedAck = false
    private var omniMunaulAck = false
    private var isOmniRunning = true
    private var isLaserRunning = true
    private var frameNumber: Int = 0

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
                        "clientOmni" -> handleClientOmniData(msg, content)
                        "clientLaser" -> handleClientLaserData(msg, content)
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
                            UiConstants.OMNICONNECT -> {
                                val omniIp = it.getString(key)
                                // 使用安全调用运算符
                                omniIp?.let { nonNullIp ->
                                        tcpClientConnect(nonNullIp)
                                }
                                break@loop
                            }
                            UiConstants.OMNIDISCONNECT -> {
                                onDisconnect()
                            }
                            UiConstants.AUTOCONTROL->{
                                setAutoMode()
                            }
                            UiConstants.MANUALCONTROL->{
                                setManaulMode()
                            }
                            UiConstants.OMNIPARAMS->{
                                val vx = it.getFloat(UiConstants.XSPEED)
                                val vy = it.getFloat(UiConstants.YSPEED)
                                val omega = it.getFloat(UiConstants.OMEGA)

                                val parameters = ByteBuffer.allocate(12).apply {
                                    order(ByteOrder.LITTLE_ENDIAN) // 设置字节顺序为小端
                                    clear() // 确保缓冲区处于初始状态
                                    putFloat(vx)
                                    putFloat(vy)
                                    putFloat(omega)
                                }.array()

                                Thread{
                                    sendOmniCommand(OmniProtocolCommand.MANUAL_CONTROL_SPEED, parameters)
                                }.start()
                                break@loop
                            }
                            UiConstants.LASERPARAMS ->{
                                xCoordinate = it.getFloat(UiConstants.XCOORDINATE)
                                yCoordinate = it.getFloat(UiConstants.YCOORDINATE)
                                theta = it.getFloat(UiConstants.THETA)

                                if(xCoordinate.isNaN() || yCoordinate.isNaN() || theta.isNaN())
                                    Log.e(TAG,"xCoordinate =$xCoordinate or yCoordinate =$yCoordinate or theta =$theta nan")
                                else
                                    startDestinationTask()
                                break@loop
                            }
                            UiConstants.EMERGENCYSTOP -> {
                                val mode = it.getBoolean(key)
                                startEmergencyStopTask(mode)
                                Log.d(TAG, "recv emergency message" )
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

    private fun tcpClientConnect(omniIp:String){
        // 清理任何之前的连接
        if (clientOmni != null) {
            Log.d(TAG,"已连接，请先断开")
            return // 如果当前已经建立连接，阻止重连
        }
        clientOmni = try {
            clientOmni ?: ClientLastly("clientOmni", clientHandler, omniIp, ParamsConstants.PORT_OMANI).apply { Thread(this).start() }
        } catch (e: Exception) {
            Log.e(TAG, "连接失败: ${e.message}")
            null // 将 clientOmni 设置为 null 以允许重试
        }

        // 清理任何之前的连接
        if (clientLaser != null) {
            Log.d(TAG,"已连接，请先断开")
            return // 如果当前已经建立连接，阻止重连
        }
        clientLaser = try {
            clientLaser ?: ClientLastly("clientLaser", clientHandler, omniIp, ParamsConstants.PORT_LASER).apply { Thread(this).start() }
        } catch (e: Exception) {
            Log.e(TAG, "连接失败: ${e.message}")
            null // 将 clientLaser 设置为 null 以允许重试
        }
    }
    private fun setAutoMode(){
        Thread {
            if (clientOmni != null)
            {
                val parameters = byteArrayOf(0x00)

                sendOmniCommand(OmniProtocolCommand.AUTO_OR_MANUAL, parameters)
            }
        }.start()
    }
    private fun setManaulMode(){

        Thread {
            if (clientOmni != null)
            {
                val parameters = byteArrayOf(0x01)

                sendOmniCommand(OmniProtocolCommand.AUTO_OR_MANUAL, parameters)
            }
        }.start()
    }

    private fun startManualControlTask(command: String, parameters: ByteArray){

        Thread {
            while (isOmniRunning) {
                if (clientOmni != null)
                {
                    // 执行你想要的周期性任务
                    sendOmniCommand(OmniProtocolCommand.MANUAL_CONTROL_SPEED,parameters)
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
        Thread {
            while (isOmniRunning) {
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

        sendOmniCommand(OmniProtocolCommand.QUERY_BATTERYINFO, parameters)
    }

    private fun sendManualOmniCommandWithRetry(): Boolean {
        val maxRetries = 3
        val parameters = byteArrayOf(0x01)


        // 尝试发送命令的重试逻辑
        for (attempt in 1..maxRetries) {
            sendOmniCommand(OmniProtocolCommand.AUTO_OR_MANUAL, parameters)

            // 等待确认并返回结果
            if (waitForOmniConfirmation(OmniProtocolCommand.AUTO_OR_MANUAL)) {
                return true // 收到确认
            }

            // 如果没有收到确认，等待一段时间再重试
            Thread.sleep(10) // 设置重试等待时间
        }
        return false // 超过最大重试次数后返回 false
    }

    private fun sendStopOmniCommandWithRetry(): Boolean {

        val maxRetries = 3
        val vx = 0.0f
        val vy = 0.0f
        val omega = 0.0f

        val parameters = ByteBuffer.allocate(12).apply {
            order(ByteOrder.LITTLE_ENDIAN) // 设置字节顺序为小端
            clear() // 确保缓冲区处于初始状态
            putFloat(vx)
            putFloat(vy)
            putFloat(omega)
        }.array()

        // 尝试发送命令的重试逻辑
        for (attempt in 1..maxRetries) {
            sendOmniCommand(OmniProtocolCommand.MANUAL_CONTROL_SPEED, parameters)

            // 等待确认并返回结果
            if (waitForOmniConfirmation(OmniProtocolCommand.MANUAL_CONTROL_SPEED)) {
                return true // 收到确认
            }

            // 如果没有收到确认，等待一段时间再重试
            Thread.sleep(10) // 设置重试等待时间
        }
        return false // 超过最大重试次数后返回 false
    }

    private fun handleClientOmniData(msg: Message, content: String) {
        when (msg.arg1) {
            MsgConstants.CLIENT_ARG -> handleClientOmniArg(content)
            MsgConstants.CLIENT_INFO -> handleClientOmniInfo(content)
            else -> Log.d(TAG, "handle error, unknown arg1=${msg.arg1}")
        }
    }

    private fun handleClientOmniArg(content: String) {
        when (content) {
            "ok" -> {
                connectStatus = true
                isOmniRunning = true
                startQueryBatteryInfoPeriodicTask()
            }
            in arrayOf("fail", "disconnected") ->{
                connectStatus = false
                isOmniRunning = false
                clientOmni = null
                clientLaser = null
            }
        }
        val msg = mapOf(
            UiConstants.OMNICONNECT to content
        )
        sendMessageToActivity(msg)
    }

    private fun handleClientOmniInfo(response: String) {
//        Log.d(TAG, "Received response: $response") // 输出接收到的响应

        val payloads = omniProtocolHandler.handleResponse(response)

        if (!payloads.isNullOrEmpty()) {
//            Log.d(TAG, "Number of payloads: ${payloads.size}") // 输出有效 payload 数量

            for (payload in payloads) { // 遍历所有有效的负载
                // 检查 payload 的长度
                if (payload.size < 10) {
                    Log.e(TAG, "Payload size too short: ${payload.size}")
                    continue // 继续到下一个 payload
                }

                // 输出当前 payload 的字节内容
//                Log.d("MainActivity", "Processing payload: ${payload.joinToString(", ") { "0x${it.toString(16).padStart(2, '0')}" }}")
//                Log.d(TAG, "Processing payload: ${payload.joinToString(", ") { "0x${(it.toInt() and 0xFF).toString(16).padStart(2, '0')}" }}")

                // 示例：使用payload进行数据提取和处理
                when (payload[7]) {
                    OmniProtocolCommand.UPLOAD_STATUS -> {
                        val faultStatus = (payload[8].toInt() and 0x01)
                        val msg = mapOf(
                            UiConstants.ALARMINFO to faultStatus.toString()
                        )
                        sendMessageToActivity(msg)
//                        Log.e(TAG,"fault-status = $faultStatus")
                    }
                    OmniProtocolCommand.AUTO_OR_MANUAL -> {
                        omniMunaulAck = true
                    }
                    OmniProtocolCommand.MANUAL_CONTROL_SPEED -> {
                        omniSpeedAck = true
                    }
                    OmniProtocolCommand.MANUAL_CONTROL_LIGHT -> {
                    }
                    OmniProtocolCommand.QUERY_BATTERYINFO -> {
                        val batSoc = (payload[8].toInt() and 0xFF) shl 24 or
                                (payload[9].toInt() and 0xFF) shl 16 or
                                (payload[10].toInt() and 0xFF) shl 8 or
                                (payload[11].toInt() and 0xFF)

                        val socValue = (batSoc / 100).toString()
                        val msg = mapOf(
                            UiConstants.BASESOC to socValue
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

    private fun sendOmniCommand(command: Byte, parameters: ByteArray) {
        if (clientOmni == null) {
            return
        }

        val frame = omniProtocolHandler.frameData(command, parameters)
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
    private fun waitForOmniConfirmation(command: Byte): Boolean {
        // 实现确认逻辑，可以是超时机制或者其他方式接收确认消息
        // 这里应该是您用于检测确认的实际逻辑
        return when(command){
            OmniProtocolCommand.AUTO_OR_MANUAL->{
                omniMunaulAck
            }
            OmniProtocolCommand.MANUAL_CONTROL_SPEED->{
                omniSpeedAck
            }
            else -> {
                false
            }
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private fun startDestinationTask(){
        var isRunning = true

        Thread {
            try {
                // 先执行 sendDestinationCommand，直到收到确认
                if (sendDestinationCommandWithRetry()) {
                    // 收到确认后再发送 sendStartDestinationCommand，同样采用重试机制
                    if (sendStartDestinationCommandWithRetry()) {
                        // 收到确认返回，退出当前线程
                        isRunning = false
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt() // 处理中断
            }
        }.start()
    }

    private fun startEmergencyStopTask(mode:Boolean){
//        var isRunning = true

        Thread {
            try {
                if(mode)
                {
                    sendStopDestinationCommandWithRetry()
                } else{
                    sendManualOmniCommandWithRetry()
                    sendStopOmniCommandWithRetry()
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt() // 处理中断
            }
        }.start()
    }

    private fun sendDestinationCommandWithRetry(): Boolean {
        val maxRetries = 1

        destinationAck = false

        frameNumber++
        if(frameNumber > 0x3F)
            frameNumber = 0

        val parameters = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN) // 设置字节顺序为小端
            clear() // 确保缓冲区处于初始状态
            putFloat(xCoordinate)
            putFloat(yCoordinate)
            putFloat(theta)
            putFloat(0.0F)
            putFloat(0.0F)
            putFloat(0.0F)

            repeat(17) {
                put(0x00.toByte())
            }
            val data1 = (frameNumber and 0b00111111)// 提取低6位（0到63）
            val data2= (data1 shl 2) // 将高6位左移2位，低2位为000
            put(data2.toByte()) // 添加到第42个字节

            put(0x00.toByte())
            put(0x00.toByte())
        }.array()

        // 尝试发送命令的重试逻辑
        for (attempt in 1..maxRetries) {
            sendLaserCommand(LaserProtocolCommand.SET_DESTINATION, parameters)

            // 等待确认并返回结果
//            if (waitForLaserConfirmation(LaserProtocolCommand.SET_DESTINATION)) {
//                return true // 收到确认
//            }
//
//            // 如果没有收到确认，等待一段时间再重试
            Thread.sleep(10) // 设置重试等待时间
        }
//        return false // 超过最大重试次数后返回 false
        return true
    }

    private fun sendStartDestinationCommandWithRetry(): Boolean {
        val maxRetries = 1

        startdestinationAck = false

        val parameters = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN) // 设置字节顺序为小端
            clear() // 确保缓冲区处于初始状态
            putFloat(xCoordinate)
            putFloat(yCoordinate)
            putFloat(theta)
            putFloat(0.0F)
            putFloat(0.0F)
            putFloat(0.0F)

            repeat(17) {
                put(0x00.toByte())
            }
            val data1 = (frameNumber and 0b00111111)// 提取低6位（0到63）
            val data2= (data1 shl 2) // 将高6位左移2位，低2位为000
            put(data2.toByte()) // 添加到第42个字节

            put(0x00.toByte())
            put(0x00.toByte())
        }.array()

        // 尝试发送命令的重试逻辑
        for (attempt in 1..maxRetries) {
            sendLaserCommand(LaserProtocolCommand.SET_START, parameters)

//            // 等待确认并返回结果
//            if (waitForLaserConfirmation(LaserProtocolCommand.SET_START)) {
//                return true // 收到确认
//            }
            Thread.sleep(10) // 设置重试等待时间
        }
//        return false // 超过最大重试次数后返回 false
        return true
    }

    private fun sendStopDestinationCommandWithRetry(): Boolean {
        val maxRetries = 1

        startdestinationAck = false

        val parameters = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN) // 设置字节顺序为小端
            clear() // 确保缓冲区处于初始状态
            putFloat(xCoordinate)
            putFloat(yCoordinate)
            putFloat(theta)
            putFloat(0.0F)
            putFloat(0.0F)
            putFloat(0.0F)
            repeat(17) {
                put(0x00.toByte())
            }
            val data1 = (frameNumber and 0b00111111)// 提取低6位（0到63）
            val data2= (data1 shl 2) // 将高6位左移2位，低2位为000
            put(data2.toByte()) // 添加到第42个字节

            put(0x00.toByte())
            put(0x00.toByte())
        }.array()

        // 尝试发送命令的重试逻辑
        for (attempt in 1..maxRetries) {
            sendLaserCommand(LaserProtocolCommand.SET_STOP, parameters)

//            // 等待确认并返回结果
//            if (waitForLaserConfirmation(LaserProtocolCommand.SET_STOP)) {
//                return true // 收到确认
//            }
//
//            // 如果没有收到确认，等待一段时间再重试
            Thread.sleep(10) // 设置重试等待时间
        }
//        return false // 超过最大重试次数后返回 false
        return true
    }

    private fun waitForLaserConfirmation(command: Int): Boolean {
        // 实现确认逻辑，可以是超时机制或者其他方式接收确认消息
        // 这里应该是您用于检测确认的实际逻辑
        return when(command){
            LaserProtocolCommand.SET_DESTINATION->{
                destinationAck
            }

            LaserProtocolCommand.SET_START->{
                startdestinationAck
            }
            LaserProtocolCommand.SET_STOP->{
                stopdestinationAck
            }

            else -> {
                false
            }
        }
    }


    private fun handleClientLaserData(msg: Message, content: String) {
        when (msg.arg1) {
            MsgConstants.CLIENT_ARG -> handleClientLaserArg(content)
            MsgConstants.CLIENT_INFO -> handleClientLaserInfo(content)
            else -> Log.d(TAG, "handle error, unknown arg1=${msg.arg1}")
        }
    }

    private fun handleClientLaserArg(content: String) {
        when (content) {
            "ok" -> {
                connectStatus = true
            }
            "fail" -> {
                connectStatus = false
                clientLaser = null
            }
            "disconnected" ->{
                connectStatus = false
                clientLaser = null
            }
        }
        val msg = mapOf(
            UiConstants.LASERCONNECT to content
        )
        sendMessageToActivity(msg)
    }

    private fun handleClientLaserInfo(response: String) {
//        Log.d(TAG, "Received response: $response") // 输出接收到的响应

        val payloads = laserProtocolHandler.handleResponse(response)

        if (!payloads.isNullOrEmpty()) {
//            Log.d(TAG, "Number of payloads: ${payloads.size}") // 输出有效 payload 数量

            for (payload in payloads) { // 遍历所有有效的负载
                // 检查 payload 的长度
                if (payload.size < 16) {
                    Log.e(TAG, "Payload size too short: ${payload.size}")
                    continue // 继续到下一个 payload
                }

                // 输出当前 payload 的字节内容
//                Log.d("MainActivity", "Processing payload: ${payload.joinToString(", ") { "0x${it.toString(16).padStart(2, '0')}" }}")
//                Log.d(TAG, "Processing payload: ${payload.joinToString(", ") { "0x${(it.toInt() and 0xFF).toString(16).padStart(2, '0')}" }}")

                val command =  ((payload[8].toInt() and 0xFF) shl 24) or
                        ((payload[7].toInt() and 0xFF) shl 16) or
                        ((payload[6].toInt() and 0xFF) shl 8) or
                        (payload[5].toInt() and 0xFF)

                // 示例：使用payload进行数据提取和处理
                when (command) {
                    LaserProtocolCommand.UPLOAD_LOCATION,
                    LaserProtocolCommand.FAIL_PLANNING-> {
                        val xCoordinateArray = payload.copyOfRange(9, 13)
                        val xCoordinate = parseByteArrayToFloat(xCoordinateArray)

                        val yCoordinateArray = payload.copyOfRange(13, 17)
                        val yCoordinate = parseByteArrayToFloat(yCoordinateArray)

                        val thetaArray = payload.copyOfRange(17, 21)
                        val theta = parseByteArrayToFloat(thetaArray)

                        val xVelocityArray = payload.copyOfRange(21, 25)
                        val xVelocity = parseByteArrayToFloat(xVelocityArray)

                        val navStatus = payload[41].toInt()

                        val msg = mapOf(
                            UiConstants.XCOORDINATE to xCoordinate,
                            UiConstants.YCOORDINATE to yCoordinate,
                            UiConstants.THETA to theta,
                            UiConstants.XVELOCITY to xVelocity,
                            UiConstants.NAVSTATUS to navStatus
                        )
                        sendMessageToActivity(msg)
//                        Log.e(TAG,"fault-status = $faultStatus")
                    }
                    LaserProtocolCommand.SET_DESTINATION_ACK-> {
                        destinationAck = true
                    }
                    LaserProtocolCommand.SET_START_ACK-> {
                        startdestinationAck = true
                    }
                    LaserProtocolCommand.SET_STOP_ACK-> {
                        stopdestinationAck = true
                    }
                    else -> {
                        Log.e(TAG, "Unknown command in payload: 0x${command.toString(16)}")
                    }
                }
            }
        } else {
            Log.e(TAG, "Invalid response received or no payloads found")
        }
    }

    private fun sendLaserCommand(command: Int, parameters: ByteArray) {
        if (clientLaser == null) {
            return
        }

        val frame = laserProtocolHandler.frameData(command, parameters)
        if (frame.isEmpty()) {
            return
        }

        try {
//            val frameStr = frame.joinToString("") { String.format("%02X", it) }
            clientLaser?.sendHex(frame)
        } catch (e: Exception) {
            Log.e(TAG, "发送命令失败: ${e.message}")
        }
    }
    private fun onDisconnect(){
        clientOmni?.close()
        clientLaser?.close()

        connectStatus = false
        isOmniRunning = false
        isLaserRunning = false

        clientOmni = null
        clientLaser = null

    }

    // 扩展 ByteArray，以便进行 hex 打印
    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02X".format(it) }
    }

    private fun parseByteArrayToFloat(byteArray: ByteArray): Float {
        val buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
        val floatValue = buffer.float

        return ((floatValue * 10000).toInt() / 10000.0f).toFloat()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        onDisconnect()
        // 3. 清理 Messenger 和 Handler
        activityMessenger = null
        serviceMessenger = null

        super.onDestroy()
    }

    companion object {
        const val TAG = "SocketClientService"
    }

}