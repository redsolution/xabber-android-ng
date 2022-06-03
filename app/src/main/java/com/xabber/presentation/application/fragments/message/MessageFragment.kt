package com.xabber.presentation.application.fragments.message

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.data.dto.MessageDto
import com.xabber.data.xmpp.messages.MessageDisplayType
import com.xabber.data.xmpp.messages.MessageSendingState
import com.xabber.databinding.FragmentMessageBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment


class MessageFragment : DetailBaseFragment(R.layout.fragment_message), MessageAdapter.Listener,
    AttachDialog.Listener {
    private val binding by viewBinding(FragmentMessageBinding::bind)
    private var messageAdapter: MessageAdapter? = null
    private val viewModel = MessageViewModel()
    var name: String = ""

    companion object {
        fun newInstance(_name: String) = MessageFragment().apply {
            arguments = Bundle().apply {
                putString("_name", _name)
                name = _name
            }
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) {
            name = savedInstanceState.getString("name", "")
        }

        binding.messageUserName.text = name
        initToolbarActions()
        initRecyclerView()
        subscribeViewModelData()
        initAnswer()
        initInputLayoutActions()
    }

    private fun initToolbarActions() {
        binding.messageIconBack.setOnClickListener {
            navigator().closeDetail()
        }
    }

    private fun initRecyclerView() {
        messageAdapter = MessageAdapter(this)
        val linearLayoutManager = LinearLayoutManager(context)
        linearLayoutManager.reverseLayout = true
        binding.messageList.adapter = messageAdapter
        binding.messageList.layoutManager = linearLayoutManager
        val replySwipeCallback = ReplySwipeCallback(binding.messageList.context)
        replySwipeCallback.setSwipeEnabled(true)
        replySwipeCallback.replySwipeCallback()
        ItemTouchHelper(replySwipeCallback).attachToRecyclerView(binding.messageList)

        binding.messageList.addItemDecoration(
            object : RecyclerView.ItemDecoration() {
                override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                    replySwipeCallback.onDraw(c)
                }
            })
    }

    private fun subscribeViewModelData() {
        viewModel.initList()
        viewModel.messages.observe(viewLifecycleOwner) {
            it.sort()
            messageAdapter?.submitList(it)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initInputLayoutActions() {
        chatInputAddListener()
        binding.buttonEmoticon.setOnClickListener { }

        binding.buttonAttach.setOnClickListener {
            val dialog = AttachDialog()
            navigator().showBottomSheetDialog(dialog)
        }

        binding.buttonSendMessage.setOnClickListener {
            val text = binding.chatInput.text.toString().trim()
            binding.chatInput.text?.clear()
            val timeStamp = System.currentTimeMillis()
            viewModel.insertMessage(
                MessageDto(
                    "151515",
                    true,
                    "Алексей Иванов",
                    "Геннадий Белов",
                    text,
                    MessageSendingState.Deliver,
                    timeStamp,
                    null,
                    MessageDisplayType.Text,
                    false,
                    false,
                    null,
                    false
                )
            )
            messageAdapter?.notifyDataSetChanged()
            scrollDown()
        }

        binding.buttonRecord.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                binding.groupRecord.isVisible = true
                binding.buttonEmoticon.isVisible = false
                binding.buttonAttach.isVisible = false
                binding.chatInput.isVisible = false
                binding.recordChronometer.base = SystemClock.elapsedRealtime()
                binding.recordChronometer.start()
                AudioRecorder.startRecord()
            } else if (motionEvent.action == MotionEvent.ACTION_UP) {
                binding.groupRecord.isVisible = false
                binding.buttonEmoticon.isVisible = true
                binding.buttonAttach.isVisible = true
                binding.chatInput.isVisible = true

                binding.recordChronometer.stop()
                binding.recordChronometer.base = SystemClock.elapsedRealtime()
                AudioRecorder.stopRecord { file ->
                    Toast.makeText(context, "${file == null}", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }

        binding.messageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy < 0) binding.btnDownward.animate()
                    .translationY(binding.btnDownward.height + binding.btnDownward.marginBottom.toFloat())
                else if (dy > 0) binding.btnDownward.animate()
                    .translationY(0f)
            }
        })
        binding.btnDownward.setOnClickListener {
            scrollDown()
        }

    }

    private fun scrollDown() {
        binding.messageList.scrollToPosition(0)
    }

    private fun updateTopDateIfNeed() {
        val layoutManager = binding.messageList.layoutManager as LinearLayoutManager
        val position = layoutManager.findFirstVisibleItemPosition()
        // val message : MessageDto = messageAdapter!!.getItem(position)
        // if (message != null)
        //     binding.tvTopDate.setText(StringUtils.getDateStringForMessage(message.t)
    }


    private fun initAnswer() {
        binding.close.setOnClickListener { binding.answer.isVisible = false }
    }


    private fun chatInputAddListener() {
        with(binding) {
            chatInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

                }

                override fun afterTextChanged(p0: Editable?) {
                    if (p0.toString().trim().isNotEmpty()) {
                        buttonRecord.isVisible = false
                        buttonAttach.isVisible = false
                        buttonSendMessage.isVisible = true
                    } else {
                        buttonRecord.isVisible = true
                        buttonAttach.isVisible = true
                        buttonSendMessage.isVisible = false
                    }
                }
            })
        }

    }


    override fun onDestroy() {
        super.onDestroy()
        messageAdapter = null
        AudioRecorder.releaseRecorder()
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("name", name)
    }

    override fun editMessage(primary: String) {
        //   binding.chatInput.text = primary
    }

    override fun onRecentPhotosSend(paths: List<String>) {

    }

    override fun onGalleryClick() {

    }

    override fun onFilesClick() {

    }

    override fun onCameraClick() {

    }

    override fun onLocationClick() {

    }

}