package com.wyg.smart_man.protocol


class CircularBuffer(private val size: Int) {
    private val buffer = ByteArray(size)
    private var head = 0
    private var tail = 0
    private var isFull = false

    init {
        require(size > 0) { "Buffer size must be positive" }
    }

    fun write(data: ByteArray) {
        if (data.size > size) {
            throw IllegalArgumentException("Input data size exceeds buffer capacity")
        }

        for (byte in data) {
            buffer[head] = byte
            head = (head + 1) % size

            if (isFull) {
                tail = (tail + 1) % size // 覆盖旧数据
            }

            isFull = head == tail // 更新 `isFull` 状态
        }
    }

    fun read(count: Int): ByteArray? {
        if (isEmpty() || count <= 0) return null

        val actualReadCount = minOf(count, availableSize())
        val result = ByteArray(actualReadCount)

        for (i in 0 until actualReadCount) {
            result[i] = buffer[tail]
            tail = (tail + 1) % size
        }

        isFull = false // 读取完后状态变为非满状态
        return result
    }

    private fun isEmpty() = !isFull && head == tail

    private fun availableSize(): Int {
        return if (isFull) size else (head - tail + size) % size
    }

    fun readAvailableData(): ByteArray? {
        return read(availableSize())
    }

    fun availableData(): ByteArray {
        val length = availableSize()
        val result = ByteArray(length)

        if (length == 0) return result

        // 处理环形缓冲区的情况
        if (tail + length <= size) {
            // 可以在一块内复制
            buffer.copyInto(result, destinationOffset = 0, startIndex = tail, endIndex = tail + length)
        } else {
            // 需要分两次复制
            val firstPartLength = size - tail // 从 tail 到 buffer 末尾
            buffer.copyInto(result, destinationOffset = 0, startIndex = tail, endIndex = size) // 复制至末尾
            buffer.copyInto(result, destinationOffset = firstPartLength, startIndex = 0, endIndex = (tail + length) % size) // 复制开头
        }

        return result
    }

    fun clear() {
        head = 0
        tail = 0
        isFull = false
    }
}

