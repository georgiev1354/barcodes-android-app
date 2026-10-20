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

    private fun itf(value: String) = Detection(value, "ITF", Box(0f, 0f, 1f, 1f), weakCheck = true)

    @Test
    fun dropsItfValuesItCannotCarry() {
        listOf("1234", "12345", "12345A", "1234567").forEach { assertNull(it, BarcodeValue.checkFormat(itf(it))) }
    }

    @Test
    fun itf14WithValidCheckDigitCountsAsChecked() {
        assertEquals(false, BarcodeValue.checkFormat(itf("15400141288763"))?.weakCheck)
        assertEquals(true, BarcodeValue.checkFormat(itf("15400141288764"))?.weakCheck)
        assertEquals(true, BarcodeValue.checkFormat(itf("123456"))?.weakCheck)
    }

    @Test
    fun otherFormatsPassUnchanged() {
        val code39 = Detection("A1", "Code 39", Box(0f, 0f, 1f, 1f), weakCheck = true)
        assertEquals(code39, BarcodeValue.checkFormat(code39))
    }
}
