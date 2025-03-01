package com.wyg.smart_man.protocol

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LaserProtocolHandler {

    companion object {
        private const val TAG = "LaserProtocolHandler"

        private val FRAME_HEADER = byteArrayOf(0x68.toByte())
        private val FRAME_END = byteArrayOf(0x5F.toByte(), 0x5F.toByte(), 0x65.toByte(), 0x6E.toByte(), 0x64.toByte(), 0x5F.toByte(), 0x5F.toByte())

        private const val HEADER_SIZE = 1
        private const val LENGTH_SIZE = 8
        private const val END_SIZE = 7
    }

    private val circularBuffer = CircularBuffer(1024)

    // 组帧方法
    fun frameData(command: Int, parameters: ByteArray): ByteArray {
//        Log.d(TAG, "Framing data with command: $command and parameters: ${parameters.joinToString(", ")}")

        val startByte = FRAME_HEADER
        val endByte = FRAME_END
        val length = parameters.size + HEADER_SIZE + LENGTH_SIZE + END_SIZE // 计算长度

        val frame = ByteBuffer.allocate(HEADER_SIZE + LENGTH_SIZE + parameters.size + END_SIZE).apply {
            order(ByteOrder.LITTLE_ENDIAN) // 设置字节顺序为小端
            clear() // 确保缓冲区处于初始状态
            put(startByte)
            putInt(length)
            putInt(command)
            put(parameters)
            put(endByte)
        }.array()

//        Log.d(TAG, "Framed data: ${frame.joinToString(", ") { it.toString() }}")
        return frame
    }

    private fun getValidPayloadFromBuffer(): List<ByteArray> {
        val availableData = circularBuffer.readAvailableData()
        val validPayloads = mutableListOf<ByteArray>()

        if(availableData == null)
            return validPayloads
//        else
//            Log.d(TAG, "Available data: ${availableData.joinToString(", ") { it.toString() }}")

        var offset = 0

        while (availableData.size - offset > HEADER_SIZE + LENGTH_SIZE + END_SIZE) {
            val currentFrame = availableData.copyOfRange(offset, availableData.size)

            // 打印当前检查帧的实际内容
//            Log.d(TAG, "Checking frame from offset $offset: ${currentFrame.joinToString(", ") { it.toString() }}")

            offset += if (isValidResponse(currentFrame)) {
                // 提取有效负载
                val payload = extractPayload(currentFrame)
                validPayloads.add(payload)

//                Log.d(TAG, "Extracted payload: ${payload.joinToString(", ") { it.toString() }}")
                payload.size
            } else {
//                Log.e(TAG, "Invalid response at offset $offset with frame: ${currentFrame.joinToString(", ") { it.toString() }}")

                // 这里决定要怎么处理无效的帧
                1 // 或者更大的值，根据数据结构来决定
            }
        }

//        Log.d(TAG, "Valid payloads found: ${validPayloads.size}")
        return validPayloads
    }


    // 解析响应方法
    fun handleResponse(response: String): List<ByteArray>? {
        val intArray = hexStringToByteArray(response)

        if (intArray == null) {
            Log.e(TAG, "Hex string to byte array conversion failed")
            return null
        }

        // 将 IntArray 转换为 ByteArray
        val byteArray = intArray.map { it.toByte() }.toByteArray()

//        Log.d(TAG, "Converted result byte array: ${byteArray.joinToString(", ") { it.toString() }}")

        circularBuffer.write(byteArray) // 将新数据写入环形缓冲区

        // 返回有效载荷列表或空列表
        return getValidPayloadFromBuffer()
    }

    // 辅助方法
    private fun hexStringToByteArray(hex: String): IntArray? {
        if (hex.length % 2 != 0) {
            Log.e(TAG, "Hex string length is not even: ${hex.length}")
            return null // 长度必须为偶数
        }

        return IntArray(hex.length / 2).apply {
            for (i in indices) {
                val byteIndex = i * 2
                // 将十六进制字符串转换为无符号整数
                this[i] = (hex.substring(byteIndex, byteIndex + 2).toInt(16) and 0xFF)
            }
        }
    }

    private fun isValidResponse(data: ByteArray): Boolean {
        if (data.size < (HEADER_SIZE + END_SIZE + LENGTH_SIZE)) {
            Log.e(TAG, "Data too short to be valid response: ${data.size}")
            return false // 数据长度至少为帧头、帧尾和长度部分
        }
        if (!isFrameHeaderValid(data)) {
            Log.e(TAG, "Frame header is invalid")
            return false
        }
        if (!isFrameEndValid(data)) {
            Log.e(TAG, "Frame end is invalid")
            return false
        }

        val length = getPayloadLength(data)

        return data.size >= length// 检查长度
    }

    private fun isFrameHeaderValid(data: ByteArray): Boolean {
        return data[0] == FRAME_HEADER[0]
    }

    private fun isFrameEndValid(data: ByteArray): Boolean {
        val length = getPayloadLength(data)

        // 确保 length 不超过数据的大小
        if (length < 16 || length > data.size) {
            Log.e(TAG, "Length is invalid: $length, data size: ${data.size}")
            return false
        }

        val endPart = data.copyOfRange(length - FRAME_END.size, length)

        return endPart.contentEquals(FRAME_END)
//        return data[length - 7] == FRAME_END[0] && data[length - 6] == FRAME_END[1] && data[length - 5] == FRAME_END[2] && data[length - 4] == FRAME_END[3] && data[length - 3] == FRAME_END[4] && data[length - 2] == FRAME_END[5] && data[length - 1] == FRAME_END[6]
    }

    private fun extractPayload(data: ByteArray): ByteArray {

        val length = getPayloadLength(data)

        if (length <= 0 || length > data.size) {
            Log.w(TAG, "Invalid length: $length")
            return ByteArray(0) // 返回空数组以表示无效的负载
        }

        return data.copyOfRange(0, length)
    }
    private fun getPayloadLength(data: ByteArray): Int {
        return ((data[HEADER_SIZE + 3].toUByte().toInt() and 0xFF) shl 24) or
                ((data[HEADER_SIZE + 2].toUByte().toInt() and 0xFF) shl 16) or
                ((data[HEADER_SIZE + 1].toUByte().toInt() and 0xFF) shl 8) or
                (data[HEADER_SIZE].toUByte().toInt() and 0xFF)
    }
}


