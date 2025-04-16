package com.xabber.xmpp.push_notifications

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import java.util.Date

open class PushMessageTemporaryStorageItem: RealmObject {
    @Index
    val target: String=""
    @Index
    val payload: String=""
    @Index
    val date: Date? = null
}