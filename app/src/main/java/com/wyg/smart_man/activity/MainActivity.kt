package com.wyg.smart_man.activity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager.Query
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import butterknife.ButterKnife
import com.wyg.smart_man.databinding.ActivityMainBinding
import com.wyg.smart_man.protocol.ProtocolHandler
import com.wyg.smart_man.socket.ClientLastly
import com.wyg.smart_man.utils.DockingPoint
import kotlinx.android.synthetic.main.activity_main.alarm_info
import kotlinx.android.synthetic.main.activity_main.base_soc
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.atan2

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var clientOmni: ClientLastly? = null
    private var clientLaser: ClientLastly? = null
    private var connectStatus = false
    private var upimage_upordown: Boolean = false
    private val commandSender: Handler = Handler(Looper.getMainLooper())
    private val mainRunnable = MainRunnable()

    private val queryBatteryInfoHandler: Handler = Handler(Looper.getMainLooper())
    private val queryBatteryInfoRunnable = QueryRunnable()

    private val protocolHandler = ProtocolHandler()
    private var isAutoMode = true
    private var isSending = false
    private var isScheduled = false
    private var dockingPoints = mutableListOf<DockingPoint>() // 存储停靠点的列表
    private lateinit var dockingPointsFile: File // 声明文件变量
    private var dockingPointCounter = 1 // 初始化停靠点计数器

    companion object {
        const val PORT_OMANI = 2017
        const val PORT_LASER = 9981
        const val COMMAND_CONTROL_SEND_INTERVAL = 100L
        const val COMMAND_QUERY_SEND_INTERVAL = 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dockingPointsFile = File(filesDir, "docking_points.txt")

        ButterKnife.bind(this)
        setupSpinner()
        binding.connect.setOnClickListener(ConnectOnClickListener())
        binding.backimage.setOnTouchListener(ImageTouchListener())
        binding.radioselect.setOnCheckedChangeListener(RadioGroupChangeListener())
        binding.recordButton.setOnClickListener(RecordButtonOnClickListener())
        binding.clearRecordButton.setOnClickListener(ClearRecordButtonOnClickListener())
    }

    private fun setupSpinner() {
        loadDockingPoints()

        // 判断 dockingPoints 是否为空
        val items = if (dockingPoints.isEmpty()) {
            listOf("无停靠点") // 默认提示
        } else {
            dockingPoints.map { it.name } // 提取停靠点名称
        }

        // 使用停靠点名称初始化适配器
        val adapter = CustomArrayAdapter(this, items)
        binding.spinner1.adapter = adapter

        // 设置触摸监听器
        binding.spinner1.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                adapter.setDropDownVisible(false)
            }
            false
        }

        // 设置选择监听器
        binding.spinner1.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                adapter.setSelectedPosition(position)
                adapter.setDropDownVisible(true)
                if (dockingPoints.isNotEmpty()) {
                    // 处理选中项
                    val selectedPoint = dockingPoints[position]
                    // 可以做更多的操作，比如显示选中点的信息
                } else {
                    // 处理无停靠点的情况
                    showToast("当前没有可用的停靠点")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun loadDockingPoints() {
        try {
            if (!dockingPointsFile.exists() || dockingPointsFile.length() == 0L) {
                return
            }

            dockingPoints.clear() // 清空列表
            dockingPointsFile.forEachLine { line ->
                val parts = line.split(",")
                // 使用 try-catch 捕获格式错误
                try {
                    if (parts.size == 4) {
                        val name = parts[0]
                        val x = parts[1].toFloat()
                        val y = parts[2].toFloat()
                        val theta = parts[3].toFloat()
                        val dockingPoint = DockingPoint(name, x, y, theta)
                        dockingPoints.add(dockingPoint) // 添加到列表
                    } else {
                        Log.w("MainActivity", "停靠点格式错误: $line")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "解析停靠点失败: ${e.message}")
                }
            }
            // 更新 Spinner
            updateSpinner()
        } catch (e: IOException) {
            Log.e("MainActivity", "加载停靠点失败: ${e.message}")
            showToast("加载停靠点失败，请稍后重试")
        }
    }

    private fun saveDockingPoint(dockingPoint: DockingPoint) {
        try {
            dockingPoints.add(dockingPoint) // 添加到列表
            dockingPointsFile.appendText("${dockingPoint.name},${dockingPoint.x},${dockingPoint.y},${dockingPoint.theta}\n") // 保存到文件

            // 更新 Spinner
            updateSpinner()
        } catch (e: IOException) {
            Log.e("MainActivity", "保存停靠点失败: ${e.message}")
            showToast("保存停靠点失败，请稍后重试")
        }
    }

    private fun updateSpinner() {
        // 判断 dockingPoints 是否为空
        val items = if (dockingPoints.isEmpty()) {
            listOf("无停靠点") // 默认提示
        } else {
            dockingPoints.map { it.name } // 提取停靠点名称
        }
        // 提取停靠点名称并更新适配器
        binding.spinner1.adapter = CustomArrayAdapter(this, items)
        binding.spinner1.isEnabled = true // 启用 spinner
    }

    private fun showSaveDockingPointDialog(dockingPoint: DockingPoint) {
        val message = "停靠点信息:\n名称: ${dockingPoint.name}\n坐标: (${dockingPoint.x}, ${dockingPoint.y})\n角度: ${dockingPoint.theta}°"

        AlertDialog.Builder(this)
            .setTitle("确认保存停靠点")
            .setMessage(message)
            .setPositiveButton("确定") { _, _ ->
                // 点击“确定”后保存停靠点
                saveDockingPoint(dockingPoint)
                showToast("停靠点已保存")
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss() // 仅关闭对话框
            }
            .create()
            .show()
    }

    inner class RecordButtonOnClickListener : View.OnClickListener {
        @RequiresApi(Build.VERSION_CODES.M)
        override fun onClick(v: View?) {
            // 假设根据用户输入创建停靠点
            val dockingPointName = "停靠点 $dockingPointCounter"
            val xCoordinate = 12.0f // 示例数据（应由用户输入）
            val yCoordinate = 34.0f // 示例数据（应由用户输入）
            val thetaAngle = 45.0f // 示例数据（应由用户输入）

            // 输入验证
            if (xCoordinate.isNaN() || yCoordinate.isNaN() || thetaAngle.isNaN()) {
                showToast("坐标输入无效，请检查输入")
                return
            }

            val dockingPoint = DockingPoint(dockingPointName, xCoordinate, yCoordinate, thetaAngle)

            // 显示对话框
            showSaveDockingPointDialog(dockingPoint)

            dockingPointCounter++
        }
    }

    inner class ClearRecordButtonOnClickListener : View.OnClickListener {
        @RequiresApi(Build.VERSION_CODES.M)
        override fun onClick(v: View?) {
            if (dockingPointsFile.exists()) {
                // 提示用户确认删除
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("确认清除")
                    .setMessage("您确定要清除所有停靠点吗？")
                    .setPositiveButton("确定") { _, _ ->
                        // 确认后删除文件
                        if (dockingPointsFile.delete()) {
                            dockingPoints.clear() // 清空停靠点列表
                            updateSpinner() // 更新 Spinner
                            showToast("所有停靠点已清除")
                        } else {
                            showToast("清除停靠点失败，请稍后重试")
                        }
                    }
                    .setNegativeButton("取消") { dialog, _ ->
                        dialog.dismiss() // 取消操作，关闭对话框
                    }
                    .create()
                    .show()
            } else {
                showToast("没有停靠点可清除")
            }
        }
    }

    inner class ConnectOnClickListener : View.OnClickListener {
        @RequiresApi(Build.VERSION_CODES.M)
        override fun onClick(v: View?) {
            if (!isNetworkAvailable()) {
                showToast("请连接到网络")
                return
            }

            // 清理任何之前的连接
            if (clientOmni != null) {
                showToast("已连接，请先断开")
                return // 如果当前已经建立连接，阻止重连
            }

            var ipOmni = binding.underIp.text.toString().trim()
            if (ipOmni.isEmpty()) {
                ipOmni = binding.underIp.hint.toString().trim()
                showToast("使用默认参数连接")
            }

            try {
                clientOmni = clientOmni ?: ClientLastly("client_omni", handler, ipOmni, PORT_OMANI).apply { Thread(this).start() }
//                clientLaser = clientLaser ?: ClientLastly("client_laser", handler, ipOmni, PORT_LASER).apply { Thread(this).start() }
                // 禁用连接按钮，避免重复点击
                binding.connect.isEnabled = false
            } catch (e: Exception) {
                Log.e("MainActivity", "连接失败: ${e.message}")
                showToast("连接失败，请检查参数或稍后重试")
                binding.connect.isEnabled = true // 恢复按钮状态
                clientOmni = null // 将 clientOmni 设置为 null 以允许重试
            }
        }
    }

    inner class RadioGroupChangeListener : RadioGroup.OnCheckedChangeListener {
        override fun onCheckedChanged(group: RadioGroup?, checkedId: Int) {
            when (checkedId) {
                binding.radioButtonAuto.id -> {
                    if (!isAutoMode) {
                        isAutoMode = true
                        Log.d("MainActivity", "切换到自动模式")
                        if (clientOmni != null) {
                            stopSending()
                        } else {
                            Log.w("MainActivity", "clientOmni 未初始化")
                        }
                        showToast("选择了自动模式")
                    }
                }
                binding.radioButtonManual.id -> {
                    if (isAutoMode) {
                        isAutoMode = false
                        Log.d("MainActivity", "切换到手动模式")
                        if (clientOmni != null) {
                            startSending()
                        } else {
                            Log.w("MainActivity", "clientOmni 未初始化")
                        }
                        showToast("选择了手动模式")
                    }
                }
            }
        }
    }

    private fun getImageViewCenterCoordinates(imageView: ImageView): Pair<Float, Float> {
        val imageViewLocation = IntArray(2)
        imageView.getLocationInWindow(imageViewLocation)

        val centerX = imageViewLocation[0] + imageView.width / 2f
        val centerY = imageViewLocation[1] + imageView.height / 2f

        val parent = imageView.parent as View
        val parentLocation = IntArray(2)
        parent.getLocationInWindow(parentLocation)

        val relativeCenterX = centerX - parentLocation[0]
        val relativeCenterY = centerY - parentLocation[1]

        return Pair(relativeCenterX, relativeCenterY)
    }

    inner class ImageTouchListener : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            event ?: return false

            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    val (centerX, centerY) = getImageViewCenterCoordinates(binding.backimage)
                    val touchX = event.x
                    val touchY = event.y
                    val angleTan2 = Math.toDegrees(atan2(abs(touchX - centerX), abs(touchY - centerY)).toDouble())

                    binding.upimage.rotation = when {
                        touchX >= centerX && touchY >= centerY -> (270.0 - angleTan2).toFloat()
                        touchX >= centerX -> (90.0 + angleTan2).toFloat()
                        touchY >= centerY -> (270.0 + angleTan2).toFloat()
                        else -> (90.0 - angleTan2).toFloat()
                    }
                    upimage_upordown = true
                    binding.upimage.visibility = View.VISIBLE
                }
                MotionEvent.ACTION_UP -> {
                    upimage_upordown = false
                    binding.upimage.rotation = 90.0F
                    binding.upimage.visibility = View.INVISIBLE
                }
            }
            return true
        }
    }

    private fun startSending() {
        if (!isSending) {
            if (clientOmni == null) {
                Log.e("MainActivity", "clientOmni 未初始化")
                showToast("网络未初始化，无法发送命令")
                return
            }
            try {
                sendCommand("8", byteArrayOf(0x01))
                commandSender.post(mainRunnable)
                isScheduled = true
                isSending = true
                Log.d("MainActivity", "开始发送命令和调度任务")
            } catch (e: NullPointerException) {
                Log.e("MainActivity", "空指针异常: ${e.message}")
                showToast("遇到空指针问题，请检查配置")
            } catch (e: IOException) {
                Log.e("MainActivity", "网络通信异常: ${e.message}")
                showToast("网络问题，请稍后重试")
            } catch (e: Exception) {
                Log.e("MainActivity", "启动发送任务时出现异常: ${e.message}")
                showToast("启动发送任务失败，请稍后重试")
            }
        } else {
            Log.w("MainActivity", "已经在发送状态中")
        }
    }

    private fun stopSending() {
        if (isScheduled) {
            try {
                sendCommand("8", byteArrayOf(0x00))
                commandSender.removeCallbacksAndMessages(null)
                isScheduled = false
                isSending = false
                Log.d("MainActivity", "停止发送命令和调度任务")
            } catch (e: Exception) {
                Log.e("MainActivity", "停止发送任务时出现异常: ${e.message}")
                showToast("停止发送任务失败，请稍后重试")
            }
        } else {
            Log.d("MainActivity", "没有调度任务需要停止")
        }
    }

    inner class MainRunnable : Runnable {
        override fun run() {
            if (!isSending) return

            // 网络请求处理
            thread {
                try {
                    sendManualCommand()
                } catch (e: IOException) {
                    Log.e("MainActivity", "发送手动命令失败: ${e.message}")
                }
            }

            commandSender.postDelayed(this, COMMAND_CONTROL_SEND_INTERVAL) // 每5秒发送一次
        }
    }

    inner class QueryRunnable : Runnable {
        override fun run() {
            Log.d("MainActivity", "SecondRunnable executed")
            // 网络请求处理
            if (clientOmni != null)
            {
                thread {
                    try {
                        sendQueryBatteryInfoCommand()
                    } catch (e: IOException) {
                        Log.e("MainActivity", "发送手动命令失败: ${e.message}")
                    }
                }
            }
            queryBatteryInfoHandler.postDelayed(this, COMMAND_QUERY_SEND_INTERVAL) // 每5秒发送一次
        }
    }

    private fun sendQueryBatteryInfoCommand()
    {
        val parameters = byteArrayOf()

        sendCommand("18", parameters)
    }

    private fun sendManualCommand() {
        var Vx = 0
        val Vy = 0
        var omega = 0

        if (upimage_upordown) {
            if (binding.upimage.rotation in 0f..180f) {
                Vx = 20
                omega = (90 - binding.upimage.rotation).toInt()
            } else {
                Vx = -20
                omega = ((270 - binding.upimage.rotation)/ 3).toInt()
            }
        }

        val parameters = ByteBuffer.allocate(12).apply {
            putInt(Vx)
            putInt(Vy)
            putInt(omega)
        }.array()

        sendCommand("17", parameters)
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val (content, clientName) = msg.obj as Pair<String, String>
//            Log.d("MainActivity", "Received data from $clientName: $content")

            when (clientName) {
                "client_omni" -> handleClientOmniData(msg, content)
                "client_laser" -> { /* 处理 ClientLaser 的数据 */ }
            }
        }
    }

    private fun handleClientOmniData(msg: Message, content: String) {
        when (msg.arg1) {
            ClientLastly.CLIENT_ARG -> handleClientArg(content)
            ClientLastly.CLIENT_INFO -> handleClientInfo(content)
            else -> Log.d("tcp_client", "handle error, unknown arg1=${msg.arg1}")
        }
    }

    private fun handleClientArg(content: String) {
        when (content) {
            "ok" -> {
                showToast("连接成功")
                binding.connect.isEnabled = false
                binding.connect.text = "已连接"
                connectStatus = true
                queryBatteryInfoHandler.post(queryBatteryInfoRunnable)
            }
            "fail" -> {
                showToast("连接失败,请检查参数")
                binding.connect.isEnabled = true
                connectStatus = false
                clientOmni = null
                clientLaser = null
            }
            "disconnected" ->{
                showToast("连接已断开")
                binding.connect.isEnabled = true
                binding.connect.text = "连接"
                connectStatus = false
                clientOmni = null
                clientLaser = null
            }
        }
    }

    private fun handleClientInfo(response: String) {
        Log.d("MainActivity", "Received response: $response") // 输出接收到的响应

        val payloads = protocolHandler.handleResponse(response)

        if (!payloads.isNullOrEmpty()) {
            Log.d("MainActivity", "Number of payloads: ${payloads.size}") // 输出有效 payload 数量

            for (payload in payloads) { // 遍历所有有效的负载
                // 检查 payload 的长度
                if (payload.size < 10) {
                    Log.e("MainActivity", "Payload size too short: ${payload.size}")
                    continue // 继续到下一个 payload
                }

                // 输出当前 payload 的字节内容
//                Log.d("MainActivity", "Processing payload: ${payload.joinToString(", ") { "0x${it.toString(16).padStart(2, '0')}" }}")
                Log.d("MainActivity", "Processing payload: ${payload.joinToString(", ") { "0x${(it.toInt() and 0xFF).toString(16).padStart(2, '0')}" }}")

                // 示例：使用payload进行数据提取和处理
                when (payload[7]) {
                    0x03.toByte() -> {
                        val faultStatus = (payload[8].toInt() and 0x01)
                        if (faultStatus == 0) {
                            alarm_info.text = "无故障"
                            Log.d("MainActivity", "Alarm Status: No Fault Detected")
                        } else {
                            alarm_info.text = "驱动器故障"
                            Log.d("MainActivity", "Alarm Status: Driver Fault Detected")
                        }
                    }
                    0x0a.toByte() -> {

                    }
                    0x12.toByte() -> {
                        val batSoc = (payload[8].toInt() and 0xFF) shl 24 or
                                (payload[9].toInt() and 0xFF) shl 16 or
                                (payload[10].toInt() and 0xFF) shl 8 or
                                (payload[11].toInt() and 0xFF)

                        val socValue = (batSoc / 100).toString()
                        base_soc.text = socValue

                        Log.d("MainActivity", "Battery SOC Value: $socValue")
                        Log.d("MainActivity", "Raw Battery SOC Bytes: " +
                                "0x${payload[8].toString(16).padStart(2, '0')} " +
                                "0x${payload[9].toString(16).padStart(2, '0')} " +
                                "0x${payload[10].toString(16).padStart(2, '0')} " +
                                "0x${payload[11].toString(16).padStart(2, '0')}")
                    }
                    else -> {
                        Log.e("MainActivity", "Unknown command in payload: 0x${payload[7].toString(16)}")
                    }
                }
            }
        } else {
            Log.e("MainActivity", "Invalid response received or no payloads found")
        }
    }

//    private fun handleClientInfo(response: String) {
//        val payload = protocolHandler.handleResponse(response)
//        if (payload != null) {
////            Log.d("MainActivity", "Payload: ${payload.toHex()}")
//            when(payload[7]){
//                0x03.toByte() ->{
//                    if((payload[8].toInt() and 0x01) == 0)
//                        alarm_info.text = "无故障"
//                    else
//                        alarm_info.text = "驱动器故障"
//                }
//            }
//        } else {
//            Log.e("MainActivity", "Invalid response received")
//        }
//    }

    private fun sendCommand(command: String, parameters: ByteArray) {
        if (clientOmni == null) {
            showToast("无法发送命令，网络连接未建立")
            return
        }

        val frame = protocolHandler.frameData(command, parameters)
        if (frame.isEmpty()) {
            showToast("命令打包失败，请检查参数")
            return
        }

        try {
//            val frameStr = frame.joinToString("") { String.format("%02X", it) }
            clientOmni?.sendHex(frame)
        } catch (e: Exception) {
            Log.e("MainActivity", "发送命令失败: ${e.message}")
            showToast("发送命令失败，请稍后重试")
        }
    }

    // 扩展 ByteArray，以便进行 hex 打印
    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02X".format(it) }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("MissingPermission")
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return with(networkCapabilities) {
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
