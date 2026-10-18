package com.gg.barcodes.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val base = "https://apks.gg-webapp.com/com.gg.smsparking/check-update"
    private fun info(code: Long, minSdk: Int = 0) = UpdateInfo(code, "1.$code", "x.apk", "a".repeat(64), minSdk = minSdk)

    @Test
    fun newerOnlyWhenHigherAndSupported() {
        assertTrue(UpdatePolicy.isNewer(info(2), 1, 35))
        assertFalse(UpdatePolicy.isNewer(info(1), 1, 35))
        assertFalse(UpdatePolicy.isNewer(info(2, minSdk = 36), 1, 35))
    }

    @Test
    fun checkUrlAppendsParameters() {
        assertEquals("$base?versionCode=3&sdk=35", UpdatePolicy.checkUrl(base, 3, 35))
        assertEquals("$base?ch=beta&versionCode=3&sdk=35", UpdatePolicy.checkUrl("$base?ch=beta", 3, 35))
    }

    @Test
    fun apkUrlMustBeHttps() {
        assertEquals("https://apks.gg-webapp.com/com.gg.smsparking/apk/com.gg.smsparking-2.apk", UpdatePolicy.resolveApkUrl(base, "apk/com.gg.smsparking-2.apk"))
        assertEquals("https://cdn.example.com/a.apk", UpdatePolicy.resolveApkUrl(base, "https://cdn.example.com/a.apk"))
        assertNull(UpdatePolicy.resolveApkUrl(base, "http://cdn.example.com/a.apk"))
        assertEquals("http://10.0.2.2:8000/a.apk", UpdatePolicy.resolveApkUrl("http://10.0.2.2:8000/x", "/a.apk", allowHttp = true))
        assertNull(UpdatePolicy.resolveApkUrl(base, "file:///sdcard/a.apk", allowHttp = true))
    }

    @Test
    fun sha256Format() {
        assertTrue(UpdatePolicy.isSha256("0123456789abcdef".repeat(4)))
        assertFalse(UpdatePolicy.isSha256("0123456789ABCDEF".repeat(4)))
        assertFalse(UpdatePolicy.isSha256("abc"))
        assertEquals("00ff10", UpdatePolicy.hex(byteArrayOf(0, -1, 16)))
    }
}
