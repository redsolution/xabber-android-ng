package com.xabber.data_base.dao

import com.xabber.models.xmpp.last_chats.LastChatsStorageItem
import io.realm.kotlin.Realm

class LastChatStorageItemDao(private val realm: Realm) {

    fun getItemByPrimary(primary: String): LastChatsStorageItem? {
        var item: LastChatsStorageItem? = null
         realm.writeBlocking {
            item = this.query(LastChatsStorageItem::class, "primary = '$primary").first().find()
        }
        return item
    }

    fun getItemList(query: String): ArrayList<LastChatsStorageItem> {
        val itemList = ArrayList<LastChatsStorageItem>()
        realm.writeBlocking {
            itemList.addAll(this.query(LastChatsStorageItem::class, query).find())
        }
        return itemList
    }

    fun setPinnedPosition(primary: String, pinnedPosition: Long) {
        realm.writeBlocking {
         val item = this.query(LastChatsStorageItem::class, "primary = '$primary'").first().find()
            item?.pinnedPosition = pinnedPosition
        }
    }

    fun setArchived(primary: String, isArchived: Boolean) {
        realm.writeBlocking {
            val item = this.query(LastChatsStorageItem::class, "primary = '$primary'").first().find()
            item?.isArchived = isArchived
        }
    }

    fun deleteItem(primary: String) {
        realm.writeBlocking {
            val item = this.query(LastChatsStorageItem::class, "primary = '$primary'").first().find()
            if (item != null) findLatest(item)?.let { delete(it) }
        }
    }

    fun setMuteExpired(primary: String, muteExpired: Long) {
        realm.writeBlocking {
            val item = this.query(LastChatsStorageItem::class, "primary = '$primary'").first().find()
            item?.muteExpired = muteExpired
        }
    }

}
