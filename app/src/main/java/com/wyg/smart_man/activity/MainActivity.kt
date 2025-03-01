package com.wyg.smart_man.activity

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.wyg.smart_man.databinding.ActivityMainBinding
import com.wyg.smart_man.utils.CheckPermission

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var checkPermission: CheckPermission

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置旋转动画
        setupRotationAnimation()

        // 设置点击事件，启动 ControlActivity
        binding.login.setOnClickListener {
            startControlActivity()
        }

        // 初始化权限检查
        initializePermissionChecker()

    }

    @SuppressLint("ObjectAnimatorBinding")
    private fun setupRotationAnimation() {
        val rotateAnimator = ObjectAnimator.ofFloat(binding.logoRobot, "rotationY", 0f, 360f).apply {
            duration = 6000 // 动画持续时间（毫秒）
            repeatCount = ObjectAnimator.INFINITE // 无限重复
            interpolator = LinearInterpolator() // 使用线性插值器
        }
//        rotateAnimator.start() // 启动动画

    }

    private fun startControlActivity() {
        val intent = Intent(this, ControlActivity::class.java)
        startActivity(intent) // 启动新的 Activity
    }

    private fun initializePermissionChecker() {
        checkPermission = object : CheckPermission(this) {
            override fun permissionSuccess() {
                // 权限申请成功时的处理
                showToast("权限申请成功")
            }
        }

        // 请求权限
        requestPermissions()
    }

    private fun requestPermissions() {
        checkPermission.permission(CheckPermission.REQUEST_CODE_PERMISSION_LOCATION)
        checkPermission.permission(CheckPermission.REQUEST_CODE_PERMISSION_STORAGE)
        checkPermission.permission(CheckPermission.REQUEST_CODE_PERMISSION_NETWORK)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
