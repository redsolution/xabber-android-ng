package com.xabber.model.xmpp.messages

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

class MessageReferenceStorageItem: RealmObject {
    @PrimaryKey
    var primary: String = ""


}