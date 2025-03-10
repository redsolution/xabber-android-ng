package com.xabber.presentation.application.CloudStorage

data class StorageUsage(
    val totalStorage: Double, // Total storage in GB
    val usedStorage: Double,   // Used storage in GB
    val usageByCategory: List<StorageUsageItem> // List of storage usage by category

)
