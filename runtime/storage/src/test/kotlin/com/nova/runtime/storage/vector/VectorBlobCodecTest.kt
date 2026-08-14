package com.nova.runtime.storage.vector

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VectorBlobCodecTest {
    @Test
    fun encodeDecode_roundTripsVector() {
        val vector = floatArrayOf(0.1f, -0.5f, 1.25f, 0f)
        val blob = VectorBlobCodec.encode(vector)
        assertEquals(vector.size * Float.SIZE_BYTES, blob.size)
        assertArrayEquals(vector, VectorBlobCodec.decode(blob, vector.size), 0.0001f)
    }

    @Test
    fun decode_rejectsWrongDimension() {
        val blob = VectorBlobCodec.encode(floatArrayOf(1f, 2f, 3f))
        assertNull(VectorBlobCodec.decode(blob, expectedDimension = 2))
        assertNull(VectorBlobCodec.decode(blob, expectedDimension = 0))
    }
}
