package com.abdellatif.clipsave.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Shared OkHttp client with browser-like defaults. */
object HttpClient {

    data class Resource(
        val body: String,
        val finalUrl: String,
        val contentType: String?
    )

    data class Probe(
        val finalUrl: String,
        val contentType: String?
    )

    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val probeClient: OkHttpClient = client.newBuilder()
        .callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun request(url: String, mobile: Boolean = false): Request = Request.Builder()
        .url(url)
        .header("User-Agent", if (mobile) MOBILE_UA else DESKTOP_UA)
        .header("Accept", "*/*")
        .header("Accept-Language", "en-US,en;q=0.9")
        .build()

    fun getString(url: String, mobile: Boolean = false): String? =
        getResource(url, mobile)?.body

    fun getResource(url: String, mobile: Boolean = false): Resource? = runCatching {
        client.newCall(request(url, mobile)).execute().use { response ->
            if (!response.isSuccessful) return@use null
            Resource(
                body = response.body?.string().orEmpty(),
                finalUrl = response.request.url.toString(),
                contentType = response.body?.contentType()?.toString()
            )
        }
    }.getOrNull()

    /** Lightweight redirect/content-type check used before choosing the video engine. */
    fun probe(url: String, mobile: Boolean = true): Probe? {
        val base = runCatching { request(url, mobile) }.getOrNull() ?: return null
        val head = runCatching {
            probeClient.newCall(base.newBuilder().head().build()).execute().use { response ->
                if (response.isSuccessful) {
                    Probe(response.request.url.toString(), response.header("Content-Type"))
                } else {
                    null
                }
            }
        }.getOrNull()
        if (head != null) return head

        return runCatching {
            probeClient.newCall(base.newBuilder().header("Range", "bytes=0-0").build())
                .execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    Probe(response.request.url.toString(), response.header("Content-Type"))
                }
        }.getOrNull()
    }

    fun resolveFinalUrl(url: String): String =
        probe(url)?.finalUrl ?: url

    inline fun execute(url: String, mobile: Boolean = false, block: (Response) -> Unit) {
        client.newCall(request(url, mobile)).execute().use(block)
    }
}
