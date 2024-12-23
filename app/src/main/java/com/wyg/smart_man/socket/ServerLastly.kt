package com.wyg.smart_man.socket

import android.os.Handler
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * 服务端实现
 *
 * @author wangyunguang
 */
class ServerLastly(private val handler: Handler) : Runnable {
    private var printWriter: PrintWriter? = null
    private var socketList = ArrayList<Socket>()

    override fun run() {
        Log.i(TAG, "=======打开服务=========")

        val ss = ServerSocket(8888)
        while (true) {
            val s = ss.accept()
            printWriter = PrintWriter(s.getOutputStream())
            socketList.add(s)

            Log.i(TAG, "======客户端连接成功=========")

            Thread(ServerThread(s)).start()

            val inetAddress: InetAddress = s.getInetAddress()
            val ip: String = inetAddress.getHostAddress()
            Log.i(TAG, "客户端ID为:"+ip)

            val msg = handler.obtainMessage()
            msg.obj = ip
            msg.arg1 = SERVER_ARG2
            handler.sendMessage(msg)
        }
    }

    inner class ServerThread(private var s:Socket): Runnable
    {
        private var bufferedReader: BufferedReader? = null
        init {
            bufferedReader = BufferedReader(InputStreamReader(s.getInputStream(),"utf-8"))
        }
        override fun run() {
            var content: String?= readFromClient()
            while (content != null)
            {
                content =readFromClient()
                Log.i(TAG, "服务端接到的数据为：$content")
                //把数据带回activity显示
                val msg = handler.obtainMessage()
                msg.obj = content
                msg.arg1 = SERVER_ARG1
                handler.sendMessage(msg)
            }
        }

        private fun readFromClient():String?
        {
            try {
                return bufferedReader?.readLine()
            }
            catch (e:IOException)
            {
                e.printStackTrace()
                socketList.remove(s)
                s.close()
                Log.i(TAG, "客户端异常关闭")
            }
            return null
        }

        fun close()
        {
            try {
                if (bufferedReader != null) {
                    bufferedReader?.close()
                }
            } catch (e: IOException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }
        }
    }
    //发数据
    fun send(data: String) {
        Log.i(TAG, "服务端发送：$data")
        if (printWriter != null) {
            printWriter!!.println(data)
            printWriter!!.flush()
        }
    }
    fun close() {
        try {
            if (printWriter != null) {
                printWriter!!.close()
            }
            var i = 0
            for(i in socketList)
            {
                i.close()
                ServerThread(i).close()
            }
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "tcp_server"
        const val SERVER_ARG1 = 0x11
        const val SERVER_ARG2 = 0x12
    }
}