package com.libexil.spcforge.core

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Plain HTTPS GETs (HttpURLConnection behaves the same on Android and the JVM). */
object Net {
    var userAgent = "SPCForge-Android/1.0 (github.com/LibexiL/SPCForge)"

    init {
        try {
            if (System.getProperty("http.maxConnections") == null) System.setProperty("http.maxConnections", "8")
            System.setProperty("http.keepAlive", "true")
        } catch (_: Exception) {}
    }

    class HttpError(val code: Int, url: String) : IOException("HTTP $code for $url")

    fun open(url: String, timeoutMs: Int = 20_000, accept: String? = null): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.instanceFollowRedirects = true
        c.useCaches = false
        c.setRequestProperty("User-Agent", userAgent)
        c.setRequestProperty("Accept-Encoding", "gzip")
        c.setRequestProperty("Cache-Control", "no-cache")
        if (accept != null) c.setRequestProperty("Accept", accept)
        val code = c.responseCode
        if (code !in 200..299) {
            try { c.errorStream?.use { it.readBytes() } } catch (_: Exception) {}
            c.disconnect()
            throw HttpError(code, url)
        }
        return c
    }

    fun stream(c: HttpURLConnection): InputStream {
        val s = c.inputStream
        return if ("gzip".equals(c.contentEncoding, true)) java.util.zip.GZIPInputStream(s) else s
    }

    fun getBytes(url: String, timeoutMs: Int = 25_000, accept: String? = null, attempts: Int = 2): ByteArray {
        var last: IOException? = null
        for (attempt in 0 until attempts) {
            try {
                val c = open(url, timeoutMs, accept)
                try {
                    return stream(c).use { it.readBytes() }
                } catch (e: IOException) {
                    c.disconnect(); throw e
                }
            } catch (e: HttpError) {
                if (e.code in 400..499) throw e
                last = e
            } catch (e: IOException) {
                last = e
            }
            if (attempt + 1 < attempts) Thread.sleep(500L * (attempt + 1))
        }
        throw last ?: IOException("download failed: $url")
    }

    fun getText(url: String, timeoutMs: Int = 25_000, accept: String? = null, attempts: Int = 2): String =
        String(getBytes(url, timeoutMs, accept, attempts), Charsets.UTF_8)

    /** SPC pages are Latin-1 / Windows-1252 in places; decode leniently. */
    fun getPage(url: String): String {
        val b = getBytes(url)
        val s = String(b, Charsets.UTF_8)
        return if (s.contains('�')) String(b, Charsets.ISO_8859_1) else s
    }
}
