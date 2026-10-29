package com.gg.barcodes.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintedTextTest {

    private val barcode = Box(100f, 100f, 500f, 160f)
    private fun code39(value: String) = Detection(value, "Code 39", barcode, weakCheck = true)
    private fun line(text: String, box: Box) = TextLine(text, box)

    private val under = Box(150f, 165f, 450f, 185f)

    @Test
    fun matchesTextUnderOrAboveBarcode() {
        assertTrue(PrintedText.matches(code39("88F7C7C4ADD8"), listOf(line("88F7C7C4ADD8", under))))
        assertTrue(PrintedText.matches(code39("88F7C7C4ADD8"), listOf(line("88F7C7C4ADD8", Box(150f, 70f, 450f, 95f)))))
    }

    @Test
    fun ignoresTextBesideOrFarFromBarcode() {
        assertFalse(PrintedText.matches(code39("88F7C7C4ADD8"), listOf(line("88F7C7C4ADD8", Box(520f, 110f, 800f, 140f)))))
        assertFalse(PrintedText.matches(code39("88F7C7C4ADD8"), listOf(line("88F7C7C4ADD8", Box(150f, 400f, 450f, 420f)))))
    }

    @Test
    fun differentTextDoesNotMatch() {
        assertFalse(PrintedText.matches(code39("88F7C7C4ADD8"), listOf(line("88F7C7C4ADD9", under))))
        assertFalse(PrintedText.matches(code39("C4ADD8"), listOf(line("88F7C7C4ADD8", under))))
    }

    @Test
    fun ignoresStartStopStarsSpacesAndLabels() {
        assertTrue(PrintedText.matches(code39("ABC123"), listOf(line("*ABC 123*", under))))
        assertTrue(PrintedText.matches(code39("12345678"), listOf(line("S/N: 1234 5678", under))))
    }

    @Test
    fun dashesAreDroppedOnlyWhenValueHasNone() {
        assertTrue(PrintedText.matches(code39("AB12CD"), listOf(line("AB-12-CD", under))))
        assertTrue(PrintedText.matches(code39("AB-12"), listOf(line("AB-12", under))))
    }

    @Test
    fun digitLookalikesAreReplacedOnlyForAllDigitValues() {
        assertTrue(PrintedText.matches(code39("1020"), listOf(line("I02O", under))))
        assertFalse(PrintedText.matches(code39("1O2O"), listOf(line("1020", under))))
    }
}
