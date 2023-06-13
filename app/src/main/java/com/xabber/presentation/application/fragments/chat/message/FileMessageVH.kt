package com.xabber.presentation.application.fragments.chat.message

//class FileMessageVH(
//    itemView: View, messageListener: MessageClickListener?,
//    longClickListener: MessageLongClickListener?,
//    private val listener: FileListener, appearance: Int
//) : XMessageVH(itemView, messageListener!!, longClickListener!!, null, appearance) {
//    val messageImage: ImageView?
//    val fileLayout: View
//    val rvFileList: RecyclerView
//    val imageGridContainer: FrameLayout
//    val uploadProgressBar: ProgressBar?
//    val ivCancelUpload: ImageButton?
//
//    interface FileListener {
//        fun onImageClick(messagePosition: Int, attachmentPosition: Int, messageUID: String?)
//        fun onFileClick(messagePosition: Int, attachmentPosition: Int, messageUID: String?)
//        fun onFileLongClick(mes: MessageReferenceStorageItem?, caller: View?)
//        fun onDownloadCancel()
//        fun onUploadCancel()
//        fun onDownloadError(error: String?)
//    }
//
//    init {
//        messageImage = itemView.findViewById(R.id.message_image)
//        fileLayout = itemView.findViewById(R.id.fileLayout)
//        rvFileList = itemView.findViewById(R.id.rvFileList)
//        imageGridContainer = itemView.findViewById(R.id.imageGridContainer)
//        uploadProgressBar = itemView.findViewById(R.id.uploadProgressBar)
//        ivCancelUpload = itemView.findViewById(R.id.ivCancelUpload)
//        ivCancelUpload?.setOnClickListener(this)
//        messageImage?.setOnClickListener(this)
//    }
//
//
//    protected fun setupImageOrFile(messageItem: MessageItem, context: Context) {
//        fileLayout.visibility = View.GONE
//        messageImage!!.visibility = View.GONE
//        imageGridContainer.removeAllViews()
//        imageGridContainer.visibility = View.GONE
//        if (messageItem.haveAttachments()) {
//            setUpImage(messageItem.getAttachments())
//            setUpFile(messageItem.getAttachments(), context)
//        } else if (messageItem.isImage()) {
//            prepareImage(messageItem, context)
//        }
//    }
//
//    private fun prepareImage(messageItem: MessageItem, context: Context) {
//        val filePath: String = messageItem.getFilePath()
//        val imageWidth: Int = messageItem.getImageWidth()
//        val imageHeight: Int = messageItem.getImageHeight()
//        val imageUrl: String = messageItem.getText()
//        val uniqueId: String = messageItem.getUniqueId()
//        setUpImage(filePath, imageUrl, uniqueId, imageWidth, imageHeight, context)
//    }
//
//    private fun setUpImage(attachments: RealmList<Attachment>) {
//        val gridBuilder = ImageGridBuilder()
//        if (!SettingsManager.connectionLoadImages()) return
//        val imageAttachments: RealmList<Attachment> = RealmList()
//        for (attachment in attachments) {
//            if (attachment.isImage()) imageAttachments.add(attachment)
//        }
//        if (imageAttachments.size() > 0) {
//            val imageGridView: View =
//                gridBuilder.inflateView(imageGridContainer, imageAttachments.size())
//            gridBuilder.bindView(imageGridView, imageAttachments, this)
//            imageGridContainer.addView(imageGridView)
//            imageGridContainer.visibility = View.VISIBLE
//        }
//    }
//
//    private fun setUpFile(attachments: RealmList<Attachment>, context: Context) {
//        val fileAttachments: RealmList<Attachment> = RealmList()
//        for (attachment in attachments) {
//            if (!attachment.isImage()) fileAttachments.add(attachment)
//        }
//        if (fileAttachments.size() > 0) {
//            val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(context)
//            rvFileList.layoutManager = layoutManager
//            val adapter = FilesAdapter(fileAttachments, this)
//            rvFileList.adapter = adapter
//            fileLayout.visibility = View.VISIBLE
//        }
//    }
//
//    private fun setUpImage(
//        imagePath: String?, imageUrl: String, uniqueId: String, imageWidth: Int?,
//        imageHeight: Int?, context: Context
//    ) {
//        if (!SettingsManager.connectionLoadImages()) return
//        if (imagePath != null) {
//            val result: Boolean = FileManager.loadImageFromFile(context, imagePath, messageImage)
//            if (result) {
//                messageImage!!.visibility = View.VISIBLE
//            } else {
//                val realm: Realm = MessageDatabaseManager.getInstance().getRealmUiThread()
//                realm.executeTransactionAsync(object : Transaction() {
//                    fun execute(realm: Realm) {
//                        val first: MessageItem = realm.where(MessageItem::class.java)
//                            .equalTo(MessageItem.Fields.UNIQUE_ID, uniqueId)
//                            .findFirst()
//                        if (first != null) {
//                            first.setFilePath(null)
//                        }
//                    }
//                })
//            }
//        } else {
//            val layoutParams = messageImage!!.layoutParams
//            if (imageWidth != null && imageHeight != null) {
//                FileManager.scaleImage(layoutParams, imageHeight, imageWidth)
//                Glide.with(context)
//                    .load(imageUrl)
//                    .listener(object : RequestListener<Drawable?> {
//                        override fun onLoadFailed(
//                            e: GlideException?, model: Any,
//                            target: Target<Drawable?>, isFirstResource: Boolean
//                        ): Boolean {
//                            messageImage.visibility = View.GONE
//                            return true
//                        }
//
//                        override fun onResourceReady(
//                            resource: Drawable?,
//                            model: Any,
//                            target: Target<Drawable?>,
//                            dataSource: DataSource,
//                            isFirstResource: Boolean
//                        ): Boolean {
//                            return false
//                        }
//                    })
//                    .into(messageImage)
//                messageImage.visibility = View.VISIBLE
//            } else {
//                Glide.with(context)
//                    .asBitmap()
//                    .load(imageUrl)
//                    .placeholder(R.drawable.ic_recent_image_placeholder)
//                    .error(R.drawable.ic_recent_image_placeholder)
//                    .into(object : CustomTarget<Bitmap?>() {
//                        override fun onLoadStarted(placeholder: Drawable?) {
//                            super.onLoadStarted(placeholder)
//                            messageImage.setImageDrawable(placeholder)
//                            messageImage.visibility = View.VISIBLE
//                        }
//
//                        override fun onLoadFailed(errorDrawable: Drawable?) {
//                            super.onLoadFailed(errorDrawable)
//                            messageImage.setImageDrawable(errorDrawable)
//                            messageImage.visibility = View.VISIBLE
//                        }
//
//                        override fun onResourceReady(
//                            resource: Bitmap,
//                            transition: Transition<in Bitmap>?
//                        ) {
//                            val width = resource.width
//                            val height = resource.height
//                            if (width <= 0 || height <= 0) {
//                                messageImage.visibility = View.GONE
//                                return
//                            }
//                            val realm: Realm =
//                                MessageDatabaseManager.getInstance().getRealmUiThread()
//                            realm.executeTransactionAsync(object : Transaction() {
//                                fun execute(realm: Realm) {
//                                    val first: MessageItem = realm.where(MessageItem::class.java)
//                                        .equalTo(MessageItem.Fields.UNIQUE_ID, uniqueId)
//                                        .findFirst()
//                                    if (first != null) {
//                                        first.setImageWidth(width)
//                                        first.setImageHeight(height)
//                                    }
//                                }
//                            })
//                            FileManager.scaleImage(layoutParams, height, width)
//                            messageImage.setImageBitmap(resource)
//                            messageImage.visibility = View.VISIBLE
//                        }
//
//                        override fun onLoadCleared(placeholder: Drawable?) {}
//                    })
//            }
//        }
//    }
//
//    /** File list Listener  */
//    fun onFileClick(attachmentPosition: Int) {
//        val messagePosition: Int = getAdapterPosition()
//        if (messagePosition == RecyclerView.NO_POSITION) {
//            LogManager.w(LOG_TAG, "onClick: no position")
//            return
//        }
//        listener.onFileClick(messagePosition, attachmentPosition, messageId)
//    }
//
//    fun onFileLongClick(attachment: Attachment?, caller: View?) {
//        listener.onFileLongClick(attachment, caller)
//    }
//
//    fun onDownloadCancel() {
//        listener.onDownloadCancel()
//    }
//
//    fun onDownloadError(error: String?) {
//        listener.onDownloadError(error)
//    }
//
//    override fun onClick(v: View) {
//        val adapterPosition: Int = getAdapterPosition()
//        if (adapterPosition == RecyclerView.NO_POSITION) {
//            LogManager.w(LOG_TAG, "onClick: no position")
//            return
//        }
//        when (v.id) {
//            R.id.ivImage0 -> listener.onImageClick(adapterPosition, 0, messageId)
//            R.id.ivImage1 -> listener.onImageClick(adapterPosition, 1, messageId)
//            R.id.ivImage2 -> listener.onImageClick(adapterPosition, 2, messageId)
//            R.id.ivImage3 -> listener.onImageClick(adapterPosition, 3, messageId)
//            R.id.ivImage4 -> listener.onImageClick(adapterPosition, 4, messageId)
//            R.id.ivImage5 -> listener.onImageClick(adapterPosition, 5, messageId)
//            R.id.message_image -> listener.onImageClick(adapterPosition, 0, messageId)
//            R.id.ivCancelUpload -> listener.onUploadCancel()
//            else -> super.onClick(v)
//        }
//    }
//
//    /** Upload progress subscription  */
//    protected fun subscribeForUploadProgress(context: Context) {
//        subscriptions.add(HttpFileUploadManager.getInstance().subscribeForProgress()
//            .doOnNext(object : Action1<HttpFileUploadManager.ProgressData?>() {
//                fun call(progressData: HttpFileUploadManager.ProgressData?) {
//                    setUpProgress(context, progressData)
//                }
//            }).subscribe()
//        )
//    }
//
//    protected fun unsubscribeAll() {
//        subscriptions.clear()
//    }
//
//    private fun setUpProgress(context: Context, progressData: HttpFileUploadManager.ProgressData?) {
//        if (progressData != null && messageId.equals(progressData.getMessageId())) {
//            if (progressData.isCompleted()) {
//                showProgress(false)
//            } else if (progressData.getError() != null) {
//                showProgress(false)
//                listener.onDownloadError(progressData.getError())
//            } else {
//                if (uploadProgressBar != null) uploadProgressBar.progress =
//                    progressData.getProgress()
//                if (messageFileInfo != null) messageFileInfo.text = context.getString(
//                    R.string.uploaded_files_count,
//                    progressData.getProgress() + "/" + progressData.getFileCount()
//                )
//                showProgress(true)
//            }
//        } else showProgress(false)
//    }
//
//    private fun showProgress(show: Boolean) {
//        if (uploadProgressBar != null) uploadProgressBar.visibility =
//            if (show) View.VISIBLE else View.GONE
//        if (ivCancelUpload != null) ivCancelUpload.visibility =
//            if (show) View.VISIBLE else View.GONE
//        if (messageFileInfo != null) messageFileInfo.visibility =
//            if (show) View.VISIBLE else View.GONE
//    }
//
//    companion object {
//        private val LOG_TAG = FileMessageVH::class.java.simpleName
//    }
//}
