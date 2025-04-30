package com.xabber.xmpp.push_notifications

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index
import java.util.Date

open class PushMessageTemporaryStorageItem: RealmObject {

    var target: String=""

    var payload: String=""
    @Ignore
    var date: Date? = null
}