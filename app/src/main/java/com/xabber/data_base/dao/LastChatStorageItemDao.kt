package com.xabber.data_base.dao

import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import io.realm.kotlin.Realm

class LastChatStorageItemDao(private val realm: Realm) {

    fun getItemByPrimary(primary: String): LastChatsStorageItem? {
        var item: LastChatsStorageItem? = null
        realm.writeBlocking {
            item = this.query(LastChatsStorageItem::class, "primary = '$primary'").first().find()
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

    suspend fun setPinnedPosition(primary: String, pinnedPosition: Long) {
        realm.write {
            val item =
                this.query(LastChatsStorageItem::class, "primary = '$primary'").first().find()
            if (item != null)
                item.pinnedPosition = pinnedPosition
        }
    }

    suspend fun setArchived(primary: String) {
        realm.write {
            val item =
                this.query(LastChatsStorageItem::class, "primary = '$primary'").first().find()
            if (item != null)
                item.isArchived = !item.isArchived
        }
    }

    suspend fun deleteItem(primary: String) {
        realm.write {
            val item =
                this.query(LastChatsStorageItem::class, "primary = '$primary'").first().find()
            if (item != null) findLatest(item)?.let { delete(it) }
        }
    }

    suspend fun setMuteExpired(primary: String, muteExpired: Long) {
        realm.write {
            val item =
                this.query(LastChatsStorageItem::class, "primary = '$primary'").first().find()
            if (item != null)
                item.muteExpired = muteExpired
        }
    }

}
