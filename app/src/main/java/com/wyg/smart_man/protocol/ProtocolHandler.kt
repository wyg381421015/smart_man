package com.wyg.smart_man.protocol

import android.util.Log
import java.nio.ByteBuffer

class ProtocolHandler {

    companion object {
        private const val TAG = "ProtocolHandler"

        private val FRAME_HEADER = byteArrayOf(0xEB.toByte(), 0x90.toByte(), 0xEB.toByte(), 0x90.toByte(), 0xEB.toByte(), 0x90.toByte())
        private val FRAME_END = byteArrayOf(0x90.toByte(), 0xEB.toByte())

        private const val HEADER_SIZE = 6
        private const val LENGTH_SIZE = 2
        private const val END_SIZE = 2
    }

    private val circularBuffer = CircularBuffer(1024)

    // 组帧方法
    fun frameData(command: String, parameters: ByteArray): ByteArray {
//        Log.d(TAG, "Framing data with command: $command and parameters: ${parameters.joinToString(", ")}")

        val startByte = FRAME_HEADER
        val endByte = FRAME_END
        val commandByte = command.toByte()
        val length = parameters.size + HEADER_SIZE + LENGTH_SIZE + END_SIZE // 计算长度

        val frame = ByteBuffer.allocate(HEADER_SIZE + LENGTH_SIZE + parameters.size + END_SIZE).apply {
            put(startByte)
            put(length.toByte())
            put(commandByte)
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

        Log.d(TAG, "Converted result byte array: ${byteArray.joinToString(", ") { it.toString() }}")

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

        // 使用无符号处理长度
        val length = data[HEADER_SIZE].toUByte().toInt() // 从帧中提取的长度
//        Log.d(TAG, "Validating response with length: $length, actual length: ${data.size}")

        return data.size >= length// 检查长度
    }

    private fun isFrameHeaderValid(data: ByteArray): Boolean {
        return data[0] == FRAME_HEADER[0] && data[1] == FRAME_HEADER[1] && data[2] == FRAME_HEADER[2] && data[3] == FRAME_HEADER[3] && data[4] == FRAME_HEADER[4] && data[5] == FRAME_HEADER[5]
    }

    private fun isFrameEndValid(data: ByteArray): Boolean {
//        val length = data[HEADER_SIZE].toUByte().toInt()
//        return data[length - 2] == FRAME_END[0] && data[length - 1] == FRAME_END[1]
        val length = data[HEADER_SIZE].toUByte().toInt()
        // 确保 length 不超过数据的大小
        if (length < 2 || length > data.size) {
            Log.e(TAG, "Length is invalid: $length, data size: ${data.size}")
            return false
        }
        return data[length - 2] == FRAME_END[0] && data[length - 1] == FRAME_END[1]
    }

    private fun extractPayload(data: ByteArray): ByteArray {
        // 提取帧长度
//        val length = ByteBuffer.wrap(data, HEADER_SIZE, LENGTH_SIZE).short.toInt() // 确保读取正确的长度
        val length = data[HEADER_SIZE].toUByte().toInt() // 从帧中提取的长度

        if (length <= 0 || length > data.size) {
            Log.w(TAG, "Payload length is zero or negative or exceeds data size: length = $length, data.size = ${data.size}")
            return ByteArray(0) // 返回空数组以表示无效的负载
        }

        // 打印提取的长度和数据
//        Log.d(TAG, "Extracting payload, extracted length: $length, from data: ${data.joinToString(", ") { it.toString() }}")

        // 这里需要确保起始和结束范围是准确的
        val start = 0
        val end = start + length

        // 向调试信息添加关于数据的长度的详细信息
//        Log.d(TAG, "Attempting to extract payload from range [$start, $end] of data size: ${data.size}")

        if (length <= 0 || end > data.size) {
            Log.w(TAG, "Payload length is zero or negative or exceeds data size: length = $length, data.size = ${data.size}")
            return ByteArray(0) // 返回空数组以表示无效的负载
        }

        return data.copyOfRange(start, end)
    }
}
