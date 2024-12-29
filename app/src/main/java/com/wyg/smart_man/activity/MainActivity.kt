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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import butterknife.ButterKnife
import com.wyg.smart_man.R
import com.wyg.smart_man.databinding.ActivityMainBinding
import com.wyg.smart_man.service.SocketClientService
import com.wyg.smart_man.utils.DockingPoint
import com.wyg.smart_man.utils.MsgConstants
import com.wyg.smart_man.utils.ParamsConstants
import com.wyg.smart_man.utils.UiConstants
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.atan2

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var imageUporDown: Boolean = false

    private var dockingPoints = mutableListOf<DockingPoint>() // 存储停靠点的列表
    private lateinit var dockingPointsFile: File // 声明文件变量
    private var dockingPointCounter = 1 // 初始化停靠点计数器

    private var serviceMessenger: Messenger? = null
    private var isAutoMode = true

    private val serviceConnect :ServiceConnection = object :ServiceConnection{
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceMessenger = Messenger(service)
            val msg = mapOf(
                "serviceConnect" to "sucess"
            )
            sendMessageToService(msg)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            TODO("Not yet implemented")
        }
    }

    // 创建 Handler 来处理来自客户端的消息
    private val handler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MsgConstants.MSG_SERVICE -> {
                    // 处理来自客户端的消息
                    parseMessage(msg)
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private fun sendMessageToService(params: Map<String, Any>) {
        // 发送消息到 Service
        val msg = Message.obtain(null, MsgConstants.MSG_ACTIVITY)
        msg.replyTo = Messenger(handler)

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
            serviceMessenger?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
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

        val intent = Intent(this,SocketClientService::class.java)
        bindService(intent,serviceConnect,Context.BIND_AUTO_CREATE)
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
            val msg = mapOf(
                "connect" to ipOmni,
                "port" to ParamsConstants.PORT_OMANI.toString()
            )
            sendMessageToService(msg)
        }
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
//        val adapter = CustomArrayAdapter(this, items)
//        val adapter = CustomArrayAdapter(this, items.toMutableList()) { itemToDelete ->
//            showDeleteConfirmationDialog(itemToDelete) // 请求删除
//        }
        val adapter = CustomArrayAdapter(this, items.toMutableList(), { itemToDelete ->
            showDeleteConfirmationDialog(itemToDelete) // 请求删除
        }) { position, updatedName ->
            // 处理更新停靠点的逻辑
            // 比如存储到文件或其他操作
            val dockingPoint = dockingPoints[position]
            dockingPoints[position] = dockingPoint.copy(name = updatedName)
            updateDockingPointsFile()
        }
        binding.spinner1.adapter = adapter

        // 设置触摸监听器
        binding.spinner1.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
//                adapter.setDropDownVisible(false)
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

    private fun updateDockingPointsFile() {
        // 更新文件以反映当前的 dockingPoints 状态
        val content = dockingPoints.joinToString("\n") { "${it.name},${it.x},${it.y},${it.theta}" }
        dockingPointsFile.writeText(content) // 使用 writeText 覆盖文件内容
    }

    private fun deleteDockingPoint(item: String) {
        // 查找对应的停靠点并删除
        val dockingPoint = dockingPoints.find { it.name == item }
        dockingPoint?.let {
            dockingPoints.remove(it) // 从内存中删除
            updateDockingPointsFile() // 更新文件
            updateSpinner() // 更新 Spinner 显示
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
//        binding.spinner1.adapter = CustomArrayAdapter(this, items)
//        binding.spinner1.adapter = CustomArrayAdapter(this, items.toMutableList()) { itemToDelete ->
//            showDeleteConfirmationDialog(itemToDelete) // 请求删除
//        }
        val adapter = CustomArrayAdapter(this, items.toMutableList(), { itemToDelete ->
            showDeleteConfirmationDialog(itemToDelete) // 请求删除
        }) { position, updatedName ->
            // 处理更新停靠点的逻辑
            // 比如存储到文件或其他操作
            val dockingPoint = dockingPoints[position]
            dockingPoints[position] = dockingPoint.copy(name = updatedName)
            updateDockingPointsFile()
        }
        binding.spinner1.adapter = adapter
        binding.spinner1.isEnabled = true // 启用 spinner
    }
    private fun showDeleteConfirmationDialog(item: String) {
        Log.d("MainActivity", "Showing delete confirmation dialog for item: $item")
        AlertDialog.Builder(this@MainActivity)
            .setTitle("确认删除")
            .setMessage("您确定要删除 '$item' 吗？")
            .setPositiveButton("确定") { _, _ ->
                deleteDockingPoint(item) // 用户确认后删除
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss() // 仅关闭对话框
            }
            .create()
            .show()
    }

//    private fun showSaveDockingPointDialog(dockingPoint: DockingPoint) {
//        val message = "停靠点信息:\n名称: ${dockingPoint.name}\n坐标: (${dockingPoint.x}, ${dockingPoint.y})\n角度: ${dockingPoint.theta}°"
//
//        AlertDialog.Builder(this@MainActivity)
//            .setTitle("确认保存停靠点")
//            .setMessage(message)
//            .setPositiveButton("确定") { _, _ ->
//                // 点击“确定”后保存停靠点
//                saveDockingPoint(dockingPoint)
//                showToast("停靠点已保存")
//            }
//            .setNegativeButton("取消") { dialog, _ ->
//                dialog.dismiss() // 仅关闭对话框
//            }
//            .create()
//            .show()
//    }
    private fun showSaveDockingPointDialog(dockingPoint: DockingPoint) {
        val message = "停靠点信息:\n坐标: (${dockingPoint.x}, ${dockingPoint.y})\n角度: ${dockingPoint.theta}°"

        // 创建自定义布局
        val dialogView = layoutInflater.inflate(R.layout.docking_point_dialog, null)
        val editTextDockingPointName: EditText = dialogView.findViewById(R.id.editTextDockingPointName)

        // 默认填充当前停靠点名称
        editTextDockingPointName.setText(dockingPoint.name)

        // 创建对话框
        AlertDialog.Builder(this@MainActivity)
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
            when (checkedId) {
                binding.radioButtonAuto.id -> {
                    if (!isAutoMode) {
                        isAutoMode = true
                        val msg = mapOf(
                            UiConstants.AUTO_CONTROL to "auto"
                        )
                        sendMessageToService(msg)
                        showToast("选择了自动模式")
                    }
                }
                binding.radioButtonManual.id -> {
                    if (isAutoMode) {
                        isAutoMode = false
                        val msg = mapOf(
                            UiConstants.MANUAL_CONTROL to "manual"
                        )
                        sendMessageToService(msg)
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
                    imageUporDown = true
                    binding.upimage.visibility = View.VISIBLE
                }
                MotionEvent.ACTION_UP -> {
                    imageUporDown = false
                    binding.upimage.rotation = 90.0F
                    binding.upimage.visibility = View.INVISIBLE
                }
            }
            if(!isAutoMode)
                sendManualCommand()
            return true
        }
    }

    private fun sendManualCommand() {
        var vx : Float = 0.0F
        var vy : Float = 0.0F
        var omega : Float = 0.0F

        if (imageUporDown) {
//            if (binding.upimage.rotation in 0f..180f) {
//                vx = 0.2F
//                omega = ((90 - binding.upimage.rotation)/100)
//            } else {
//                vx = (-0.2).toFloat()
//                omega = ((270 - binding.upimage.rotation)/ 300)
//            }
            if (binding.upimage.rotation in 0f..85f) {
                vx = 0.2F
                omega = ((90 - binding.upimage.rotation)/100)
            } else if(binding.upimage.rotation in 85f..90f){
                vx = 0.2F
            }else if(binding.upimage.rotation in 90f..95f){
                vx = 0.2F
            }else if(binding.upimage.rotation in 95f..180f){
                vx = 0.2F
                omega = ((90 - binding.upimage.rotation)/100)
            }else {
//                vx = (-0.2).toFloat()
                omega = ((270 - binding.upimage.rotation)/ 300)
            }
        }
        else
        {
            vx = 0.0F
            vy = 0.0F
            omega = 0.0F
        }
        val msg = mapOf(
            UiConstants.OMNI_PARAMS to "omni",
            "Vx" to vx,
            "Vy" to vy,
            "omega" to omega
        )
        sendMessageToService(msg)
    }

    private fun parseMessage(msg: Message) {
        when (msg.what) {
            MsgConstants.MSG_SERVICE -> {
                // 从消息中获取 Bundle
                val bundle = msg.data

                // 如果数据不为空，则解析每个键值对
                bundle?.let { // 如果需要循环处理，使用 allKeys 方法
                    for (key in it.keySet()) { // 遍历所有键
                        when (key) {
                            UiConstants.CONNECT -> {
                                when(it.getString(key)){
                                    "ok"->{
                                        binding.connect.isEnabled = false
                                        binding.connect.text="已连接"
                                        showToast("客户端连接成功")
                                    }
                                    "fail"->{
                                        binding.connect.isEnabled = true
                                        binding.connect.text="连接"
                                        showToast("客户端连接失败，请检查参数")
                                    }
                                    "disconnect"->{
                                        binding.connect.text="连接"
                                        binding.connect.isEnabled = true
                                        showToast("客户端连接断开")
                                    }
                                }
                            }
                            UiConstants.ALARM_INFO->{
                                val value = it.getString(key)?.toInt()
                                if(value == 0)
                                    binding.alarmInfo.text = "无故障"
                                else
                                    binding.alarmInfo.text = "驱动故障"
                            }
                            UiConstants.BASE_SOC->{
                                val value = it.getString(key)
                                binding.baseSoc.text = value
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

    companion object {
        const val TAG="MainActivity"
    }
}
