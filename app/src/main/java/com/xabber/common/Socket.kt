package com.xabber.common

import android.util.Log
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Socket(private val host: String, private val port: Int) {
    private var socket: io.ktor.network.sockets.Socket? = null
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val TAG = "Socket_ng"

    // Connects to the specified host and port using Ktor TCP socket
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = aSocket(selectorManager).tcp().connect(host, port)
            Log.d(TAG, "Ktor TCP socket connected to $host:$port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to $host:$port: ${e.message}", e)
            false
        }
    }

    // Closes the socket if open
    suspend fun close() = withContext(Dispatchers.IO) {
        try {
            socket?.let {
                it.close()
                Log.d(TAG, "Ktor TCP socket closed for $host:$port")
            }
            socket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: ${e.message}", e)
        }
    }

    // Getter for the Ktor socket
    fun getSocket(): io.ktor.network.sockets.Socket? {
        return socket
    }
}