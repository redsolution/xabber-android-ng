package com.xabber.presentation.application.fragments.chat.message

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.model.dto.MessageDto
import com.xabber.utils.dp
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.model.xmpp.messages.MessageSendingState.*
import com.xabber.databinding.ItemMessageOutgoingBinding
import com.xabber.model.dto.MessageVhExtraData
import com.xabber.utils.StringUtils
import java.util.*

class OutgoingMessageVH(
    private val itemView: View,
    private val messageClickListener: MessageClickListener,
    private val messageLongClickListener: MessageLongClickListener,
    private val fileListener: FileListener
) : MessageVH(itemView, messageClickListener, messageLongClickListener, fileListener) {


    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("RestrictedApi")
    override fun bind(
        messageDto: MessageDto,
        extraData: MessageVhExtraData
    ) {
        super.bind(messageDto, extraData)
        val needTail = extraData.isNeedTail

        setStatusIcon(messageDto)

        binding.includeNonExternalGeolocation.location.isVisible = (messageDto.location != null)
        if (messageDto.location != null) binding.includeNonExternalGeolocation.locationCoordinates.text =
            messageDto.location.latitude.toString() + ", " + messageDto.location.longitude.toString()


        val isNeedTail =
            if ((messageDto.messageBody == null || messageDto.messageBody.isNotEmpty()) && messageDto.references != null) false else _isNeedTail
// text & appearance
        binding.tvContent.isVisible = messageDto.messageBody != null
        if (messageDto.messageBody != null) binding.tvContent.text = messageDto.messageBody
// tvContent.setTextAppearance(SettingsManager.chatsAppearanceStyle()) - берем из класса настроек

// date
        binding.messageDate.tvDate.isVisible = needDay
        binding.messageDate.tvDate.text =
            StringUtils.getDateStringForMessage(messageDto.sentTimestamp)

// time
        val date = Date(messageDto.sentTimestamp)
        val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
        binding.messageInfo.isVisible =
            (messageDto.messageBody != null && messageDto.references == null && messageDto.kind == null) || (messageDto.location != null)
        binding.tvSendingTime.text = time

// status
        if (messageDto.isOutgoing) setStatus(
            binding.imageMessageStatus,
            messageDto.messageSendingState
        )


        //  binding.messageInfo.isVisible = messageDto.kind == null
        binding.info.isVisible =
            messageDto.kind != null || (messageDto.references != null && messageDto.messageBody != null) || messageDto.location != null


        binding.checkboxIncoming.isVisible = showCheckbox
//dateMessage.isVisible = need

// val nextMessage = getMessage(position + 1)
// if (nextMessage != null)

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(24.dp, 0, if (isNeedTail) 2.dp else 10.dp, 0)

        params.gravity = Gravity.END
        binding.balloon.layoutParams = params
        if (messageDto.references == null && messageDto.messageBody != null) {
            binding.balloon.setPadding(16.dp, 8.dp, if (isNeedTail) 16.dp else 8.dp, 10.dp)
        } else if (messageDto.references != null && messageDto.messageBody != null) {
            binding.balloon.setPadding(4.dp, 4.dp, if (isNeedTail) 12.dp else 8.dp, 10.dp)
        } else {
            binding.balloon.setPadding(4.dp, 4.dp, 4.dp, -17.dp)
        }


        val typedValue = TypedValue()
        binding.root.context.theme.resolveAttribute(R.attr.message_background, typedValue, true)
        val shadowDrawable: Drawable =
            ContextCompat.getDrawable(binding.root.context, R.drawable.fwd_out_shadow)!!
        shadowDrawable.setColorFilter(
            ContextCompat.getColor(binding.root.context, R.color.black),
            PorterDuff.Mode.MULTIPLY
        )


        binding.balloon.setBackgroundDrawable(
            ContextCompat.getDrawable(
                binding.root.context,
                if (isNeedTail) R.drawable.msg_out else R.drawable.msg
            )
        )
        Log.d("aaa", "${binding.info.isVisible}")

        if (messageDto.kind == null) {
            binding.replyMessage.isVisible = false
        } else {
            binding.replyMessage.isVisible = true
            binding.replyMessageTitle.text = messageDto.kind.owner
            binding.replyMessageContent.text = messageDto.kind.content
        }

// tvContent.marginStart = if (needTail) 20 else 11

        binding.root.setOnClickListener {
            Log.d("show", "$showCheckbox")
            if (showCheckbox) {
                binding.checkboxIncoming.isChecked = !binding.checkboxIncoming.isChecked
                if (binding.checkboxIncoming.isChecked) {
                    binding.frameLayoutBlackout.setBackgroundResource(R.color.selected)
                    binding.tvContent.setTextIsSelectable(true)
                } else {
                    binding.frameLayoutBlackout.setBackgroundResource(R.color.transparent)
                    binding.tvContent.setTextIsSelectable(false)
                }
            } else {
                val popup = PopupMenu(it.context, it, Gravity.CENTER)
                popup.setForceShowIcon(true)
                if (messageDto.isOutgoing) popup.inflate(R.menu.popup_menu_message_outgoing)
                else popup.inflate(R.menu.popup_menu_message_incoming)

                val menuHealper =
                    MenuPopupHelper(it.context, popup.menu as MenuBuilder, binding.root)
                menuHealper.setForceShowIcon(true)
                menuHealper.show()

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.copy -> {
                            val text = binding.tvContent.text.toString()
                            listener.copyText(text)
                            showSnackbar(itemView)

                        }
                        R.id.forward -> {
                            listener.forwardMessage(messageDto)
                        }
                        R.id.reply -> {
                            listener.replyMessage(messageDto)
                        }
                        R.id.delete_message -> {
                            listener.deleteMessage(messageDto)
                        }
                        R.id.edit -> {
                            listener.editMessage(messageDto.primary)
                        }
                    }
                    true
                }
                popup.show()
                true
            }
        }

        setBackground(messageDto, isNeedTail)
        setupReferences(messageDto)
        binding.root.setOnLongClickListener {

            if (!showCheckbox) listener.onLongClick(messageDto.primary)
//                } else {
//                    binding.checkboxIncoming.isChecked = !binding.checkboxIncoming.isChecked
//                    binding.balloon.setBackgroundResource(R.color.selected)
//                    binding.tvContent.setTextIsSelectable(showCheckbox)
//                }
            true
        }
    }

    override fun onClick(p0: View?) {
    super
    }

    override fun onLongClick(p0: View?): Boolean {
        TODO("Not yet implemented")
    }

    override fun onFileClick(position: Int) {
        TODO("Not yet implemented")
    }

    override fun onVoiceClick(
        position: Int,
        attachmentId: String,
        saved: Boolean,
        timeStamp: Long
    ) {
        TODO("Not yet implemented")
    }

    override fun onVoiceProgressClick(
        position: Int,
        attachmentId: String,
        timestamp: Long,
        current: Int,
        max: Int
    ) {
        TODO("Not yet implemented")
    }

    override fun onFileLongClick(caller: View) {
        TODO("Not yet implemented")
    }

    override fun onDownLoadCancel() {
        TODO("Not yet implemented")
    }

    override fun onDownLoadError(error: String) {
        TODO("Not yet implemented")
    }


    private fun showSnackbar(view: View) {
        val snackbar: Snackbar?
        snackbar = view.let {
            Snackbar.make(
                it,
                "The message has copied to the clipboard",
                Snackbar.LENGTH_SHORT
            )
        }
        snackbar.setTextColor(Color.YELLOW)
        snackbar.show()
    }

    private fun setStatusIcon(messageDto: MessageDto) {
       statusIcon.isVisible = true
        bottomStatusIcon.isVisible = true
        progressBar.isVisible = false

        if (messageDto.messageSendingState == Uploading) {
            messageTextTv.text = ""
            statusIcon.isVisible = false
            bottomStatusIcon.isVisible = false
        } else {
            MessageDeliveryStatusHelper.INSTANCE.setupStatusImageView(
                    messageRealmObject, getStatusIcon()
            );
            MessageDeliveryStatusHelper.INSTANCE.setupStatusImageView(
                    messageRealmObject, getBottomStatusIcon()
            );
        }
    }

    private fun setStatusIcon(messageDto: MessageDto) {
        var image: Int? = null
        var tint: Int? = null
        when (messageDto.messageSendingState) {
            Sending -> {
                tint = R.color.grey_500
                image = R.drawable.ic_clock_outline
            }
            Sended -> {
                tint = R.color.grey_500
                image = R.drawable.ic_check_green
            }
            Deliver -> {
                tint = R.color.green_500
                image = R.drawable.ic_check_green
            }
            Read -> {
                tint = R.color.green_500
                image = R.drawable.ic_check_all_green
            }
            Error -> {
                tint = R.color.red_500
                image = R.drawable.ic_exclamation_mark_outline
            }
            NotSended -> {
                tint = R.color.grey_500
                image = R.drawable.ic_clock_outline
            }
            Uploading -> {
                tint = R.color.blue_500
                image = R.drawable.ic_clock_outline
            }
            None -> {
                imageView.isVisible = false
            }
        }
        if (tint != null && image != null) {
            Glide.with(itemView)
                .load(image)
                .centerCrop()
                .skipMemoryCache(true)
                .into(imageView)
            imageView.setColorFilter(
                ContextCompat.getColor(itemView.context, tint),
                PorterDuff.Mode.SRC_IN
            )
        }

    }

    private fun setBackground(
        messageDto: MessageDto,
        isMessageNeedTail: Boolean
    ) {
        val balloonDrawable = ResourcesCompat.getDrawable(
            itemView.resources,
            if (isMessageNeedTail)
                R.drawable.msg_out
            else
                R.drawable.msg,
            itemView.context.theme
        )!!
        if (!messageDto.isOutgoing)
            balloonDrawable.setColorFilter(
                itemView.resources.getColor(
                    R.color.blue_100,
                    itemView.context.theme
                ), PorterDuff.Mode.MULTIPLY
            )
        binding.balloon.background = balloonDrawable

        val shadowDrawable = ResourcesCompat.getDrawable(
            itemView.resources,
            if (isMessageNeedTail)
                if (messageDto.isOutgoing)
                    R.drawable.msg_out_shadow
                else
                    R.drawable.msg_in_shadow
            else
                R.drawable.msg_shadow,
            itemView.context.theme
        )!!
// if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
// shadowDrawable.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
// itemView.resources.getColor(R.color.grey_300, itemView.context.theme),
// BlendModeCompat.MULTIPLY
// )
// }
// else {
        shadowDrawable.setColorFilter(
            itemView.resources.getColor(
                R.color.black,
                itemView.context.theme
            ), PorterDuff.Mode.MULTIPLY
        )
    }
// }
// messageShadow.background = shadowDrawable


    private fun setupReferences(messageDto: MessageDto) {
        Log.d("uuu", "${messageDto.references}")
        binding.grid1.grid1.isVisible = false
        binding.grid2.grid2.isVisible = false
        binding.grid3.grid3.isVisible = false
        binding.grid4.grid4.isVisible = false
        binding.grid5.grid5.isVisible = false
        binding.grid6.grid6.isVisible = false

        if (messageDto.uries != null) {

            when (messageDto.uries.size) {
                0 -> {}
                1 -> {
                    binding.grid1.grid1.isVisible = true
                    Glide.with(binding.root).load(messageDto.uries[0])
                        .into(binding.grid1.ivImage0)
                    binding.grid1.imageMessageInfo.isVisible = messageDto.messageBody!!.isEmpty()
                    val date = Date(messageDto.sentTimestamp)
                    val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
                    binding.grid1.tvImageSendingTime.text = time
                }
                2 -> {
                    binding.grid2.grid2.isVisible = true
                    Glide.with(binding.root).load(messageDto.uries[0])
                        .into(binding.grid2.ivImage0)
                    Glide.with(binding.root).load(messageDto.uries[1])
                        .into(binding.grid2.ivImage1)
                    binding.grid2.imageMessageInfo.isVisible = messageDto.messageBody!!.isEmpty()
                    val date = Date(messageDto.sentTimestamp)
                    val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
                    binding.grid2.tvImageSendingTime.text = time
                }
                3 -> {
                    binding.grid3.grid3.isVisible = true
                    Glide.with(binding.root).load(messageDto.uries[0])
                        .into(binding.grid3.ivImage0)
                    Glide.with(binding.root).load(messageDto.uries[1])
                        .into(binding.grid3.ivImage1)
                    Glide.with(binding.root).load(messageDto.uries[2])
                        .into(binding.grid3.ivImage2)
                    binding.grid3.imageMessageInfo.isVisible = messageDto.messageBody!!.isEmpty()
                    val date = Date(messageDto.sentTimestamp)
                    val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
                    binding.grid3.tvImageSendingTime.text = time
                }
                4 -> {
                    binding.grid4.grid4.isVisible = true
                    Glide.with(binding.root).load(messageDto.uries[0])
                        .into(binding.grid4.ivImage0)
                    Glide.with(binding.root).load(messageDto.uries[1])
                        .into(binding.grid4.ivImage1)
                    Glide.with(binding.root).load(messageDto.uries[2])
                        .into(binding.grid4.ivImage2)
                    Glide.with(binding.root).load(messageDto.uries[3])
                        .into(binding.grid4.ivImage3)

                    binding.grid4.imageMessageInfo.isVisible = messageDto.messageBody!!.isEmpty()
                    val date = Date(messageDto.sentTimestamp)
                    val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
                    binding.grid4.tvImageSendingTime.text = time
                }
                5 -> {
                    binding.grid5.grid5.isVisible = true
                    Glide.with(binding.root).load(messageDto.uries[0])
                        .into(binding.grid5.ivImage0)
                    Glide.with(binding.root).load(messageDto.uries[1])
                        .into(binding.grid5.ivImage1)
                    Glide.with(binding.root).load(messageDto.uries[2])
                        .into(binding.grid5.ivImage2)
                    Glide.with(binding.root).load(messageDto.uries[3])
                        .into(binding.grid5.ivImage3)
                    Glide.with(binding.root).load(messageDto.uries[4])
                        .into(binding.grid5.ivImage4)

                    binding.grid5.imageMessageInfo.isVisible = messageDto.messageBody!!.isEmpty()
                    val date = Date(messageDto.sentTimestamp)
                    val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
                    binding.grid5.tvImageSendingTime.text = time
                }
                else -> {
                    binding.grid6.grid6.isVisible = true
                    Glide.with(binding.root).load(messageDto.uries[0])
                        .into(binding.grid6.ivImage0)
                    Glide.with(binding.root).load(messageDto.uries[1])
                        .into(binding.grid6.ivImage1)
                    Glide.with(binding.root).load(messageDto.uries[2])
                        .into(binding.grid6.ivImage2)
                    Glide.with(binding.root).load(messageDto.uries[3])
                        .into(binding.grid6.ivImage3)
                    Glide.with(binding.root).load(messageDto.uries[4])
                        .into(binding.grid6.ivImage4)
                    Glide.with(binding.root).load(messageDto.uries[5])
                        .into(binding.grid6.ivImage5)
                    val count = messageDto.uries.size - 6
                    if (count > 0) {
                        binding.grid6.tvCounter.isVisible = true
                        binding.grid6.tvCounter.text = "+ $count"
                    }

                    binding.grid6.imageMessageInfo.isVisible = messageDto.messageBody!!.isEmpty()
                    val date = Date(messageDto.sentTimestamp)
                    val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
                    binding.grid6.tvImageSendingTime.text = time
                }
            }


            //  setUpFile(messageDto.references, vhExtraData)
            //   setupNonExternalGeo(messageDto)
        } else {
            binding.grid1.grid1.isVisible = false
            binding.grid2.grid2.isVisible = false
            binding.grid3.grid3.isVisible = false
            binding.grid4.grid4.isVisible = false
            binding.grid5.grid5.isVisible = false
            binding.grid6.grid6.isVisible = false


        }
    }

    private fun setUpImage(messageDto: MessageDto) {

        //     messageDto.references
        //         ?.filter { it.isImage }
//            ?.also { imageCount = it.size }
//            ?.takeIf { it.isNotEmpty() }
//            ?.let {
//                RealmList<>().apply {
//                    addAll(it)
//                }
//            }
//            ?.let {
//                val gridBuilder = ImageGrid()
//                val imageGridView = messageDto.references?.size?.let {
//                    gridBuilder.inflateView(binding.imageGridContainerFl,
//                        it
//                    )
//                }


    }
}

private fun setUpFile(
    //   referenceRealmObjects: RealmList<ReferenceRealmObject>, vhExtraData: MessageVhExtraData
) {
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
}










