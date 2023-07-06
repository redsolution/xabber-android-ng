package com.xabber.presentation.application.fragments.chat

import android.content.ContentUris
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.MediaDto
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.presentation.XabberApplication
import com.xabber.utils.toMessageReferenceDto
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class MediaViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())

    private val _complited = MutableLiveData<Boolean>()
    val complited: LiveData<Boolean> = _complited

    fun getMediaList(): ArrayList<MediaDto> {
        val images = getImages()
        val videos = getVideos()
        val medias = ArrayList<MediaDto>()
        medias.addAll(images)
        medias.addAll(videos)
        medias.sort()
        return medias
    }

    private fun getImages(): ArrayList<MediaDto> {
        val images = ArrayList<MediaDto>()
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN
        )
        val imageQueryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val resolver = XabberApplication.applicationContext().contentResolver
        val imageCursor = resolver?.query(
            imageQueryUri,
            imageProjection,
            null,
            null,
            MediaStore.Images.Media.DATE_TAKEN + " DESC"
        )

        imageCursor?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateAddedColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val displayNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri =
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val dateAdded = Date(TimeUnit.SECONDS.toMillis(cursor.getLong(dateAddedColumn)))
                val displayName = cursor.getString(displayNameColumn)
                val mediaDto = MediaDto(id, displayName, dateAdded, contentUri, 0)
                images += mediaDto
            }
        }
        return images
    }

    private fun getVideos(): List<MediaDto> {
        val videos = mutableListOf<MediaDto>()

        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DURATION
        )
        val videoQueryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val resolver = XabberApplication.applicationContext().contentResolver
        val videoCursor = resolver?.query(
            videoQueryUri,
            videoProjection,
            null,
            null,
            MediaStore.Video.Media.DATE_TAKEN + " DESC"
        )

        videoCursor?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateAddedColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val displayNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri =
                    ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                val dateAdded =
                    Date(TimeUnit.SECONDS.toMillis(cursor.getLong(dateAddedColumn)))
                val displayName = cursor.getString(displayNameColumn)
                val duration = cursor.getLong(durationColumn)
                val mediaDto = MediaDto(id, displayName, dateAdded, contentUri, duration)
                videos += mediaDto
            }
        }
        return videos
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }


    fun insertMessageList(messages: ArrayList<MessageDto>, chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {

            realm.writeBlocking {
                for (i in 0 until messages.size) {
                    val rreferences = realmListOf<MessageReferenceStorageItem>()
                    for (j in 0 until messages[i].references.size) {
                        val ref = this.copyToRealm(MessageReferenceStorageItem().apply {
                            primary = messages[i].references[j].id + "${System.currentTimeMillis()}"
                            uri = messages[i].references[j].uri
                            mimeType = messages[i].references[j].mimeType
                            isGeo = messages[i].references[j].isGeo
                            latitude = messages[i].references[j].latitude
                            longitude = messages[i].references[j].longitude
                            isAudioMessage = messages[i].references[j].isVoiceMessage
                            fileName = messages[i].references[j].fileName
                            fileSize = messages[i].references[j].size
                        })
                        rreferences.add(ref)
                    }
                    val message = this.copyToRealm(MessageStorageItem().apply {
                        primary = messages[i].primary
                        owner = messages[i].owner
                        opponent = messages[i].opponentJid
                        body = messages[i].messageBody
                        date = messages[i].sentTimestamp
                        sentDate = messages[i].sentTimestamp
                        editDate = messages[i].editTimestamp
                        outgoing = messages[i].isOutgoing
                        isRead = !messages[i].isUnread
                        references = rreferences
                        conversationType_ = ConversationType.Channel.toString()
                    })
                    val item: LastChatsStorageItem? =
                        this.query(LastChatsStorageItem::class, "primary = '$chatId'").first()
                            .find()
                    item?.lastMessage = message
                    item?.messageDate = message.date
//                var oldValue = item?.unread ?: 0
//                oldValue++
//                item?.unread = if (messageDto.isOutgoing || isReaded) 0 else oldValue
                    item?.lastMessage?.outgoing = messages[i].isOutgoing
                    if (item != null) {
                        if (!messages[i].isOutgoing && item.muteExpired <= 0) item.isArchived = false
                    }
                }
            }
            _complited.postValue(true)
        }
    }


}
