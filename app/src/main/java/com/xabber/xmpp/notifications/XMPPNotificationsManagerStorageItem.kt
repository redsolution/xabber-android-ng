package com.xabber.xmpp.notifications

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.json.JSONObject
import android.util.Log
import com.xabber.utils.toMap
import io.realm.kotlin.types.annotations.Ignore
import java.util.Date


open class XMPPNotificationsManagerStorageItem : RealmObject {
    companion object {

        fun genPrimary(owner: String): String {
            return listOf(owner).prp()
        }
    }

    @PrimaryKey
    var primary: String = ""

    var owner: String = ""
    var lastItemId: String? = null
    var unread: Int = 0
    var node: String? = null
}


open class NotificationStorageItem : RealmObject {
    companion object {
        private const val TAG = "NotificationStorageItem"


        fun genPrimary(owner: String, jid: String, uniqueId: String): String {
            return listOf(owner, jid, uniqueId).prp()
        }
    }

    @PrimaryKey
    var primary: String = ""

    var owner: String = ""
    var jid: String = ""
    var uniqueId: String = ""

    private var categoryRaw: String = ""

    var isRead: Boolean = true
    var associatedJid: String? = null
    var displayedNick: String? = null
    var text: String? = null
    private var metadataRaw: String? = null
    @Ignore
    var date: Date = Date()
    var shouldShow: Boolean = false


    var category: Category
        get() = Category.fromRaw(categoryRaw)
        set(value) {
            categoryRaw = value.rawValue
        }

    var metadata: Map<String, Any>?
        get() = metadataRaw?.let { raw ->
            try {
                JSONObject(raw).toMap()
            } catch (e: Exception) {
                Log.e(TAG, "Cannot parse metadata: ${e.message}")
                null
            }
        }
        set(value) {
            metadataRaw = value?.let { map ->
                try {
                    JSONObject(map).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot encode metadata: ${e.message}")
                    null
                }
            }
        }
}

enum class Category(val rawValue: String) {
    DEVICE("device"),
    CONTACT("contact"); // Assumed from prior context

    companion object {
        fun fromRaw(raw: String): Category =
            values().find { it.rawValue == raw } ?: DEVICE
    }
}

// Utility to convert JSONObject to Map (simplified)
