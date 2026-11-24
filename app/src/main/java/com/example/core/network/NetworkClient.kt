package com.example.core.network

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client and connection pool so every repository reuses the same
 * warm TCP/TLS/HTTP2 socket to the backend instead of re-handshaking per screen.
 */
object NetworkClient {
    val standard: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val speaking: OkHttpClient by lazy {
        standard.newBuilder()
            .readTimeout(35, TimeUnit.SECONDS)
            .build()
    }

    val admin: OkHttpClient by lazy {
        standard.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS) // batch TTS generation
            .build()
    }
}
