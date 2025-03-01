package com.wyg.smart_man.socket

import android.os.Handler
import android.util.Log
import android.util.Pair
import com.wyg.smart_man.utils.MsgConstants
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.experimental.and

/**
 * 客户端
 *
 * @param handler 用于与 UI 线程通信的处理器
 * @param server_ip 服务器 IP 地址
 * @param server_port 服务器端口
 */
class ClientLastly(private val clientId: String, private var handler: Handler, private var ip: String, private var port: Int) : Runnable {
    private val timeout = 60000
    private var clientSocket: Socket? = null
    private var printWriter: PrintWriter? = null
    private var bufferedReader: BufferedReader? = null
    private var isReadRunning = false

    override fun run() {
        try {
            clientSocket = Socket(ip, port)
            clientSocket!!.soTimeout = timeout

            // 检查连接状态
            if (clientSocket!!.isConnected) {
                Log.i(TAG, "=======连接服务器成功=========")
                printWriter = PrintWriter(clientSocket!!.getOutputStream())

                val msg = handler.obtainMessage()
                msg.arg1 = MsgConstants.CLIENT_ARG
                msg.obj = Pair("ok", clientId)
                handler.sendMessage(msg)

                isReadRunning = true
                startReading()
//                startQueryBatteryInfo() // 启动保持活动线程
            } else {
                Log.e(TAG, "连接失败: 客户端未成功连接")
                isReadRunning = false
                // 创建一个信息，表示连接失败
                val errorMsg = handler.obtainMessage()
                errorMsg.arg1 = MsgConstants.CLIENT_ARG
                errorMsg.obj = Pair("fail", clientId)
                handler.sendMessage(errorMsg)
                if(clientSocket != null)
                    close() // 关闭连接
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, "连接服务器失败: ${e.message}", e)
            isReadRunning = false
            // 创建一个信息，表示连接失败
            val errorMsg = handler.obtainMessage()
            errorMsg.arg1 = MsgConstants.CLIENT_ARG
            errorMsg.obj = Pair("fail", clientId)
            handler.sendMessage(errorMsg)
            if(clientSocket != null)
                close() // 关闭连接
        }
    }
    private fun bytesToHex(bytes: ByteArray): String {
        val hexString = StringBuilder()
        for (byte in bytes) {
            val hex = String.format("%02X", byte)
            hexString.append(hex)
        }
        return hexString.toString()
    }

    private fun startReading() {
        Thread {
            try {
                val inputStream = clientSocket!!.getInputStream()
                bufferedReader = BufferedReader(InputStreamReader(inputStream))
                Log.d(TAG, "BufferedReader thread running")

                // 这里直接使用 InputStream 来处理字节流
                val byteArray = ByteArray(1024) // 假设一个缓冲区大小
                var bytesRead: Int

                while (isReadRunning) {
                    try {
                        bytesRead = inputStream.read(byteArray) // 读取字节
                        if (bytesRead == -1) {
                            // 连接已关闭或没有数据可读
                            Log.d(TAG, "No more data: socket closed or end of stream.")
                            onDisconnected()
                            break
                        }
                        // 将读取的字节转换为十六进制字符串
                        val hexData = bytesToHex(byteArray.copyOf(bytesRead))
//                        Log.d(TAG, "客户端接收到的数据为：$hexData")

                        val msg = handler.obtainMessage()
                        msg.arg1 = MsgConstants.CLIENT_INFO
                        msg.obj = Pair(hexData, clientId)
                        handler.sendMessage(msg)

                    } catch (e: SocketTimeoutException) {
                        Log.e(TAG, "Socket timeout: ${e.message}", e)
                        onDisconnected()
                        close() // 关闭连接
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "IOException during reading from server: ${e.message}", e)
                onDisconnected()
            } finally {
                close() // 清理资源
            }
        }.start()
    }

    fun send(data: String) {
        Thread {
            if (clientSocket?.isConnected == true && printWriter != null) {
                try {
                    // 发送数据
                    printWriter!!.println(data)
                    printWriter!!.flush()
                    Log.d(TAG, "数据已发送: $data")
                } catch (e: IOException) {
                    Log.e(TAG, "发送数据时发生异常: ${e.message}", e)
                }
            } else {
                Log.e(TAG, "Socket未连接，无法发送数据")
            }
        }.start() // 在新线程中执行发送操作
    }

    private fun byteArrayToHexString(data: ByteArray): String {
        val stringBuilder = StringBuilder()
        for (byte in data) {
            stringBuilder.append(String.format("%02X", byte.and(0xFF.toByte())))
        }
        return stringBuilder.toString()
    }

    fun sendHex(data: ByteArray) {
//        Thread {
            if (clientSocket?.isConnected == true) {
                try {
                    // 直接获取 socket 输出流，并发送原始字节
                    val outputStream = clientSocket!!.getOutputStream()
                    outputStream.write(data)
                    outputStream.flush()

                    // 将字节数组转换为十六进制字符串用于日志
                    val hexString = byteArrayToHexString(data)
                    Log.d(TAG, "数据已发送: $hexString")
                } catch (e: IOException) {
                    Log.e(TAG, "发送数据时发生异常: ${e.message}", e)
                }
            } else {
                Log.e(TAG, "Socket未连接，无法发送数据")
            }
//        }.start() // 在新线程中执行发送操作
    }

    fun close() {
        try {
            isReadRunning = false
            printWriter?.close()
            bufferedReader?.close()
            clientSocket?.close()
            clientSocket = null
            Log.d(TAG, "Connection closed.")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

//    private fun startQueryBatteryInfo() {
//        Thread {
//            while (clientSocket?.isConnected == true) {
//                send("keep_alive") // 定期发送保持活动的命令
//                Thread.sleep(1000) // 每秒发送一次，可以调整这个间隔
//            }
//        }.start()
//    }

    private fun onDisconnected(){
        val msg = handler.obtainMessage()
        msg.arg1 = MsgConstants.CLIENT_ARG
        msg.obj = Pair("disconnected", clientId)
        handler.sendMessage(msg)
    }

    companion object {
        private const val TAG = "tcp_client"
    }
}
