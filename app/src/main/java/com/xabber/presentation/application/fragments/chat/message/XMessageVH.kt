package com.xabber.presentation.application.fragments.chat.message

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.QuoteSpan
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.StyleRes
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.models.dto.MessageDto
import com.xabber.models.dto.MessageVhExtraData
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.application.fragments.chat.ReferenceRealmObject
import com.xabber.presentation.application.fragments.chat.audio.VoiceManager
import com.xabber.presentation.custom.CorrectlyTouchEventTextView
import com.xabber.presentation.custom.CustomFlexboxLayout
import com.xabber.utils.StringUtils.getDateStringForMessage
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

open class XMessageVH(
    itemView: View,
    private val listener: MessageClickListener,
    private val longClickListener: MessageLongClickListener,
    private val fileListener: FileListener?,
    @StyleRes appearance: Int
) : XBasicMessageVH(itemView, appearance), View.OnClickListener,
    //FileListListener,
    View.OnLongClickListener {

    var isUnread = false
    var messageId: String? = null

 //   private val subscriptions = CompositeSubscription()

    protected val messageTime: TextView = itemView.findViewById(R.id.message_time)
    protected val messageHeader: TextView = itemView.findViewById(R.id.message_sender_tv)
    protected val messageBalloon: View = itemView.findViewById(R.id.message_balloon)
    protected val messageShadow: View = itemView.findViewById(R.id.message_shadow)
    protected val statusIcon: ImageView = itemView.findViewById(R.id.message_status_icon)
    protected val messageInfo: View = itemView.findViewById(R.id.message_info)
    private val flexboxLayout: CustomFlexboxLayout = itemView.findViewById(R.id.message_flex_layout)
    protected val forwardedMessagesRV: RecyclerView = itemView.findViewById(R.id.forwardedRecyclerView)
    protected val messageFileInfo: TextView = itemView.findViewById(R.id.message_file_info)
    protected val progressBar: ProgressBar = itemView.findViewById(R.id.message_progress_bar)
    private val rvFileList: RecyclerView = itemView.findViewById(R.id.file_list_rv)
    private val imageGridContainer: FrameLayout = itemView.findViewById(R.id.image_grid_container_fl)

    //todo there are duplicated views! (or else triplicated!)
    private val messageStatusLayout: LinearLayoutCompat = itemView.findViewById(R.id.message_bottom_status)
    protected val bottomMessageTime: TextView = messageStatusLayout.findViewById(R.id.message_time)
    protected var bottomStatusIcon: ImageView = messageStatusLayout.findViewById(R.id.message_status_icon)

    private val uploadProgressBar: ProgressBar? = itemView.findViewById(R.id.uploadProgressBar)
    private val ivCancelUpload: ImageButton? = itemView.findViewById(R.id.ivCancelUpload)

    private var imageCount = 0
    private var fileCount = 0

    interface FileListener {
        fun onImageClick(messagePosition: Int, attachmentPosition: Int, messageUID: String?)
        fun onFileClick(messagePosition: Int, attachmentPosition: Int, messageUID: String?)
        fun onVoiceClick(
            messagePosition: Int,
            attachmentPosition: Int,
            attachmentId: String?,
            messageUID: String?,
            timestamp: Long?
        )

        fun onFileLongClick(referenceRealmObject: ReferenceRealmObject?, caller: View?)
        fun onDownloadCancel()
        fun onUploadCancel()
        fun onDownloadError(error: String?)
    }

    interface MessageClickListener {
        fun onMessageClick(caller: View, position: Int)
    }

    interface MessageLongClickListener {
        fun onLongMessageClick(position: Int)
    }

    open fun bind(messageDto: MessageDto, vhExtraData: MessageVhExtraData) {
//        GlobalScope.launch { bottomMessageTime.text = "" }
//        val chat = ChatManager.getInstance().getChat(
//            messageRealmObject.account, messageRealmObject.user
//        )

        // groupchat
//        if (vhExtraData.groupMember != null) {
//            if (!vhExtraData.groupMember.isMe) {
//                val user = vhExtraData.groupMember
//                messageHeader.text = user.nickname
//                messageHeader.setTextColor(
//                    ColorManager.changeColor(
//                        ColorGenerator.MATERIAL.getColor(user.nickname),
//                        0.8f
//                    )
//                )
//                messageHeader.visibility = View.VISIBLE
//            } else if (chat is GroupChat && chat.privacyType === GroupPrivacyType.INCOGNITO) {
//                val user = vhExtraData.groupMember
//                messageHeader.text = user.nickname
//                messageHeader.setTextColor(
//                    ColorManager.changeColor(
//                        ColorGenerator.MATERIAL.getColor(user.nickname),
//                        0.8f
//                    )
//                )
          //      messageHeader.visibility = View.VISIBLE
       //     } else {
     //           messageHeader.visibility = View.GONE
     //       }
   //     } else {
      //      messageHeader.visibility = View.GONE
    //    }

//        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
//            if (SettingsManager.interfaceTheme() == SettingsManager.InterfaceTheme.dark) {
//                messageTextTv.setTextColor(itemView.context.getColor(R.color.grey_200))
//            } else {
//                messageTextTv.setTextColor(itemView.context.getColor(R.color.black))
//            }
//        }

        // Added .concat("&zwj;") and .concat(String.valueOf(Character.MIN_VALUE)
        // to avoid click by empty space after ClickableSpan
        // Try to decode to avoid ugly non-english links
//        if (messageRealmObject.markupText != null && messageRealmObject.markupText.isNotEmpty()) {
//            val spannable = Html.fromHtml(
//                messageRealmObject.markupText.trim { it <= ' ' }.replace("\n", "<br/>") + "&zwj;",
//                null,
//                ClickTagHandler(
//                    itemView.context, messageRealmObject.account
//                )
//            ) as SpannableStringBuilder
//            val color: Int = if (SettingsManager.interfaceTheme() == SettingsManager.InterfaceTheme.light) {
//                ColorManager.getInstance().accountPainter.getAccountMainColor(
//                    messageRealmObject.account
//                )
//            } else {
//                ColorManager.getInstance().accountPainter.getAccountSendButtonColor(
//                    messageRealmObject.account
//                )
//            }
//            modifySpannableWithCustomQuotes(
//                spannable,
//                itemView.context.resources.displayMetrics,
//                color
//            )
//            messageTextTv.setText(spannable, TextView.BufferType.SPANNABLE)
//                messageTextTv.setText(
//                    getDecodedSpannable(messageRealmObject.text.trim { it <= ' ' } + Character.MIN_VALUE.toString()),
//                    TextView.BufferType.SPANNABLE
//                )
//

        if (messageTextTv.text.isNotEmpty()) {
            messageStatusLayout.visibility = View.GONE
        }

//        if (messageRealmObject.hasReferences() || messageRealmObject.hasForwardedMessages()) {
//            flexboxLayout.layoutParams = LinearLayout.LayoutParams(
//                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
//            )
//        } else {
            flexboxLayout.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
    //    }

        messageTextTv.movementMethod = CorrectlyTouchEventTextView.LocalLinkMovementMethod

        // set unread status
    //    isUnread = vhExtraData.isUnread

        // set date
        needDate = vhExtraData.isNeedDate
        date = getDateStringForMessage(messageDto.sentTimestamp)
        if (!vhExtraData.isNeedName) {
            messageHeader.visibility = View.GONE
        }

//        if (messageRealmObject.text.isNullOrEmpty()
//            && messageRealmObject.referencesRealmObjects.none { it.isImage }
//            && messageRealmObject.messageStatus != MessageStatus.UPLOADING
//        ) {
//            messageStatusLayout.visibility = View.VISIBLE
//        }  else {
//            messageStatusLayout.visibility = View.GONE
//        }

        // setup CHECKED
        if (vhExtraData.isChecked) {
            itemView.setBackgroundColor(
                itemView.context.resources.getColor(R.color.message_selected)
            )
        } else {
            itemView.background = null
        }

        setupTime(messageDto)
        setupReferences(messageDto, vhExtraData)
    }

    protected fun setupTime(messageDto: MessageDto) {
        var time = getTimeText(Date(messageDto.sentTimestamp))
//        messageRealmObject.delayTimestamp?.let {
//            val delay = itemView.context.getString(
//                if (messageRealmObject.isIncoming) R.string.chat_delay else R.string.chat_typed,
//                getTimeText(Date(it))
//            )
//            time += " ($delay)"
//        }
//        messageRealmObject.editedTimestamp?.let {
//            time += itemView.context.getString(
//                R.string.edited,
//                getTimeText(Date(it))
//            )
//        }
        messageTime.text = time
        bottomMessageTime.text = time
    }

    private fun setupReferences(messageDto: MessageDto, vhExtraData: MessageVhExtraData) {
        rvFileList.visibility = View.GONE
        imageGridContainer.removeAllViews()
        imageGridContainer.visibility = View.GONE
//        if (messageRealmObject.hasReferences()) {
//            setUpImage(messageRealmObject, vhExtraData)
//            setUpFile(messageRealmObject.referencesRealmObjects, vhExtraData)
//            setupNonExternalGeo(messageRealmObject)
//        }
    }

    private fun setupNonExternalGeo(messageRealmObject: MessageDto){
//        messageRealmObject.referencesRealmObjects?.firstOrNull { it.isGeo }?.let {
//
//
//            itemView.findViewById<RelativeLayout>(R.id.include_non_external_geolocation).apply {
//                if (SettingsManager.useExternalLocation() && !it.filePath.isNullOrEmpty()) {
//                    visibility = View.GONE
//                    return@let
//                }
//                if (!it.filePath.isNullOrEmpty()) {
//                    visibility = View.GONE
//                    return@let
//                }
//
//                visibility = View.VISIBLE
//
//                setOnClickListener { _ ->
//                    context.startActivity(
//                        Intent().apply {
//                            action = Intent.ACTION_VIEW
//                            data = Uri.parse("geo:${it.latitude},${it.longitude}?q=${it.latitude},${it.longitude}")
//                        }
//                    )
//                }
//                val coordFormatString = "%.4f"
//                findViewById<TextView>(R.id.location_coordinates).text = "${coordFormatString.format(it.longitude)}, ${coordFormatString.format(it.latitude)}"
//            }
//        }
    }

    private fun setUpImage(message: MessageDto, messageVhExtraData: MessageVhExtraData) {
//        if (!SettingsManager.connectionLoadImages()) {
//            return
//        }
//        message.referencesRealmObjects
//            ?.filter { it.isImage }
//            ?.also { imageCount = it.size }
//            ?.takeIf { it.isNotEmpty() }
//            ?.let {
//                RealmList<ReferenceRealmObject>().apply {
//                    addAll(it)
//                }
//            }
//            ?.let {
//                val gridBuilder = ImageGrid()
//                val imageGridView = gridBuilder.inflateView(imageGridContainer, it.size)
//                gridBuilder.bindView(imageGridView, message, this, messageVhExtraData) { v: View ->
//                    onLongClick(v)
//                    true
//                }
//                imageGridContainer.addView(imageGridView)
//                imageGridContainer.visibility = View.VISIBLE
//            }
    }

//    private fun setUpFile(
//        referenceRealmObjects: RealmList<ReferenceRealmObject>, vhExtraData: MessageVhExtraData
//    ) {
//        referenceRealmObjects
//            .filter { !it.isImage && !it.isGeo }
//            .also { fileCount = it.size }
//            .takeIf { it.isNotEmpty() }
//            ?.let {
//                RealmList<ReferenceRealmObject>().apply { addAll(it) }
//            }
//            ?.let {
//                rvFileList.apply {
//                    layoutManager = LinearLayoutManager(itemView.context)
//                    adapter = FilesAdapter(it, vhExtraData.mainMessageTimestamp, this@MessageVH)
//                    visibility = View.VISIBLE
//                }
//            }
//    }

    /** File list Listener  */
//    override fun onFileClick(attachmentPosition: Int) {
//        val messagePosition = adapterPosition
//        if (messagePosition == RecyclerView.NO_POSITION) {
//           // LogManager.w(this, "onClick: no position")
//            return
//        }
//        fileListener?.onFileClick(messagePosition, attachmentPosition, messageId)
//    }

//    override fun onVoiceClick(
//        attachmentPosition: Int,
//        attachmentId: String,
//        saved: Boolean,
//        mainMessageTimestamp: Long
//    ) {
//        val messagePosition = absoluteAdapterPosition
//        if (messagePosition == RecyclerView.NO_POSITION) {
//            Log.d("this", "onClick: no position")
//            return
//        }
//        if (!saved) {
//            fileListener?.onVoiceClick(
//                messagePosition,
//                attachmentPosition,
//                attachmentId,
//                messageId,
//                mainMessageTimestamp
//            )
//        } else {
//            VoiceManager.getInstance().voiceClicked(
//                messageId, attachmentPosition, mainMessageTimestamp
//            )
//        }
//    }

//    override fun onVoiceProgressClick(
//        attachmentPosition: Int,
//        attachmentId: String,
//        timestamp: Long,
//        current: Int,
//        max: Int
//    ) {
//        val messagePosition = adapterPosition
//        if (messagePosition == RecyclerView.NO_POSITION) {
//            Log.d("this", "onClick: no position")
//            return
//        }
//        VoiceManager.getInstance().seekAudioPlaybackTo(attachmentId, timestamp, current, max)
//    }

//    override fun onFileLongClick(referenceRealmObject: ReferenceRealmObject, caller: View) {
//        fileListener?.onFileLongClick(referenceRealmObject, caller)
//    }
//
//    override fun onDownloadCancel() {
//        fileListener?.onDownloadCancel()
//    }
//
//    override fun onDownloadError(error: String) {
//        fileListener?.onDownloadError(error)
//    }

    override fun onClick(v: View) {
        val adapterPosition = adapterPosition
        if (adapterPosition == RecyclerView.NO_POSITION) {
            //LogManager.w(this, "onClick: no position")
            return
        }
        when (v.id) {
            R.id.ivImage0 -> fileListener?.onImageClick(adapterPosition, 0, messageId)
            R.id.ivImage1 -> fileListener?.onImageClick(adapterPosition, 1, messageId)
            R.id.ivImage2 -> fileListener?.onImageClick(adapterPosition, 2, messageId)
            R.id.ivImage3 -> fileListener?.onImageClick(adapterPosition, 3, messageId)
            R.id.ivImage4 -> fileListener?.onImageClick(adapterPosition, 4, messageId)
            R.id.ivImage5 -> fileListener?.onImageClick(adapterPosition, 5, messageId)
            R.id.ivCancelUpload -> fileListener?.onUploadCancel()
            else -> listener.onMessageClick(messageBalloon, adapterPosition)
        }
    }

    /** Upload progress subscription  */
//    protected fun subscribeForUploadProgress() {
//        fun setUpProgress(progressData: HttpFileUploadManager.ProgressData?) {
//            if (progressData != null && messageId == progressData.messageId) {
//                if (progressData.isCompleted) {
//                    showProgress(false)
//                    showFileProgressModified(rvFileList, fileCount, fileCount)
//                    showProgressModified(false, 0, imageCount)
//                } else if (progressData.error != null) {
//                    showProgress(false)
//                    showFileProgressModified(rvFileList, fileCount, fileCount)
//                    showProgressModified(false, 0, imageCount)
//                    fileListener?.onDownloadError(progressData.error)
//                } else {
//                    showProgress(true)
//                    messageFileInfo.setText(R.string.message_status_uploading)
//                    if (progressData.progress <= imageCount) {
//                        showProgressModified(true, progressData.progress - 1, imageCount)
//                    }
//                    if (progressData.progress - imageCount <= fileCount) {
//                        showFileProgressModified(
//                            rvFileList,
//                            progressData.progress - imageCount,
//                            progressData.fileCount - imageCount
//                        )
//                    }
//                }
//            } else {
//                showProgress(false)
//                showFileProgressModified(rvFileList, fileCount, fileCount)
//                showProgressModified(false, 0, imageCount)
//            }
//        }

//        subscriptions.add(
//            HttpFileUploadManager.getInstance()
//                .subscribeForProgress()
//                .doOnNext { progressData -> setUpProgress(progressData) }
//                .subscribe()
//        )
  //  }

    protected fun unsubscribeAll() {
     //   subscriptions.clear()
    }

    private fun showProgress(show: Boolean) {
        messageFileInfo.visibility = if (show) View.VISIBLE else View.GONE
        messageTime.visibility = if (show) View.GONE else View.VISIBLE
        bottomMessageTime.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showFileProgressModified(view: RecyclerView, startAt: Int, endAt: Int) {
        for (i in 0 until startAt) {
            showFileUploadProgress(view.getChildAt(i), false)
        }
        for (j in startAt.coerceAtLeast(0) until endAt) {
            showFileUploadProgress(view.getChildAt(j), true)
        }
    }

    private fun showFileUploadProgress(view: View, show: Boolean) {
        view.findViewById<ProgressBar>(R.id.uploadProgressBar)?.visibility =
            if (show) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }


    //todo yep, these methods must be in ImageGrid 🗿
//    private fun showProgressModified(show: Boolean, current: Int, last: Int) {
//        fun getProgressView(view: View, index: Int): ProgressBar {
//            return when (index) {
//                1 -> view.findViewById(R.id.uploadProgressBar1)
//                2 -> view.findViewById(R.id.uploadProgressBar2)
//                3 -> view.findViewById(R.id.uploadProgressBar3)
//                4 -> view.findViewById(R.id.uploadProgressBar4)
//                5 -> view.findViewById(R.id.uploadProgressBar5)
//                else -> view.findViewById(R.id.uploadProgressBar0)
   //         }
  //      }

     //   fun getImageShadow(view: View, index: Int): ImageView {
//            return when (index) {
//                1 -> view.findViewById(R.id.ivImage1Shadow)
//                2 -> view.findViewById(R.id.ivImage2Shadow)
//                3 -> view.findViewById(R.id.ivImage3Shadow)
//                4 -> view.findViewById(R.id.ivImage4Shadow)
//                5 -> view.findViewById(R.id.ivImage5Shadow)
//                else -> view.findViewById(R.id.ivImage0Shadow)
//            }
    //    }

//        if (show) {
//            for (i in 0 until current) {
//                getProgressView(imageGridContainer, i).visibility = View.GONE
//                getImageShadow(imageGridContainer, i).visibility = View.GONE
//            }
//            for (j in current until last) {
//                getProgressView(imageGridContainer, j).visibility = View.VISIBLE
//                getImageShadow(imageGridContainer, j).visibility = View.VISIBLE
//            }
//        } else {
//            for (i in 0 until last) {
//                getProgressView(imageGridContainer, i).visibility = View.GONE
//                getImageShadow(imageGridContainer, i).visibility = View.GONE
//            }
//        }
//    }

//    fun setupForwarded(messageRealmObject: MessageRealmObject, vhExtraData: MessageVhExtraData) {
//        val forwardedIDs = messageRealmObject.forwardedIdsAsArray
//        if (!forwardedIDs.contains(null)) {
//            DatabaseManager.getInstance().defaultRealmInstance
//                .where(MessageRealmObject::class.java)
//                .`in`(MessageRealmObject.Fields.PRIMARY_KEY, forwardedIDs)
//                .findAll()
//                .sort(MessageRealmObject.Fields.TIMESTAMP, Sort.ASCENDING)
//                .takeIf { it.isNotEmpty() }
//                ?.let { forwardedMessages ->
//                    forwardedMessagesRV.apply {
//                        layoutManager = LinearLayoutManager(itemView.context)
//                        adapter = ForwardedAdapter(forwardedMessages, vhExtraData)
//                        forwardedMessagesRV.addItemDecoration(object : RecyclerView.ItemDecoration() {
//                            override fun getItemOffsets(
//                                outRect: Rect,
//                                view: View,
//                                parent: RecyclerView,
//                                state: RecyclerView.State
//                            ) {
//                                super.getItemOffsets(outRect, view, parent, state)
//                                if (parent.getChildLayoutPosition(view) != 0) {
//                                    outRect.top = 12
//                                }
//                            }
//                        })
//                        visibility = View.VISIBLE
//                    }
//                }
//        }
//    }

    override fun onLongClick(v: View): Boolean {
        val adapterPosition = adapterPosition
        return if (adapterPosition == RecyclerView.NO_POSITION) {
        //    LogManager.w(this, "onClick: no position")
            false
        } else {
            longClickListener.onLongMessageClick(adapterPosition)
            true
        }
    }

    protected fun setUpMessageBalloonBackground(view: View, colorList: ColorStateList?) {
            view.background.setTintList(colorList)
    }

    private fun modifySpannableWithCustomQuotes(
        spannable: SpannableStringBuilder, displayMetrics: DisplayMetrics, color: Int
    ) {
        for (span in spannable.getSpans(0, spannable.length, QuoteSpan::class.java).reversed()){
            var spanEnd = spannable.getSpanEnd(span)
            var spanStart = spannable.getSpanStart(span)

            spannable.removeSpan(span)

            if (spanEnd < 0 || spanStart < 0) {
                break
            }

            var newlineCount = 0
            if ('\n' == spannable[spanEnd]) {
                newlineCount++
                if (spanEnd + 1 < spannable.length && '\n' == spannable[spanEnd + 1]) {
                    newlineCount++
                }
                if ('\n' == spannable[spanEnd - 1]) {
                    newlineCount++
                }
            }
            when (newlineCount) {
                3 -> {
                    spannable.delete(spanEnd - 1, spanEnd + 1)
                    spanEnd -= 2
                }
                2 -> {
                    spannable.delete(spanEnd, spanEnd + 1)
                    spanEnd--
                }
            }

            if (spanStart > 1 && '\n' == spannable[spanStart - 1]) {
                if ('\n' == spannable[spanStart - 2]) {
                    spannable.delete(spanStart - 2, spanStart - 1)
                    spanStart--
                }
            }

//            spannable.setSpan(
//                CustomQuoteSpan(color, displayMetrics),
//                spanStart,
//                spanEnd,
//                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
//            )

            var current: Char
            var waitForNewLine = false
            var j = spanStart
            while (j < spanEnd) {
                if (j >= spannable.length) {
                    break
                }
                current = spannable[j]
                waitForNewLine =
                    if (waitForNewLine && current != '\n') {
                        j++
                        continue
                    } else {
                        false
                    }

                if (current == '>') {
                    spannable.delete(j, j + 1)
                    j--
                    waitForNewLine = true
                }
                j++
            }
        }
    }

    private fun getTimeText(timeStamp: Date): String {
        return DateFormat.getTimeFormat(XabberApplication.applicationContext()).format(timeStamp)
    }

    init {
        ivCancelUpload?.setOnClickListener(this)
        itemView.setOnClickListener(this)
        itemView.setOnLongClickListener(this)
    }

}