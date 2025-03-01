package com.wyg.smart_man.activity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import butterknife.ButterKnife
import com.google.gson.JsonObject
import com.wyg.smart_man.R
import com.wyg.smart_man.databinding.ActivityControlBinding
import com.wyg.smart_man.model.ApiResponse
import com.wyg.smart_man.service.RetrofitClient
import com.wyg.smart_man.service.SocketClientService
import com.wyg.smart_man.ui.GetViewModel
import com.wyg.smart_man.utils.DockingPoint
import com.wyg.smart_man.utils.MsgConstants
import com.wyg.smart_man.utils.ParamsConstants
import com.wyg.smart_man.utils.UiConstants
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.atan2

class ControlActivity : AppCompatActivity() {

    private lateinit var binding: ActivityControlBinding
    private var isServiceBound = false

    private var imageUporDown: Boolean = false

    private var dockingPoints = mutableListOf<DockingPoint>() // 存储停靠点的列表
    private lateinit var dockingPointsFile: File // 声明文件变量
    private var dockingPointCounter = 1 // 初始化停靠点计数器
    private var selectedPoint: DockingPoint? = null

    private var serviceMessenger: Messenger? = null
    private var isAutoMode = true

    private var xCoordinate: Float = 0.0f
    private var yCoordinate: Float = 0.0f
    private var theta: Float = 0.0f
    private var xVelocity: Float = 0.0f
    private var navStatus: Int = 1
    private var isConnected: Boolean = false

    private val viewModel: GetViewModel by viewModels()

    private val serviceConnect: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceMessenger = Messenger(service)
            sendMessageToService(mapOf("serviceConnect" to "success"))
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // 处理服务断开连接的逻辑
            isServiceBound = false
        }
    }

    // 创建 Handler 来处理来自客户端的消息
    private val handler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            if (msg.what == MsgConstants.MSG_SERVICE) {
                parseMessage(msg)
            } else {
                super.handleMessage(msg)
            }
        }
    }

    private fun sendMessageToService(params: Map<String, Any>) {
        val msg = Message.obtain(null, MsgConstants.MSG_ACTIVITY)
        msg.replyTo = Messenger(handler)
        msg.data = Bundle().apply {
            for ((key, value) in params) {
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Float -> putFloat(key, value)
                    is Double -> putDouble(key, value)
                    is Long -> putLong(key, value)
                    is Byte -> putByte(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Char -> putChar(key, value)
                    is Bundle -> putBundle(key, value)
                    else -> throw IllegalArgumentException("Unsupported type: ${value.javaClass.name}")
                }
            }
        }
        if (serviceMessenger != null) {
            try {
                serviceMessenger?.send(msg)
                Log.d(TAG, "Message sent successfully: $params")
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        } else {
            Log.e(TAG, "Service messenger is not initialized.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityControlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dockingPointsFile = File(filesDir, "docking_points.txt")
        ButterKnife.bind(this)

        setupUI()
        loadDockingPoints()

        val intent = Intent(this, SocketClientService::class.java)
        bindService(intent, serviceConnect, Context.BIND_AUTO_CREATE)
    }

    private fun setupUI() {
        binding.connect.setOnClickListener(ConnectOnClickListener())
        binding.disconnect.setOnClickListener { handleDisconnect() }
        binding.backimage.setOnTouchListener(ImageTouchListener())
        binding.radioselect.setOnCheckedChangeListener(RadioGroupChangeListener())
        binding.execButton.setOnClickListener(ExecButtonOnClickListener())
        binding.recordButton.setOnClickListener(RecordButtonOnClickListener())
        binding.clearRecordButton.setOnClickListener(ClearRecordButtonOnClickListener())
//        binding.emergencyStop.setOnClickListener { sendMessageToService(mapOf(UiConstants.EMERGENCYSTOP to isAutoMode)) }

        setupEmergencyStopButton()
        setupWaveButtonListeners()
        observeViewModel()

        binding.spinner1.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position >= 0 && position < dockingPoints.size) { // 确保位置合法
                    selectedPoint = dockingPoints.getOrNull(position)!!
                }
                if (selectedPoint != null) {
                    showToast("选中的停靠点: ${selectedPoint!!.name}, 坐标: (${selectedPoint!!.x}, ${selectedPoint!!.y}), 角度: ${Math.toDegrees(
                        selectedPoint!!.theta.toDouble())}°")
                } else {
                    showToast("未选择任何停靠点")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // 可以在这里做一些处理，比如清空显示或者提示用户
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupEmergencyStopButton() {
        binding.emergencyStop.setOnTouchListener { _, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.emergencyStop.setBackgroundResource(R.mipmap.ic_stop_down)
                    true // 事件已处理
                }
                MotionEvent.ACTION_UP -> {
                    binding.emergencyStop.setBackgroundResource(R.mipmap.ic_stop_normal)
                    sendMessageToService(mapOf(UiConstants.EMERGENCYSTOP to isAutoMode))
                    true // 事件已处理
                }
                else -> false // 其他事件未处理
            }
        }
    }
    private fun setupWaveButtonListeners() {
        binding.waveButton.setOnClickListener { handleWaveButtonClick(0, 0) }
        binding.comeButton.setOnClickListener { handleWaveButtonClick(1, 0) }
        binding.lookButton.setOnClickListener { handleWaveButtonClick(2, 0) }
        binding.handshakeButton.setOnClickListener { handleWaveButtonClick(3, 0) }
        binding.handdownButton.setOnClickListener { handleWaveButtonClick(4, 0) }
        binding.selfButton.setOnClickListener { handleWaveButtonClick(5, 0) }
        binding.showButton.setOnClickListener { handleWaveButtonClick(6, 0) }
        binding.introButton.setOnClickListener { handleWaveButtonClick(7, 0) }

        binding.requestButton.setOnClickListener { handleWaveButtonClick(0, 1) }

        binding.speakButton.setOnClickListener { handleWaveButtonClick(0, 2) }
        binding.listenButton.setOnClickListener { handleWaveButtonClick(1, 2) }
        binding.pauseButton.setOnClickListener { handleWaveButtonClick(2, 2) }

        binding.waveButton.isEnabled = false
        binding.comeButton.isEnabled = false
        binding.lookButton.isEnabled = false
        binding.handshakeButton.isEnabled = false
        binding.handdownButton.isEnabled = false
        binding.speakButton.isEnabled = false
        binding.listenButton.isEnabled = false
        binding.pauseButton.isEnabled = false
        binding.requestButton.isEnabled = true
        binding.selfButton.isEnabled = false
        binding.showButton.isEnabled = false
        binding.introButton.isEnabled = false
    }

    private fun isValidIpAddress(ipAddress: String): Boolean {
        val ipPattern = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
        return ipPattern.matches(ipAddress)
    }

    private fun isValidPort(port: String): Boolean {
        return port.toIntOrNull() in 1..65535
    }

    private fun handleWaveButtonClick(typeValue: Int, modeValue: Int) {
        var webAddress = binding.webIp.text.toString().trim()
        if (webAddress.isEmpty()) {
            webAddress = binding.webIp.hint.toString().trim()
            showToast("使用默认参数连接")
        }

        if (webAddress.isNotBlank() && isValidIpAddress(webAddress)) {
            val webPort = when (modeValue) {
                0, 1 -> ParamsConstants.PORT_HAND.toString()
                2 -> ParamsConstants.PORT_SPEAK.toString()
                else -> throw IllegalArgumentException("Invalid modeValue: $modeValue")
            }

            if (isValidPort(webPort)) {
                fetchDataWithBaseUrl(webAddress, webPort) {
                    when (modeValue) {
                        0 -> viewModel.fetchGestureData(typeValue)
                        1 -> viewModel.fetchStatus()
                        2 -> viewModel.fetchWebInfo(typeValue)
                    }
                    Log.d("handleWaveButtonClick", "baseport ${webPort}")
                }
            } else {
                showToast("端口号无效，请输入正确的端口号")
            }
        } else {
            showToast("IP地址无效，请输入正确的IP地址")
        }
    }

    private fun fetchDataWithBaseUrl(webAddress: String, webPort: String, fetchAction: () -> Unit) {
        try {
            RetrofitClient.setBaseUrl("http://$webAddress:$webPort/")
            Log.d("fetchDataWithBaseUrl", "Base URL set to: http://$webAddress:$webPort/")

//            val retrofit = RetrofitClient.getRetrofitInstance()
//            Log.d("fetchDataWithBaseUrl", "Current Retrofit baseUrl: ${retrofit.baseUrl()}")

            fetchAction()
        } catch (e: Exception) {
            showToast("设置 URL 时发生错误: ${e.message}")
            Log.e("URLSettingError", "Error setting URL", e)
        }
    }

    private fun observeViewModel() {
        viewModel.data.observe(this, Observer { apiResponse ->
            try {
                when (apiResponse) {
                    is ApiResponse.Success -> handleApiResponseSuccess(apiResponse)
                    is ApiResponse.Error -> handleApiResponseError(apiResponse)
                    null -> handleApiResponseNull()
                }
            } catch (e: Exception) {
                showToast("解析数据时发生错误: ${e.message}")
                Log.e("ApiResponseError", "Error parsing API response", e)
            }
        })
    }

    private fun handleApiResponseSuccess(apiResponse: ApiResponse.Success) {
        when (apiResponse.source) {
            "fetchStatus" -> handleFetchStatusResponse(apiResponse.data)
            "fetchGestureData" -> handleFetchGestureDataResponse(apiResponse.data)
            "fetchWebInfo" -> handleFetchWebInfoResponse(apiResponse.data)
        }
    }

    private fun handleFetchStatusResponse(data: JsonObject) {
        val step = data.getFieldAsInt("step")
        if (step == 1) {
            enableButtons()
            showToast("已启动，可进行其他操作")
        } else {
            showToast("启动中，请稍后")
        }
    }

    private fun handleFetchGestureDataResponse(data: JsonObject) {
        val action = data.getFieldAsString("action")
        if (action != null) {
            showToast(action)
        } else {
            showToast("无效的响应数据")
        }
    }

    private fun handleFetchWebInfoResponse(data: JsonObject) {
        val message = data.getFieldAsString("message")
        if (message != null) {
            showToast(message)
        } else {
            showToast("无效的响应数据")
        }
    }

    private fun handleApiResponseError(apiResponse: ApiResponse.Error) {
        showToast("An error occurred: ${apiResponse.message}")
    }

    private fun handleApiResponseNull() {
        showToast("No data received")
    }


    private fun JsonObject.getFieldAsString(fieldName: String): String? {
        return if (has(fieldName) && get(fieldName).isJsonPrimitive) {
            get(fieldName).asString
        } else {
            null
        }
    }

    private fun JsonObject.getFieldAsInt(fieldName: String): Int? {
        return if (has(fieldName) && get(fieldName).isJsonPrimitive) {
            get(fieldName).asInt
        } else {
            null
        }
    }

    private fun enableButtons() {
        binding.waveButton.isEnabled = true
        binding.comeButton.isEnabled = true
        binding.lookButton.isEnabled = true
        binding.handshakeButton.isEnabled = true
        binding.handdownButton.isEnabled = true
        binding.speakButton.isEnabled = true
        binding.listenButton.isEnabled = true
        binding.pauseButton.isEnabled = true
        binding.requestButton.isEnabled = true
        binding.selfButton.isEnabled = true
        binding.showButton.isEnabled = true
        binding.introButton.isEnabled = true
    }

    private fun handleDisconnect() {
        sendMessageToService(mapOf(UiConstants.OMNIDISCONNECT to "ok"))
        binding.connect.text = "连 接"
        binding.connect.isEnabled = true
    }

    private fun loadDockingPoints() {
        try {
            // 检查文件是否存在且长度不为零
            if (!dockingPointsFile.exists() || dockingPointsFile.length() == 0L) {
                Log.w(TAG, "停靠点文件不存在或为空")
                updateSpinner()
                return
            }

            dockingPoints.clear() // 清空列表
            dockingPointsFile.forEachLine { line -> parseDockingPoint(line) }

            // 设置默认选中的停靠点
            selectedPoint = if (dockingPoints.isNotEmpty()) {
                dockingPoints[0] // 默认选择第一个停靠点
            } else
                null
            // 更新 Spinner
            updateSpinner()
        } catch (e: IOException) {
            Log.e(TAG, "加载停靠点失败: ${e.message}")
            showToast("加载停靠点失败，请稍后重试")
        }
    }

    private fun parseDockingPoint(line: String) {
        val parts = line.split(",").map { it.trim() }
        Log.d(TAG, "读取到的行: \"$line\", 分割结果: $parts")
        try {
            if (parts.size == 4) {
                val name = parts[0]
                val x = parts[1].toFloat()
                val y = parts[2].toFloat()
                val theta = parts[3].toFloat()
                dockingPoints.add(DockingPoint(name, x, y, theta)) // 添加到列表
            } else {
                Log.w(TAG, "停靠点格式错误: $line")
            }
        } catch (e: NumberFormatException) {
            Log.e(TAG, "解析停靠点失败 (格式错误): $line - ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "解析停靠点失败: ${e.message}")
        }
    }

    private fun updateSpinner() {
        val items =
            if (dockingPoints.isEmpty()) listOf("无停靠点") else dockingPoints.map { it.name }
        val adapter = CustomArrayAdapter(
            this,
            items.toMutableList(),
            { itemToDelete -> showDeleteConfirmationDialog(itemToDelete) }) { position, updatedName ->
            // 更新停靠点名称
            val dockingPoint = dockingPoints[position].copy(name = updatedName)
            dockingPoints[position] = dockingPoint
            updateDockingPointsFile()
        }
        binding.spinner1.adapter = adapter
        binding.spinner1.isEnabled = true // 启用 spinner

    }

    private fun updateDockingPointsFile() {
        val content =
            dockingPoints.joinToString("\n") { "${it.name},${it.x},${it.y},${it.theta}" } + "\n"
        dockingPointsFile.writeText(content) // 使用 writeText 覆盖文件内容
    }

    private fun showDeleteConfirmationDialog(item: String) {
        AlertDialog.Builder(this@ControlActivity)
            .setTitle("确认删除")
            .setMessage("您确定要删除 '$item' 吗？")
            .setPositiveButton("确定") { _, _ -> deleteDockingPoint(item) }
            .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun deleteDockingPoint(item: String) {
        dockingPoints.find { it.name == item }?.let {
            dockingPoints.remove(it) // 从内存中删除
            updateDockingPointsFile() // 更新文件
            updateSpinner() // 更新 Spinner 显示
        }
    }

    private fun showSaveDockingPointDialog(dockingPoint: DockingPoint) {
        val message = "停靠点信息:\n坐标: (${dockingPoint.x}, ${dockingPoint.y})\n角度: ${
            "%.4f".format(Math.toDegrees(dockingPoint.theta.toDouble()))
        }°"

        // 创建自定义布局
        val dialogView = layoutInflater.inflate(R.layout.docking_point_dialog, null)
        val editTextDockingPointName: EditText =
            dialogView.findViewById(R.id.editTextDockingPointName)

        // 默认填充当前停靠点名称
        editTextDockingPointName.setText(dockingPoint.name)

        // 创建对话框
        AlertDialog.Builder(this@ControlActivity)
            .setTitle("确认保存停靠点")
            .setMessage(message)
            .setView(dialogView) // 设置自定义布局
            .setPositiveButton("确定") { _, _ ->
                val newName = editTextDockingPointName.text.toString().trim() // 获取用户输入的新名称
                if (newName.isNotEmpty()) {
                    // 更新停靠点名称
                    dockingPoint.name = newName
                    saveDockingPoint(dockingPoint) // 保存停靠点
                    showToast("停靠点已保存")
                } else {
                    showToast("名称不能为空")
                }
            }
            .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() } // 仅关闭对话框
            .create()
            .show()
    }

    private fun saveDockingPoint(dockingPoint: DockingPoint) {
        try {
            dockingPoints.add(dockingPoint) // 添加到列表
            dockingPointsFile.appendText("${dockingPoint.name},${dockingPoint.x},${dockingPoint.y},${dockingPoint.theta}\n") // 保存到文件

            // 更新 Spinner
            updateSpinner()
        } catch (e: IOException) {
            Log.e(TAG, "保存停靠点失败: ${e.message}")
            showToast("保存停靠点失败，请稍后重试")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("MissingPermission")
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false

        return with(networkCapabilities) {
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }
    }

    private fun parseMessage(msg: Message) {
        if (msg.what == MsgConstants.MSG_SERVICE) {
            val bundle = msg.data
            bundle?.let { parseBundleData(it) }
        } else {
            Log.d(TAG, "Unknown message type: ${msg.what}")
        }
    }

    private fun parseBundleData(bundle: Bundle) {
        synchronized(dockingPoints) {
            for (key in bundle.keySet()) {
                when (key) {
                    UiConstants.OMNICONNECT -> handleOmniConnectResponse(bundle.getString(key))
                    UiConstants.LASERCONNECT -> handleLaserConnectResponse(bundle.getString(key))
                    UiConstants.ALARMINFO -> handleAlarmInfo(bundle.getString(key))
                    UiConstants.BASESOC -> binding.baseSoc.text = bundle.getString(key)
                    UiConstants.XCOORDINATE -> xCoordinate = bundle.getFloat(key)
                    UiConstants.YCOORDINATE -> yCoordinate = bundle.getFloat(key)
                    UiConstants.THETA -> theta = bundle.getFloat(key)
                    UiConstants.XVELOCITY -> binding.speed.text = String.format("%.2f m/s", bundle.getFloat(key))
                    UiConstants.NAVSTATUS -> { navStatus = bundle.getInt(key); if(navStatus == 0) binding.navStatus.text = "正 常" else binding.navStatus.text = "异 常"}
                }
            }
        }
    }

    private fun handleOmniConnectResponse(response: String?) {
        when (response) {
            "ok" -> {
                binding.connect.isEnabled = false
                binding.connect.text = "已连接"
                isConnected = true
                showToast("客户端连接底盘成功")
            }

            "fail" -> {
                binding.connect.isEnabled = true
                binding.connect.text = "连 接"
                isConnected = false
                showToast("客户端连接底盘失败，请检查参数")
            }

            "disconnected" -> {
                binding.connect.text = "连 接"
                binding.connect.isEnabled = true
                isConnected = false
                showToast("客户端连接底盘断开")
            }
        }
    }

    private fun handleLaserConnectResponse(response: String?) {
        when (response) {
            "ok" -> {
                showToast("客户端连接导航成功")
            }

            "fail" -> {
                showToast("客户端连接导航失败，请检查参数")
            }

            "disconnect" -> {
                showToast("客户端连接导航断开")
            }
        }
    }

    private fun handleAlarmInfo(value: String?) {
        binding.alarmInfo.text = if (value?.toInt() == 0) "无故障" else "驱动故障"
    }

    inner class ConnectOnClickListener : View.OnClickListener {
        @RequiresApi(Build.VERSION_CODES.M)
        override fun onClick(v: View?) {
            if (!isNetworkAvailable()) {
                showToast("请连接到网络")
                return
            }
            var ipOmni = binding.underIp.text.toString().trim()
            if (ipOmni.isEmpty()) {
                ipOmni = binding.underIp.hint.toString().trim()
                showToast("使用默认参数连接")
            }
            sendMessageToService(
                mapOf(
                    UiConstants.OMNICONNECT to ipOmni
                )
            )
        }
    }

    inner class ImageTouchListener : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            event ?: return false

            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    val (centerX, centerY) = getImageViewCenterCoordinates(binding.backimage)
                    val touchX = event.x
                    val touchY = event.y
                    val angleTan2 = Math.toDegrees(
                        atan2(
                            abs(touchX - centerX),
                            abs(touchY - centerY)
                        ).toDouble()
                    )

                    binding.upimage.rotation = when {
                        touchX >= centerX && touchY >= centerY -> (270.0 - angleTan2).toFloat()
                        touchX >= centerX -> (90.0 + angleTan2).toFloat()
                        touchY >= centerY -> (270.0 + angleTan2).toFloat()
                        else -> (90.0 - angleTan2).toFloat()
                    }
                    imageUporDown = true
                    binding.upimage.visibility = View.VISIBLE
                }

                MotionEvent.ACTION_UP -> {
                    imageUporDown = false
                    binding.upimage.rotation = 90.0F
                    binding.upimage.visibility = View.INVISIBLE
                }
            }
            if ((!isAutoMode) && (isConnected))
                sendManualCommand()
            return true
        }
    }

    inner class ExecButtonOnClickListener : View.OnClickListener {
        @RequiresApi(Build.VERSION_CODES.M)
        override fun onClick(v: View?) {
            try {
                if (selectedPoint == null) {
                    showToast("未选择任何停靠点")
                }
                else
                {
                    // 执行相关的导航任务
                    val msg = mapOf(
                        UiConstants.LASERPARAMS to selectedPoint!!.name,
                        UiConstants.XCOORDINATE to selectedPoint!!.x,
                        UiConstants.YCOORDINATE to selectedPoint!!.y,
                        UiConstants.THETA to selectedPoint!!.theta
                    )
                    if (isConnected) {
                        sendMessageToService(msg)
                        showToast("开始执行自动导航任务")
                    } else {
                        showToast("请连接机器人后重试")
                    }
                }
            }catch (e: Exception) {
                // 记录异常并显示友好提示
                Log.e(TAG, "导航任务执行失败: ${e.message}")
                showToast("执行导航任务时发生错误，请重试。")
            }
        }
    }

    inner class RecordButtonOnClickListener : View.OnClickListener {
        @RequiresApi(Build.VERSION_CODES.M)
        override fun onClick(v: View?) {

            if (!isConnected) {
                showToast("请连接机器人后重试")
                return
            }

            if(navStatus != 0){
                showToast("定位状态异常，请检查机器人定位状态,status=$navStatus")
                return
            }

            // 记录停靠点的逻辑
            // 假设根据用户输入创建停靠点
            val dockingPointName = "停靠点 $dockingPointCounter"

            // 输入验证
            if (xCoordinate.isNaN() || yCoordinate.isNaN() || theta.isNaN()) {
                showToast("坐标输入无效，请检查输入")
                return
            }

            val dockingPoint = DockingPoint(dockingPointName, xCoordinate, yCoordinate, theta)

            // 显示对话框
            showSaveDockingPointDialog(dockingPoint)

            dockingPointCounter++
        }
    }

    inner class ClearRecordButtonOnClickListener : View.OnClickListener {
        @RequiresApi(Build.VERSION_CODES.M)
        override fun onClick(v: View?) {
            // 清除所有停靠点
            if (dockingPointsFile.exists()) {
                // 提示用户确认删除
                AlertDialog.Builder(this@ControlActivity)
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
                        dockingPointCounter = 1
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

    inner class RadioGroupChangeListener : RadioGroup.OnCheckedChangeListener {
        override fun onCheckedChanged(group: RadioGroup?, checkedId: Int) {
            if(isConnected){
                // 处理模式选择变化
                when (checkedId) {
                    binding.radioButtonAuto.id -> {
                        if (!isAutoMode) {
                            isAutoMode = true
                            val msg = mapOf(
                                UiConstants.AUTOCONTROL to "auto"
                            )
                            sendMessageToService(msg)
                            showToast("选择了自动模式")
                        }
                    }

                    binding.radioButtonManual.id -> {
                        if (isAutoMode) {
                            isAutoMode = false
                            val msg = mapOf(
                                UiConstants.MANUALCONTROL to "manual"
                            )
                            sendMessageToService(msg)
                            showToast("选择了手动模式")
                        }
                    }
                }
            }
            else{
                showToast("请连接机器人后重试")
            }
        }
    }

    companion object {
        const val TAG = "ControlActivity"
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

    private fun sendManualCommand() {
        var vx: Float = 0.0F
        var vy: Float = 0.0F
        var omega: Float = 0.0F

        if (imageUporDown) {
            when (binding.upimage.rotation) {
                in 0f..90f -> {
                    vx = 0.4F
                    omega = ((90 - binding.upimage.rotation) / 200)
                }
//                in 85f..90f -> {
//                    vx = 0.4F
//                    omega = 0.0F
//                }
//                in 90f..95f -> {
//                    vx = 0.4F
//                    omega = 0.0F
//                }
                in 90f..180f -> {
                    vx = 0.4F
                    omega = ((90 - binding.upimage.rotation) / 200)
                }
                in 260f..280f -> {
                    vx = -0.4F
                    omega = 0.0F
                }
                in 180f..260f->{
                    omega = -0.25f
                }
                else -> {
                    omega = 0.25f
//                    omega = -((270 - binding.upimage.rotation) / 300)
                }
            }
        } else {
            vx = 0.0F
            vy = 0.0F
            omega = 0.0F
        }
        val msg = mapOf(
            UiConstants.OMNIPARAMS to "omni",
            UiConstants.XSPEED to vx,
            UiConstants.YSPEED to vy,
            UiConstants.OMEGA to omega
        )
        sendMessageToService(msg)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 解绑服务
        if (isServiceBound) {
            unbindService(serviceConnect)
            isServiceBound = false
        }
    }
}
