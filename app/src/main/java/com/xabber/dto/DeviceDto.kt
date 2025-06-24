package com.xabber.dto

data class DeviceDto(
    val uid: String, // Unique device ID
    val name: String, // Device name (e.g., client or device field)
    val description: String, // Public label (descr field)
    val model: String, // Device model (device field)
    val lastAuth: String, // Formatted authDate
    val isExpired: Boolean, // Whether the device has expired
    val client: String

)