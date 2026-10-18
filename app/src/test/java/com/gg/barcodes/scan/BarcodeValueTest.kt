package com.gg.barcodes.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeValueTest {

    @Test
    fun removesSymbologyIdentifier() {
        assertEquals("2430011117002865", BarcodeValue.clean("]C12430011117002865", stripLabels = false))
        assertEquals("4006381333931", BarcodeValue.clean("]E04006381333931", stripLabels = false))
    }

    @Test
    fun removesLabelsWhenEnabled() {
        listOf("S/N: 2430011117002865", "s/n:2430011117002865", "S/N 2430011117002865", "SN: 2430011117002865",
            "P/N#2430011117002865", "]C1S/N: 2430011117002865").forEach {
            assertEquals(it, "2430011117002865", BarcodeValue.clean(it, stripLabels = true))
        }
    }

    @Test
    fun keepsLabelsWhenDisabled() {
        assertEquals("S/N: 123", BarcodeValue.clean("S/N: 123", stripLabels = false))
    }

    @Test
    fun keepsValuesThatOnlyLookLikeLabels() {
        assertEquals("SN1234", BarcodeValue.clean("SN1234", stripLabels = true))
        assertEquals("PN-55", BarcodeValue.clean("PN-55", stripLabels = true))
        assertEquals("ABC]C1", BarcodeValue.clean("ABC]C1", stripLabels = true))
    }

    @Test
    fun nothingLeftIsNull() {
        assertNull(BarcodeValue.clean("]C1", stripLabels = true))
        assertNull(BarcodeValue.clean("S/N: ", stripLabels = true))
    }
}
