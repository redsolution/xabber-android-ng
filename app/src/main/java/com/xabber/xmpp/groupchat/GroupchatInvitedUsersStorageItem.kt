package com.xabber.xmpp.groupchat

import com.xabber.R
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

/**
 * Realm model for storing invited users of a group chat.
 * Primary key is generated from jid, groupchatId and owner.
 */
open class GroupchatInvitedUsersStorageItem : RealmObject {

    @PrimaryKey
    var primary: String = ""

    var groupchatId: String = ""
    var owner: String = ""
    var jid: String = ""
    var nickname: String = ""
    var lastSeen: Long = 0

    var updatedTS: Double = 0.0
    var oldschoolAvatarKey: String? = null
    var avatarMaxUrl: String? = null
    var avatarMinUrl: String? = null
    var avatarUpdatedTS: Double = -1.0

    /**
     * Returns the best available avatar URL:
     * avatarMaxUrl → avatarMinUrl → oldschoolAvatarKey
     */
    val avatarUrl: String?
        get() = avatarMaxUrl ?: avatarMinUrl ?: oldschoolAvatarKey

    /**
     * Returns a localized, human-readable string describing when the user was last seen.
     * @param context Android Context needed to access string resources.
     */
//    fun getDateString(context: android.content.Context): String? {
//        val lastSeen = this.lastSeen ?: return null
//        val now = Date()
//        val diffMillis = now.time - lastSeen.time
//        val seconds = diffMillis / 1000
//        val minutes = seconds / 60
//        val hours = minutes / 60
//        val days = hours / 24
//
//        val res = context.resources
//
//        return when {
//            seconds < 60 -> res.getString(R.string.chat_seen_just_now)
//            minutes < 60 -> res.getString(R.string.chat_seen_minutes_ago, minutes)
//            hours < 2   -> res.getString(R.string.chat_seen_hour_ago)
//            hours < 12  -> res.getString(R.string.chat_seen_at, formatTime(lastSeen))
//            hours < 24  -> res.getString(R.string.chat_seen_yesterday, formatTime(lastSeen))
//            days <= 7   -> res.getString(R.string.chat_seen_date_time, formatDayOfWeek(lastSeen), formatTime(lastSeen))
//            isSameYear(lastSeen, now) -> res.getString(R.string.chat_seen_date, formatDayMonth(lastSeen))
//            else        -> res.getString(R.string.chat_seen_date_year, formatDate(lastSeen))
//        }
//    }

    // region Date formatting helpers
    private fun formatTime(date: Date): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)

    private fun formatDayOfWeek(date: Date): String =
        SimpleDateFormat("E", Locale.getDefault()).format(date)

    private fun formatDayMonth(date: Date): String =
        SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)

    private fun formatDate(date: Date): String =
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(date)

    private fun isSameYear(date1: Date, date2: Date): Boolean {
        val cal = Calendar.getInstance()
        cal.time = date1
        val year1 = cal.get(Calendar.YEAR)
        cal.time = date2
        val year2 = cal.get(Calendar.YEAR)
        return year1 == year2
    }
    // endregion

    companion object {
        /**
         * Generates the primary key for a given combination.
         * @param jid The JID of the invited user.
         * @param groupchat The group chat identifier.
         * @param owner The owner of the group chat.
         * @return A string that uniquely identifies this invited user entry.
         */
        fun genPrimary(jid: String, groupchat: String, owner: String): String =
            "$jid:$groupchat:$owner"
    }
}