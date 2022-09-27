package com.xabber.presentation.application.fragments.chat.message;

import static com.xabber.utils.ExtentsionsKt.dipToPx;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.StyleRes;

import com.xabber.R;
import com.xabber.model.dto.MessageDto;
import com.xabber.model.dto.MessageVhExtraData;
import com.xabber.model.xmpp.messages.MessageSendingState;

public class OutgoingMessageVH extends MessageVH {

    OutgoingMessageVH(View itemView, MessageClickListener messageListener,
                      MessageLongClickListener longClickListener,
                      FileListener fileListener) {
        super(itemView, messageListener, longClickListener, fileListener);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    public void bind(MessageDto messageDto, MessageVhExtraData extraData) {
        super.bind(messageDto, extraData);

        final Context context = itemView.getContext();
        boolean needTail = extraData.isNeedTail();

        setStatusIcon(messageDto);

        // setup PROGRESS
        if (messageDto.getMessageSendingState().equals(MessageSendingState.Uploading)) {
            getProgressBar().setVisibility(View.VISIBLE);
            getMessageFileInfo().setText("Sending...");
            getMessageFileInfo().setVisibility(View.VISIBLE);
            getMessageTime().setVisibility(View.GONE);
            getBottomMessageTime().setVisibility(View.GONE);
        } else {
            getProgressBar().setVisibility(View.GONE);
            getMessageFileInfo().setText("");
            getMessageFileInfo().setVisibility(View.GONE);
            getMessageTime().setVisibility(View.VISIBLE);
            getBottomMessageTime().setVisibility(View.VISIBLE);
        }

        // setup FORWARDED
        boolean haveForwarded = messageDto.getHasForwardedMessages();
        if (haveForwarded) {
         //   setupForwarded(messageDto, extraData);

            LinearLayout.LayoutParams forwardedParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            forwardedParams.setMargins(
                    dipToPx(0f, context),
                    dipToPx(0f, context),
                    dipToPx(4f, context),
                    dipToPx(0f, context));

            getForwardedMessagesRV().setLayoutParams(forwardedParams);
        } else if (getForwardedMessagesRV() != null) {
            getForwardedMessagesRV().setVisibility(View.GONE);
        }

        if (messageDto.getHasReferences() && messageDto.getHasImage()) {
            needTail = false;
        }

        // setup BACKGROUND
        Drawable shadowDrawable = context.getResources().getDrawable(
                (needTail ? R.drawable.msg_out_shadow : R.drawable.msg_shadow)
        );

        shadowDrawable.setColorFilter(
                context.getResources().getColor(R.color.black),
                PorterDuff.Mode.MULTIPLY
        );

        getMessageBalloon().setBackground(
                context.getResources().getDrawable(
                        (needTail ? R.drawable.msg_out : R.drawable.msg)
                )
        );
        getMessageShadow().setBackground(shadowDrawable);

        // setup BALLOON margins
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        layoutParams.setMargins(
                dipToPx(0f, context),
                dipToPx(haveForwarded ? 0f : 3f, context),
                dipToPx(needTail ? 3f : 11f, context),
                dipToPx(3f, context)
        );
        getMessageShadow().setLayoutParams(layoutParams);

        // setup MESSAGE padding
        getMessageBalloon().setPadding(
                dipToPx(12f, context),
                dipToPx(8f, context),
                //Utils.dipToPx(needTail ? 20f : 12f, context),
                dipToPx(needTail ? 14.5f : 6.5f, context),
                dipToPx(8f, context));

        float border = 3.5f;

        if(messageDto.getHasReferences()) {
            if(messageDto.getHasImage()) {
                getMessageBalloon().setPadding(
                        dipToPx(border, context),
                        dipToPx(border-0.05f, context),
                        dipToPx(border, context),
                        dipToPx(border, context)
                );
                if (messageDto.isAttachmentImageOnly()) {
                    getMessageTime().setTextColor(context.getResources().getColor(R.color.white));
                    getBottomMessageTime().setTextColor(context.getResources().getColor(R.color.white));
                } else {
                    getMessageInfo().setPadding(
                            0, 0, dipToPx(border+1.5f, context), dipToPx(4.7f, context)
                    );
                }
            }
        }

        // setup BACKGROUND COLOR
//       setUpMessageBalloonBackground(
//                getMessageBalloon(),
//                extraData.getColors().getOutgoingRegularBalloonColors()
//        );

        // subscribe for FILE UPLOAD PROGRESS
        itemView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                //subscribeForUploadProgress();
            }
            @Override
            public void onViewDetachedFromWindow(View v) {
             //   unsubscribeAll();
            }
        });

        if (getMessageTextTv().getText().toString().trim().isEmpty()) {
            getMessageTextTv().setVisibility(View.GONE);
        }
    }

    private void setStatusIcon(MessageDto messageDto) {
        getStatusIcon().setVisibility(View.VISIBLE);
        getBottomStatusIcon().setVisibility(View.VISIBLE);
        getProgressBar().setVisibility(View.GONE);

        if (messageDto.getMessageSendingState() == MessageSendingState.Uploading) {
            getMessageTextTv().setText("");
            getStatusIcon().setVisibility(View.GONE);
            getBottomStatusIcon().setVisibility(View.GONE);
        } else {
            MessageDeliveryStatusHelper.INSTANCE.setupStatusImageView(
                    messageDto, getStatusIcon()
            );
            MessageDeliveryStatusHelper.INSTANCE.setupStatusImageView(
                    messageDto, getBottomStatusIcon()
            );
        }
    }

}
