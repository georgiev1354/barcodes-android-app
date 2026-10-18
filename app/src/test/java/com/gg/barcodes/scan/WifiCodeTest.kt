package com.gg.barcodes.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiCodeTest {

    @Test
    fun parsesTypicalRouterSticker() {
        assertEquals(
            WifiCode(ssid = "ZTE_2.4G_Q5754N", password = "PRyK9EtQ", security = "WPA", hidden = false),
            WifiCode.parse("WIFI:S:ZTE_2.4G_Q5754N;T:WPA;P:PRyK9EtQ;;"),
        )
    }

    @Test
    fun fieldOrderAndCaseDoNotMatter() {
        val code = WifiCode.parse("wifi:p:secret;s:Home;t:WPA;h:true;;")!!
        assertEquals("Home", code.ssid)
        assertEquals("secret", code.password)
        assertTrue(code.hidden)
    }

    @Test
    fun unescapesSpecialCharacters() {
        val code = WifiCode.parse("""WIFI:S:My\;Net\:5G;T:WPA;P:pa\\ss\;word\,1;;""")!!
        assertEquals("My;Net:5G", code.ssid)
        assertEquals("""pa\ss;word,1""", code.password)
    }

    @Test
    fun stripsQuotes() {
        assertEquals("Cafe Wifi", WifiCode.parse("WIFI:S:\"Cafe Wifi\";T:nopass;;")!!.ssid)
    }

    @Test
    fun openNetworkHasNoPassword() {
        val code = WifiCode.parse("WIFI:T:nopass;S:Guest;;")!!
        assertNull(code.password)
        assertTrue(code.isOpen)
        assertFalse(WifiCode.parse("WIFI:S:x;T:WPA;P:y;;")!!.isOpen)
    }

    @Test
    fun rejectsOtherValues() {
        assertNull(WifiCode.parse("2430011117002865"))
        assertNull(WifiCode.parse("https://example.com"))
        assertNull(WifiCode.parse("WIFI:T:WPA;P:secret;;"))
    }
}
