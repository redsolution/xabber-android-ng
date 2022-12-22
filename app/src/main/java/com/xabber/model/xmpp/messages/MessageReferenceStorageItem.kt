package com.xabber.model.xmpp.messages


import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class MessageReferenceStorageItem: RealmObject {  // вложения
    @PrimaryKey
    var primary: String = ""


}