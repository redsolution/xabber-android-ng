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
import com.xabber.presentation.XabberApplication
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

    fun insertMessageList(messages: List<MessageStorageItem>, chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                var lastChat: LastChatsStorageItem? =
                    query<LastChatsStorageItem>("primary = $0", chatId).first().find()

                for (message in messages) {
                    // Assume incoming messages are unmanaged with possibly unmanaged references
                    val managedMessage = copyToRealm(message)

                    if (lastChat == null) {
                        lastChat = copyToRealm(
                            LastChatsStorageItem().apply {
                                primary = chatId
                                // Other required fields should be set by caller or defaults
                            }
                        )
                    }

                    lastChat.apply {
                        this.lastMessage = managedMessage
                        this.messageDate = managedMessage.sentDate
                        this.lastMessageId = managedMessage.messageId

                        // Basic unread handling (incoming non-read messages increment unread)
                        if (!managedMessage.outgoing && !managedMessage.isRead && muteExpired <= 0) {
                            unread += 1
                        } else if (managedMessage.outgoing) {
                            unread = 0
                        }

                        if (!managedMessage.outgoing && muteExpired <= 0) {
                            isArchived = false
                        }
                    }
                }
            }
            _complited.postValue(true)
        }
    }
}