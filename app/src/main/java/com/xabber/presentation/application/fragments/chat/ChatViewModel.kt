package com.xabber.presentation.application.fragments.chat

import android.util.Log
import androidx.lifecycle.*
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.AccountDto
import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.utils.toAccountDto
import com.xabber.utils.toChatListDto
import com.xabber.utils.toMessageReferenceDto
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File


class ChatViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())

    private val _chat = MutableLiveData<ChatListDto?>()
    val chat: LiveData<ChatListDto?> = _chat

    private val _messages = MutableLiveData<ArrayList<MessageDto>>()
    val messages: LiveData<ArrayList<MessageDto>> = _messages

    private var job: Job? = null

    private val _opponentName = MutableLiveData<String>()
    val opponentName: LiveData<String> = _opponentName

    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private val _muteExpired = MutableLiveData<Long>()
    val muteExpired: LiveData<Long> = _muteExpired

    private var messageList = ArrayList<MessageDto>()
    var a = 11

    private val _selectedCount = MutableLiveData<Int>()
    val selectedCount: LiveData<Int> = _selectedCount
    private val selectedItems = HashSet<String>()

    private var test = 0

    private var count = 0

    companion object {

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(
                modelClass: Class<T>
            ): T {
                return ChatViewModel(
                ) as T
            }
        }
    }


    init {

    }

    fun getAccountColor(owner: String): String {
        var colorKey = "blue"
        realm.writeBlocking {
            val account = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "jid = '$owner'").first().find()
            if (account != null) colorKey = account.colorKey
        }
        return colorKey
    }

    fun initMessagesListener(owner: String, opponentJid: String) {
        val request =
            realm.query(MessageStorageItem::class, "owner = '$owner' && opponent = '$opponentJid'")
        val lastChatsFlow = request.asFlow()
        viewModelScope.launch(Dispatchers.IO) {
            lastChatsFlow.collect { changes: ResultsChange<MessageStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                        val list = ArrayList<MessageDto>()
                        list.addAll(changes.list.map { T ->
                            MessageDto(
                                primary = T.primary,
                                isOutgoing = T.outgoing,
                                owner = T.owner,
                                opponentJid = T.opponent,
                                messageBody = T.body,
                                MessageSendingState.Read,
                                sentTimestamp = T.sentDate,
                                editTimestamp = T.editDate,
                                MessageDisplayType.Text,
                                canEditMessage = false,
                                canDeleteMessage = false,
                                null, // hasAttachment
                                false, // isSystemMessage
                                null, //isMentioned
                                false,
                                references= T.references.map { T -> T.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                                isChecked = selectedItems.contains(T.primary),
                                isUnread = !T.isRead// почему дабл
                            )
                        })

                        count = 0
                        for (i in 0 until list.size) {
                            if (list[i].isUnread) count++
                        }
                        messageList = list
                        messageList.sort()
                        withContext(Dispatchers.Main) {
                            _messages.value = messageList
                            _unreadCount.value = count
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun initChatDataListener(chatId: String) {
        val request = realm.query(LastChatsStorageItem::class, "primary = '$chatId'").find()
        val lastChatsFlow = request.asFlow()
        job = viewModelScope.launch(Dispatchers.IO) {
            lastChatsFlow.collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        val chat = if (changes.list.isNotEmpty()) changes.list.first() else null
                        withContext(Dispatchers.Main) {
                            _chat.value = chat?.toChatListDto()
                            if (chat != null) {
                                _muteExpired.value = chat.muteExpired
                                _opponentName.value = chat.toChatListDto().getChatName()
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun getDraft(id: String): String? {
        var drafted: String? = null
        viewModelScope.launch {
            realm.writeBlocking {
                val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                drafted = item?.draftMessage
            }
        }
        return drafted
    }

    fun getContactId(id: String): String? {
        var contactPrimary: String? = null
        viewModelScope.launch {
            realm.writeBlocking {
                val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                contactPrimary = item?.rosterItem?.primary
            }
        }
        return contactPrimary
    }

    fun getChat(chatId: String): ChatListDto? {
        var chatListDto: ChatListDto? = null
        realm.writeBlocking {
            val chat =
                this.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
            if (chat != null) chatListDto = chat.toChatListDto()
        }
        return chatListDto
    }

    fun getMessageList(chatId: String) {
        val lastChatsStorageItem =
            realm.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
        val owner = lastChatsStorageItem?.owner
        val opponent = lastChatsStorageItem?.jid
        viewModelScope.launch(Dispatchers.IO) {
            val realmList =
                realm.query(
                    MessageStorageItem::class,
                    "owner == '$owner' && opponent = '$opponent'"
                ).find()
            val list = ArrayList<MessageDto>()
            list.addAll(realmList.map { T ->
                MessageDto(
                    T.primary,
                    T.outgoing,
                    T.owner,
                    T.opponent,
                    T.body,
                    MessageSendingState.Read,
                    T.sentDate,
                    editTimestamp = T.editDate,
                    MessageDisplayType.Text,
                    true,
                    false,
                    null, // hasAttachment
                    false, // isSystemMessage
                    null, //isMentioned
                    false,
                    references= T.references.map { T -> T.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                    isChecked = selectedItems.contains(T.primary),
                    isUnread = !T.isRead
                )

            })

            var count = 0
            for (i in 0 until list.size) {
                if (list[i].isUnread) count++
            }
            messageList = list
            messageList.sort()
            withContext(Dispatchers.Main) {
                _messages.value = messageList
                _unreadCount.value = count
            }
        }
    }

    fun getAccount(): AccountDto? {
        var account: AccountDto? = null
        realm.writeBlocking {
            val acc = this.query(com.xabber.data_base.models.account.AccountStorageItem::class).first().find()
            account = if (acc != null) acc.toAccountDto() else null
        }
        return account
    }

    fun insertMessage(chatId: String, messageDto: MessageDto) {
            val rreferences = realmListOf<MessageReferenceStorageItem>()
            realm.writeBlocking {
                for (i in 0 until messageDto.references.size) {
                    val ref = this.copyToRealm(MessageReferenceStorageItem().apply {
                        primary = messageDto.references[i].id + "${System.currentTimeMillis()}"
                        uri = messageDto.references[i].uri
                        mimeType = messageDto.references[i].mimeType
                        isImage = messageDto.references[i].isImage
                        isGeo = messageDto.references[i].isGeo
                        latitude = messageDto.references[i].latitude
                        longitude = messageDto.references[i].longitude
                    })
                    rreferences.add(ref)
                }

                val message = this.copyToRealm(MessageStorageItem().apply {
                    primary = messageDto.primary
                    owner = messageDto.owner
                    opponent = messageDto.opponentJid
                    body = messageDto.messageBody
                    date = messageDto.sentTimestamp
                    sentDate = messageDto.sentTimestamp
                    editDate = messageDto.editTimestamp
                    outgoing = messageDto.isOutgoing
                    isRead = !messageDto.isUnread
                    references = rreferences
                    conversationType_ = ConversationType.Channel.toString()
                })
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
                item?.lastMessage = message
                item?.messageDate = message.date
//                var oldValue = item?.unread ?: 0
//                oldValue++
//                item?.unread = if (messageDto.isOutgoing || isReaded) 0 else oldValue
                item?.lastMessage?.outgoing = messageDto.isOutgoing
                if (item != null) {
                    if (!messageDto.isOutgoing && item.muteExpired <= 0) item.isArchived = false
                }
                Log.d("yyy", "item unread = ${item?.unread}")
            }
    }

    fun selectMessage(primary: String, checked: Boolean) {
        if (checked) {
            selectedItems.add(primary)
        } else {
            selectedItems.remove(primary)
        }
        _selectedCount.value = selectedItems.size
    }

    fun clearAllSelected() {
        selectedItems.clear()
    }

    fun isOutgoing(): Boolean {
        var out = false
        if (selectedItems.size == 1) {
            val l = arrayListOf<String>()
            l.addAll(selectedItems)
            val primary = l[0]
            realm.writeBlocking {
                val item = realm.query(MessageStorageItem::class, "primary = '$primary'").first()
                    .find()
                if (item != null && item.outgoing) out = true
            }
        }
        return out
    }

    fun deleteMessage(primary: String, forAll: Boolean) {
        test++
        Log.d("iii", "test = $test")
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val deletedMessage =
                    realm.query(MessageStorageItem::class, "primary = '$primary'").first()
                        .find()
                if (deletedMessage != null) findLatest(deletedMessage)?.let { delete(it) }
            }
        }
        if (forAll) {

            // запрос на сервер удалить сообщение
        }
    }

    fun deleteMessages(forAll: Boolean) {
        val selected = arrayListOf<String>()
        selected.addAll(selectedItems)
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                for (i in 0 until selected.size) {
                    val primary = selected[i]
                    val deletedMessage =
                        realm.query(MessageStorageItem::class, "primary = '$primary'").first()
                            .find()
                    if (deletedMessage != null) findLatest(deletedMessage)?.let { delete(it) }
                }
            }
        }
        if (forAll) {
            // запрос на сервер удалить сообщение
        }
    }

    fun getSelectedText(): String {
        var text = ""
        val selected = arrayListOf<String>()
        selected.addAll(selectedItems)

        realm.writeBlocking {
            for (i in 0 until selected.size) {
                val primary = selected[i]
                val message =
                    realm.query(MessageStorageItem::class, "primary = '$primary'").first()
                        .find()
                if (message != null) text += "${message}${message.body} \n"
                Log.d("iii", " inter $text")
            }
        }
        Log.d("iii", "$text")
        return text
    }

    fun editMessage(primary: String, newBody: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val editableMessage: MessageStorageItem? =
                    this.query(MessageStorageItem::class, "primary = '$primary'").first().find()
                editableMessage?.body = newBody
                editableMessage?.editDate = System.currentTimeMillis()
            }
        }
    }

    fun clearHistory(chatId: String, opponentJid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val messages =
                    this.query(MessageStorageItem::class, "opponent = '$opponentJid'").find()
                delete(messages)

                val chat =
                    this.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
                chat?.lastMessage = null
                chat?.lastPosition = ""
                chat?.unread = 0
            }
        }
    }

    fun deleteChat(id: String) {
        job?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                if (item != null) findLatest(item)?.let { delete(item) }
            }
        }
    }

    fun insertChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val c = copyToRealm(LastChatsStorageItem().apply {
                    primary = id
                })
            }
        }
    }

    fun saveDraft(id: String, draft: String?) {
        realm.writeBlocking {
            val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
            if (item != null) {
                findLatest(item).also {
                    val oldDraft = it?.draftMessage
                    if (oldDraft != draft) {
                        it?.draftMessage = draft
                        if (!draft.isNullOrEmpty())
                            it?.messageDate = System.currentTimeMillis()
                        else {
                            it?.messageDate = it?.lastMessage?.date ?: 0
                        }
                    }
                }
            }
        }
    }

    fun setMute(id: String, mute: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                Log.d("item", "$item")
                item?.muteExpired = mute
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // realm.close()
    }

    fun saveLastPosition(id: String, savedPosition: String) {
        realm.writeBlocking {
            val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
            if (item != null) {
                findLatest(item).also {
                    it?.lastPosition = savedPosition
                }
            }
        }
    }


    fun getPositionMessage(lastPosition: String): Int {
        Log.d("mmm", "messageList = $messageList")

        messageList.sort()
        var pos = 0
        for (i in 0 until messageList.size) {
            if (messageList[i].primary == lastPosition) pos = i

        }
        return pos
    }

    fun lastPositionPrimary(id: String): String {
        var lastPosition = ""
        realm.writeBlocking {
            val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
            if (item != null) lastPosition = item.lastPosition
        }
        return lastPosition


    }

    fun getSelectedMessageText(): String {
        var text = ""
        val selected = arrayListOf<String>()
        selected.addAll(selectedItems)
        val id = selected[0]
        realm.writeBlocking {
            val item = this.query(MessageStorageItem::class, "primary = '$id'").first().find()
            if (item != null) text = item.body
        }
        return text
    }

    fun getMessageId(): String {
        val selected = arrayListOf<String>()
        selected.addAll(selectedItems)
        return selected[0]
    }

    fun setUnread(id: String) {
//        viewModelScope.launch(Dispatchers.IO) {
//            realm.writeBlocking {
//                val mes = this.query(MessageStorageItem::class, "primary = '$id").first().find()
//                mes?.isRead = true
//            }
//        }
    }

    fun getMessage(primary: String? = null): MessageDto? {
        val selected = arrayListOf<String>()
        selected.addAll(selectedItems)
        val id = primary ?: selected[0]
        var message: MessageDto? = null
        realm.writeBlocking {
            val item = this.query(MessageStorageItem::class, "primary = '$id'").first().find()
            if (item != null) message = MessageDto(
                item.primary,
                item.outgoing,
                item.owner,
                item.opponent,
                item.body,
                MessageSendingState.Sent,
                item.sentDate,
                editTimestamp = item.editDate,
                MessageDisplayType.Text,
                true,
                false,
                null, // hasAttachment
                false, // isSystemMessage
                null, //isMentioned
                false,

                isChecked = selectedItems.contains(item.primary)
            )
        }
        return message
    }

    fun markAllMessageUnread(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val unreadMessages = this.query(MessageStorageItem::class, "isRead = false").find()
                unreadMessages.forEach { it.isRead = true }
                val chat =
                    this.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
                chat?.unread = 0
            }
        }

    }

    fun getForwardMessagesText(): String {
        Log.d("yyy", "selectedItems = $selectedItems")
        val selected = arrayListOf<String>()
        selected.addAll(selectedItems)
        var text = ""
        realm.writeBlocking {
            for (i in 0 until selected.size) {
                val id = selected[i]
                val item = this.query(MessageStorageItem::class, "primary = '$id'").first().find()
                if (item != null) text += if (item.outgoing) item.owner else item.opponent + "\n" + item.body
            }
        }

        return text
    }

    val token = "554cbba7-c31a-4368-ac63-ad474de54151"
    val baseUrl = "https://gallery.xmpp.redsolution.com/api/v1/files/"
    var call: Call<ResponseBody>? = null
    fun sendFile(file: File) {
        Log.d("ffff", "${file.length()}")
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {

            val interceptor = HttpLoggingInterceptor()
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            val client: OkHttpClient = OkHttpClient.Builder()
                .addInterceptor(interceptor) //.addInterceptor(REWRITE_CACHE_CONTROL_INTERCEPTOR)
                .addNetworkInterceptor(Interceptor { chain ->
                    val request: Request =
                        chain.request().newBuilder() // .addHeader(Constant.Header, authToken)
                            .build()
                    chain.proceed(request)
                }).build()


            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
            val apiService = retrofit.create(PostFileApi::class.java)
            val requestBodyFile =
                file.asRequestBody("multipart/form-data".toMediaTypeOrNull())

            Log.d("resss", "$requestBodyFile, ${RequestBody}")
            val requestBodyMediaType =
                "media_type".toRequestBody("text/plain".toMediaTypeOrNull())

            val filePart = MultipartBody.Part.createFormData("file", file.name, requestBodyFile)
            call = apiService.uploadFile(
                "Bearer $token",
                filePart,
                "text"
            )


            call!!.enqueue(object : Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody>,
                    response: Response<ResponseBody>
                ) {
                    Log.d("response", "responce code ${response.code()}")
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    t.printStackTrace()
                }
            })
        }

    }


    val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
        //   Log.d("ttttt","${call!!.request().body}")
        throwable.message
        Log.d("retrofit", "yyyyy" + throwable.printStackTrace().toString())
    }
}

