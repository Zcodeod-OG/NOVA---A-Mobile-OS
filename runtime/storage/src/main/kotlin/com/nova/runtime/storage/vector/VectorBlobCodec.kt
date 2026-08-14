package com.nova.runtime.storage.vector

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Encodes/decodes embedding float vectors for Room BLOB persistence. */
object VectorBlobCodec {
    fun encode(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun decode(blob: ByteArray, expectedDimension: Int): FloatArray? {
        if (expectedDimension <= 0) return null
        val expectedBytes = expectedDimension * Float.SIZE_BYTES
        if (blob.size != expectedBytes) return null
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(expectedDimension) { buffer.getFloat() }
    }
}
