package com.v2ray.ang.util

import com.v2ray.ang.AppConfig.LOOPBACK
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.Utils.encode
import com.v2ray.ang.util.Utils.urlDecode
import java.io.IOException
import java.net.*
import java.util.*

object HttpUtil {

    /**
     * Converts a URL string to its ASCII representation.
     *
     * @param str The URL string to convert.
     * @return The ASCII representation of the URL.
     */
    fun idnToASCII(str: String): String {
        val url = URI(str)
        val host = url.host
        val asciiHost = IDN.toASCII(url.host, IDN.ALLOW_UNASSIGNED)
        if (host != asciiHost) {
            return str.replace(host, asciiHost)
        } else {
            return str
        }
    }

    /**
     * Retrieves the content of a URL as a string.
     *
     * @param url The URL to fetch content from.
     * @param timeout The timeout value in milliseconds.
     * @param httpPort The HTTP port to use.
     * @return The content of the URL as a string.
     */
    fun getUrlContent(url: String, timeout: Int, httpPort: Int = 0): String? {
        val conn = createProxyConnection(url, httpPort, timeout, timeout) ?: return null
        try {
            return conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
        } finally {
            conn.disconnect()
        }
        return null
    }

    /**
     * Retrieves the content of a URL as a string with a custom User-Agent header.
     *
     * @param url The URL to fetch content from.
     * @param timeout The timeout value in milliseconds.
     * @param httpPort The HTTP port to use.
     * @return The content of the URL as a string.
     * @throws IOException If an I/O error occurs.
     */
    @Throws(IOException::class)
    fun getUrlContentWithUserAgent(url: String?, timeout: Int = 15000, httpPort: Int = 0): String {
        var currentUrl = url
        var redirects = 0
        val maxRedirects = 3

        while (redirects++ < maxRedirects) {
            if (currentUrl == null) continue
            val conn = createProxyConnection(currentUrl, httpPort, timeout, timeout) ?: continue
            conn.setRequestProperty("User-agent", "v2rayNG/${BuildConfig.VERSION_NAME}")
            conn.connect()

            val responseCode = conn.responseCode
            when (responseCode) {
                in 300..399 -> {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrEmpty()) {
                        throw IOException("Redirect location not found")
                    }
                    currentUrl = location
                    continue
                }

                else -> try {
                    return conn.inputStream.use { it.bufferedReader().readText() }
                } finally {
                    conn.disconnect()
                }
            }
        }
        throw IOException("Too many redirects")
    }

    /**
     * Creates an HttpURLConnection object connected through a proxy.
     *
     * @param urlStr The target URL address.
     * @param port The port of the proxy server.
     * @param connectTimeout The connection timeout in milliseconds (default is 15000 ms).
     * @param readTimeout The read timeout in milliseconds (default is 15000 ms).
     * @param needStream Whether the connection needs to support streaming.
     * @return Returns a configured HttpURLConnection object, or null if it fails.
     */
    fun createProxyConnection(
        urlStr: String,
        port: Int,
        connectTimeout: Int = 15000,
        readTimeout: Int = 15000,
        needStream: Boolean = false
    ): HttpURLConnection? {

        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            // Create a connection
            conn = if (port == 0) {
                url.openConnection()
            } else {
                url.openConnection(
                    Proxy(
                        Proxy.Type.HTTP,
                        InetSocketAddress(LOOPBACK, port)
                    )
                )
            } as HttpURLConnection

            // Set connection and read timeouts
            conn.connectTimeout = connectTimeout
            conn.readTimeout = readTimeout
            if (!needStream) {
                // Set request headers
                conn.setRequestProperty("Connection", "close")
                // Disable automatic redirects
                conn.instanceFollowRedirects = false
                // Disable caching
                conn.useCaches = false
            }

            //Add Basic Authorization
            url.userInfo?.let {
                conn.setRequestProperty(
                    "Authorization",
                    "Basic ${encode(urlDecode(it))}"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If an exception occurs, close the connection and return null
            conn?.disconnect()
            return null
        }
        return conn
    }
}

