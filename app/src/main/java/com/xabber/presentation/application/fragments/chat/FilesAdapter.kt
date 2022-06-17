package com.xabber.presentation.application.fragments.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.databinding.ItemAttachedFileBinding

class FilesAdapter : RecyclerView.Adapter<FileViewHolder>() {

    interface FileListener {
        fun onFileClick(position: Int)
        fun onVoiceClick(position: Int, attachmentId: String, saved: Boolean, timestamp: Long)
        fun onVoiceProgressClick(
            position: Int,
            attachmentId: String,
            timestamp: Long,
            current: Int,
            max: Int
        )

        fun onFileLongClick(caller: View)
        fun onDownloadCancel()
        fun onDownloadError(error: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        return FileViewHolder(
            ItemAttachedFileBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
//        val messageReferenceStorageItem = holder.absoluteAdapterPosition(position)
//        holder.attachmentId = referenceRealmObject.getUniqueId()
//        if (referenceRealmObject.isVoice()) {
//            holder.voiceMessage = true
//            holder.subscribeForAudioProgress()
//            val voiceText = StringBuilder()
//            voiceText.append(
//                Application.getInstance().getResources().getString(R.string.voice_message)
//            )
//            if (referenceRealmObject.getDuration() != null && referenceRealmObject.getDuration() !== 0) {
//                voiceText.append(
//                    java.lang.String.format(
//                        Locale.getDefault(),
//                        ", %s",
//                        DatesUtilsKt.getDurationStringForVoiceMessage(
//                            null,
//                            referenceRealmObject.getDuration()
//                        )
//                    )
//                )
//                val lp: RelativeLayout.LayoutParams =
//                    holder.fileInfoLayout.getLayoutParams() as RelativeLayout.LayoutParams
//                val width: Int = dipToPx(140, holder.fileInfoLayout.getContext())
//                if (referenceRealmObject.getDuration() < 10) {
//                    lp.width = width + dipToPx(
//                        6 * referenceRealmObject.getDuration(),
//                        holder.fileInfoLayout.getContext()
//                    )
//                } else {
//                    lp.width = width + dipToPx(60, holder.fileInfoLayout.getContext())
//                }
//                holder.fileInfoLayout.setLayoutParams(lp)
//            }
//            holder.tvFileName.setText(voiceText)
//            val size: Long = referenceRealmObject.getFileSize()
//            if (referenceRealmObject.getFilePath() != null) {
//                holder.tvFileName.setVisibility(View.GONE)
//                holder.tvFileSize.setText(
//                    if (referenceRealmObject.getDuration() != null && referenceRealmObject.getDuration() !== 0) DatesUtilsKt.getDurationStringForVoiceMessage(
//                        0L, referenceRealmObject.getDuration()
//                    ) else FileUtils.byteCountToDisplaySize(size ?: 0)
//                )
//                VoiceMessagePresenterManager.getInstance()
//                    .sendWaveDataIfSaved(referenceRealmObject.getFilePath(), holder.audioVisualizer)
//                holder.audioVisualizer.setVisibility(View.VISIBLE)
//                holder.audioVisualizer.setOnTouchListener(object : onProgressTouch() {
//                    fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
//                        when (motionEvent.getAction()) {
//                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> return if (VoiceManager.getInstance()
//                                    .playbackInProgress(holder.attachmentId, timestamp)
//                            ) {
//                                LogManager.d("TOUCH", "down/move super")
//                                super.onTouch(view, motionEvent)
//                            } else {
//                                LogManager.d("TOUCH", "down/move")
//                                (view as PlayerVisualizerView).updatePlayerPercent(0, true)
//                                true
//                            }
//                            MotionEvent.ACTION_UP -> {
//                                if (VoiceManager.getInstance()
//                                        .playbackInProgress(holder.attachmentId, timestamp)
//                                ) listener.onVoiceProgressClick(
//                                    holder.getAdapterPosition(),
//                                    holder.attachmentId,
//                                    timestamp,
//                                    motionEvent.getX() as Int,
//                                    view.getWidth()
//                                )
//                                LogManager.d("TOUCH", "up super")
//                                return super.onTouch(view, motionEvent)
//                            }
//                        }
//                        LogManager.d("TOUCH", "empty")
//                        return super.onTouch(view, motionEvent)
//                    }
//                })
//            } else {
//                holder.tvFileSize.setText(FileUtils.byteCountToDisplaySize(size ?: 0))
//                if (SettingsManager.chatsAutoDownloadVoiceMessage()) listener.onFileClick(position)
//            }
//            holder.ivFileIcon.setImageResource(R.drawable.ic_play)
//        } else {
//            // set file icon
//            holder.voiceMessage = false
//            holder.tvFileName.setText(referenceRealmObject.getTitle())
//            val size: Long = referenceRealmObject.getFileSize()
//            holder.tvFileSize.setText(FileUtils.byteCountToDisplaySize(size ?: 0))
//            holder.ivFileIcon.setImageResource(
//                if (referenceRealmObject.getFilePath() != null) getFileIconByCategory(
//                    FileCategory.determineFileCategory(referenceRealmObject.getMimeType())
//                ) else R.drawable.ic_download
//            )
//        }
//        holder.ivFileIcon.setOnClickListener { view ->
//            if (holder.voiceMessage) listener.onVoiceClick(
//                holder.getAdapterPosition(),
//                holder.attachmentId,
//                referenceRealmObject.getFilePath() != null,
//                timestamp
//            ) else listener.onFileClick(position)
//        }
//        holder.itemView.setOnLongClickListener { v ->
//            if (items.size() > position) listener.onFileLongClick(items.get(position), v)
//            true
//        }
//        holder.ivCancelDownload.setOnClickListener { v -> listener.onDownloadCancel() }
//        holder.itemView.addOnAttachStateChangeListener(object : OnAttachStateChangeListener() {
//            fun onViewAttachedToWindow(view: View?) {
//                holder.subscribeForDownloadProgress()
//                holder.subscribeForAudioProgress()
//            }
//
//            fun onViewDetachedFromWindow(v: View?) {
//                holder.unsubscribeAll()
//            }
//        })
    }

//    override fun getItemCount(): Int = items.size()

    private fun getFileIconByCategory(category: FileCategory): Int {
        return when (category) {
            FileCategory.IMAGE -> R.drawable.ic_image
            FileCategory.AUDIO -> R.drawable.ic_audio
            FileCategory.VIDEO -> R.drawable.ic_video
            FileCategory.DOCUMENT -> R.drawable.ic_document
            FileCategory.PDF -> R.drawable.ic_pdf
            FileCategory.TABLE -> R.drawable.ic_table
            FileCategory.PRESENTATION -> R.drawable.ic_presentation
            FileCategory.ARCHIVE -> R.drawable.ic_archive
            else -> R.drawable.ic_file
        }

    }

    override fun getItemCount(): Int {
        return 0
    }
}