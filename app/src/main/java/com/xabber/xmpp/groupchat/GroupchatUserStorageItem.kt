package com.xabber.xmpp.groupchat

import com.xabber.utils.prp
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import android.content.Context
import android.net.Uri
import android.util.Log
import com.xabber.R
import io.realm.kotlin.types.annotations.Ignore
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale


open class GroupchatUserStorageItem : RealmObject {
    enum class Role(val rawValue: String) {
        OWNER("owner"),
        ADMIN("admin"),
        MEMBER("member");

        companion object {
            fun fromRaw(raw: String): Role =
                values().find { it.rawValue == raw } ?: MEMBER
        }


    }

    enum class IntegerRole(val rawValue: Int) {
        OWNER(1),
        ADMIN(2),
        MEMBER(20)
    }

    enum class Subscribtion(val rawValue: String) {
        NONE("none"),
        BOTH("both");

        companion object {
            fun fromRaw(raw: String): Subscribtion =
                values().find { it.rawValue == raw } ?: BOTH
        }
    }

    companion object {
        private const val TAG = "GroupchatUserStorageItem"

        fun primaryKey(): String? = "primary"

        fun indexedProperties(): List<String> = listOf("owner", "groupchatId")

        fun genPrimary(id: String, groupchat: String, owner: String): String {
            return listOf(id, groupchat, owner).prp()
        }
    }

    @PrimaryKey
    var primary: String = ""
    var groupchatId: String = ""
    var userId: String = ""
    var owner: String = ""
    var jid: String = ""
    var role_: String = Role.MEMBER.rawValue
    var nickname: String = ""
    var badge: String = ""
    var avatarURI: String = ""
    var temporaryAvatarHash: String = ""
    var avatarHash: String = ""
    var isOnline: Boolean = false
    var lastSeen: Long = 0L
    var isBlocked: Boolean = false
    var isKicked: Boolean = false
    var isTemporary: Boolean = false
    var permissons_: RealmList<String> = realmListOf()
    var restrictions_: RealmList<String> = realmListOf()
    var subscribtion_: String = Subscribtion.BOTH.rawValue
    var isMe: Boolean = false
    var sortedRole: Int = IntegerRole.MEMBER.rawValue
    var updateTimestamp: Long = 0L

    var subscribtion: Subscribtion
        get() = when (subscribtion_) {
            Subscribtion.BOTH.rawValue -> Subscribtion.BOTH
            Subscribtion.NONE.rawValue -> Subscribtion.NONE
            else -> Subscribtion.BOTH
        }
        set(newValue) {
            subscribtion_ = newValue.rawValue
        }

    var role: Role
        get() = when (role_) {
            Role.MEMBER.rawValue -> Role.MEMBER
            Role.ADMIN.rawValue -> Role.ADMIN
            Role.OWNER.rawValue -> Role.OWNER
            else -> Role.MEMBER
        }
        set(newValue) {
            role_ = newValue.rawValue
        }

    var avatarURL: Uri?
        get() = Uri.parse(avatarURI)
        set(newValue) {
            avatarURI = newValue?.toString() ?: ""
        }

    val avatarKey: Uri
        get() = Uri.parse(listOf(userId, groupchatId).prp())

    var permissions: List<Map<String, String>>
        get() {
            val out = mutableListOf<Map<String, String>>()
            permissons_.forEach { item ->
                try {
                    val json = JSONObject(item)
                    val map = mutableMapOf<String, String>()
                    json.keys().forEach { key ->
                        map[key] = json.getString(key)
                    }
                    out.add(map)
                } catch (e: Exception) {
                    Log.e(TAG, "permissions: ${e.message}")
                }
            }
            return out
        }
        set(newValue) {
            val out = mutableListOf<String>()
            newValue.forEach { map ->
                try {
                    val json = JSONObject(map)
                    out.add(json.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "permissions: ${e.message}")
                }
            }
            permissons_.clear()
            permissons_.addAll(out)
        }

    var restrictions: List<Map<String, String>>
        get() {
            val out = mutableListOf<Map<String, String>>()
            restrictions_.forEach { item ->
                try {
                    val json = JSONObject(item)
                    val map = mutableMapOf<String, String>()
                    json.keys().forEach { key ->
                        map[key] = json.getString(key)
                    }
                    out.add(map)
                } catch (e: Exception) {
                    Log.e(TAG, "restrictions: ${e.message}")
                }
            }
            return out
        }
        set(newValue) {
            val out = mutableListOf<String>()
            newValue.forEach { map ->
                try {
                    val json = JSONObject(map)
                    out.add(json.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "restrictions: ${e.message}")
                }
            }
            restrictions_.clear()
            restrictions_.addAll(out)
        }

//    val dateString: String?
//        get() {
//            val lastSeenDateFormatter = SimpleDateFormat("", Locale.getDefault())
//            lastSeen?.let { date ->
//                val today = Date()
//                val interval = (today.time - date.time) / 1000
//                if (interval < 60) {
//                    lastSeenDateFormatter.applyPattern(context.getString(R.string.chat_seen_just_now))
//                } else if (interval < 60 * 60) {
//                    val minutes = (interval / 60).toInt()
//                    lastSeenDateFormatter.applyPattern(
//                        context.getString(R.string.chat_seen_minutes_ago, minutes)
//                    )
//                } else if (interval < 2 * 60 * 60) {
//                    lastSeenDateFormatter.applyPattern(context.getString(R.string.chat_seen_hour_ago))
//                } else if (interval < 12 * 60 * 60) {
//                    lastSeenDateFormatter.applyPattern(context.getString(R.string.chat_seen_at))
//                } else if (interval < 24 * 60 * 60) {
//                    lastSeenDateFormatter.applyPattern(context.getString(R.string.chat_seen_yesterday))
//                } else {
//                    val calendar = Calendar.getInstance()
//                    calendar.time = date
//                    val todayCalendar = Calendar.getInstance()
//                    todayCalendar.time = today
//                    val days = daysBetween(calendar, todayCalendar)
//                    if (days <= 7) {
//                        lastSeenDateFormatter.applyPattern(context.getString(R.string.chat_seen_date_time))
//                    } else if (todayCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)) {
//                        lastSeenDateFormatter.applyPattern(context.getString(R.string.chat_seen_date))
//                    } else {
//                        lastSeenDateFormatter.applyPattern(context.getString(R.string.chat_seen_date_year))
//                    }
//                }
//                return lastSeenDateFormatter.format(date)
//            }
//            return null
//        }

@Ignore
    private lateinit var context: Context

    fun setContext(context: Context) {
        this.context = context
    }

    private fun daysBetween(start: Calendar, end: Calendar): Int {
        val startDate = start.clone() as Calendar
        startDate.set(Calendar.HOUR_OF_DAY, 0)
        startDate.set(Calendar.MINUTE, 0)
        startDate.set(Calendar.SECOND, 0)
        startDate.set(Calendar.MILLISECOND, 0)
        val endDate = end.clone() as Calendar
        endDate.set(Calendar.HOUR_OF_DAY, 0)
        endDate.set(Calendar.MINUTE, 0)
        endDate.set(Calendar.SECOND, 0)
        endDate.set(Calendar.MILLISECOND, 0)
        return ((endDate.timeInMillis - startDate.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
    }
}