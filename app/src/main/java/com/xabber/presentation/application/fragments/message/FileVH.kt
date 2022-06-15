package com.xabber.presentation.application.fragments.message

import android.app.DownloadManager
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import rx.subscriptions.CompositeSubscription

class FileVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val  subscriptions = CompositeSubscription()
       private var attachmentId: String = ""
       private var voiceMessage = false

      public fun unsubscribeAll() {
            subscriptions.clear()
        }

//        public fun subscribeForDownloadProgress() {
//            subscriptions.add(
//                DownloadManager.getInstance().subscribeForProgress()
//                    .doOnNext(this::setUpProgress).subscribe());
//        }
//
//        fun subscribeForAudioProgress() {
//            subscriptions.add(VoiceManager.PublishAudioProgress.getInstance().subscribeForProgress()
//                    .doOnNext(this::setUpAudioProgress).subscribe());
//        }
//
//        private fun setUpAudioProgress(VoiceManager.PublishAudioProgress.AudioInfo info) {
//            if(info != null && info.getAttachmentIdHash() == attachmentId.hashCode()) {
//                if (info.getTimestamp() != null && info.getTimestamp().equals(timestamp)) {
//                    if (info.getDuration() != 0) {
//                        if (info.getDuration() > 1000) {
//                            audioVisualizer.updatePlayerPercent(
//                                    ((float) info.getCurrentPosition() / (float) info.getDuration()),
//                                    false
//                            );
//                            audioProgress.setMax(info.getDuration());
//                        } else {
//                            audioVisualizer.updatePlayerPercent(
//                                    ((float) info.getCurrentPosition() / ((float) info.getDuration() * 1000)),
//                                    false
//                            );
//                            audioProgress.setMax(info.getDuration() * 1000);
//                        }
//                        audioProgress.setProgress(info.getCurrentPosition());
//                        if (info.getResultCode() == VoiceManager.COMPLETED_AUDIO_PROGRESS) {
//                            ivFileIcon.setImageResource(R.drawable.ic_play);
//                            showProgress(false);
//                            tvFileSize.setText(
//                                    DatesUtilsKt.getDurationStringForVoiceMessage(
//                                            0L,
//                                    info.getDuration() > 1000 ? (info.getDuration() / 1000) : info.getDuration()
//                                    )
//                            );
//                        } else if (info.getResultCode() == VoiceManager.PAUSED_AUDIO_PROGRESS) {
//                            ivFileIcon.setImageResource(R.drawable.ic_play);
//                            showProgress(false);
//                            tvFileSize.setText(
//                                    DatesUtilsKt.getDurationStringForVoiceMessage(
//                                            (long) info.getCurrentPosition() / 1000,
//                                            info.getDuration() > 1000 ? (info.getDuration() / 1000) : info.getDuration()
//                                    )
//                            );
//                        } else {
//                            ivFileIcon.setImageResource(R.drawable.ic_pause);
//                            showProgress(false);
//                            tvFileSize.setText(
//                                    DatesUtilsKt.getDurationStringForVoiceMessage(
//                                            (long) info.getCurrentPosition() / 1000,
//                                            info.getDuration() > 1000 ? (info.getDuration() / 1000) : info.getDuration()
//                                    )
//                            );
//                        }
//                    }
//                }
//            }
//        }
}