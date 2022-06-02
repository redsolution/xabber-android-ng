package com.xabber.presentation.application.fragments.message

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import com.xabber.presentation.application.util.StringUtils
import java.util.*
import kotlin.collections.ArrayList


class MessageFragment : DetailBaseFragment(R.layout.fragment_message), MessageAdapter.Listener {
    private val binding by viewBinding(FragmentMessageBinding::bind)
    private var messageAdapter: MessageAdapter? = null
    private val viewModel = MessageViewModel()
    var name: String = ""
    var position = 0
    var offset = 0

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
//val listState: Parcelable? = savedInstanceState.getParcelable("listState")
         //   Log.d("listState", "$listState")
            position = savedInstanceState.getInt("position")
            offset = savedInstanceState.getInt("offset")
        }




        binding.messageUserName.text = name
        initToolbarActions()
        // binding?.tvUserName?.text = userName
        //   applicationToolbarChanger().toolbarIconChange(FragmentAction(R.drawable.ic_material_check_24, R.string.signup_username_subtitle))
        initNavigationBar()

//        val lm = LinearLayoutManager(requireContext())
//        lm.reverseLayout = true
//        with(binding.messageList) {
//            this.layoutManager = lm
//            this.adapter = MessageAdapter().also { messageAdapter = it }
//        }
        val adapter = MessageAdapter(this)

        binding.messageList.adapter = adapter
        val lin = LinearLayoutManager(context)
       lin.reverseLayout = true

        binding.messageList.layoutManager = lin

        val list = ArrayList<MessageDto>()
          list.add(
            MessageDto(
                "1",
                true,
                "Кирилл Степанов",
                "Геннадий Белов",
                "2Apps often need to provide the user with a consistent experience for executing actions that are contextually specific. The most common actions for an activity live on the default Action Bar but actions that are more specific to an item or element can be displayed contextually using menus and popups. In addition to menus, apps often need to display popup overlays that contain informational content. This guide covers how to manage menus and popups within your Android apps.",
                MessageSendingState.Read,
                System.currentTimeMillis() + 2000000,
                null,
                MessageDisplayType.Text,
                false,
                false, null,false
            )
        )
          list.add(
            MessageDto(
                "1",
                true,
                "Кирилл Степанов",
                "Геннадий Белов",
                "1Apps often need to provide the user with a consistent experience for executing actions that are contextually specific. The most common actions for an activity live on the default Action Bar but actions that are more specific to an item or element can be displayed contextually using menus and popups. In addition to menus, apps often need to display popup overlays that contain informational content. This guide covers how to manage menus and popups within your Android apps.",
                MessageSendingState.Read,
                System.currentTimeMillis() + 1000000,
                null,
                MessageDisplayType.Text,
                false,
                false, null,false
            )
        )
        list.add(
            MessageDto(
                "1",
                true,
                "Кирилл Степанов",
                "Геннадий Белов",
                "Apps often need to provide the user with a consistent experience for executing actions that are contextually specific. The most common actions for an activity live on the default Action Bar but actions that are more specific to an item or element can be displayed contextually using menus and popups. In addition to menus, apps often need to display popup overlays that contain informational content. This guide covers how to manage menus and popups within your Android apps.",
                MessageSendingState.Read,
                System.currentTimeMillis(),
                null,
                MessageDisplayType.Text,
                false,
                false, null,false
            )
        )
         list.add(
            MessageDto(
                "2",
                true,
                "Ann",
                "Геннадий Белов",
                "Алексей присоединился к чату",
                MessageSendingState.Read,
               12345678,
                null,
                MessageDisplayType.System,
                false,
                false,
                null,false
            )
        )
  list.add(
            MessageDto(
                "3",
                true,
                "Кирилл Степанов",
                "Геннадий Белов",
                "What are you doing?",
                MessageSendingState.Read,
               5623450975,
                null,
                MessageDisplayType.Text,
                false,
                false,
                null,false
            )
        )
 list.add(
            MessageDto(
                "3",
                true,
                "Кирилл Степанов",
                "Геннадий Белов",
                "What are you doing?",
                MessageSendingState.Read,
               5623450975,
                null,
                MessageDisplayType.Text,
                false,
                false,
                null,false
            )
        )
 list.add(
            MessageDto(
                "3",
                true,
                "Кирилл Степанов",
                "Геннадий Белов",
                "What are you doing?",
                MessageSendingState.Read,
               5623450975,
                null,
                MessageDisplayType.Text,
                false,
                false,
                null,false
            )
        )
 list.add(
            MessageDto(
                "3",
                true,
                "Кирилл Степанов",
                "Геннадий Белов",
                "What are you doing?",
                MessageSendingState.Read,
               5623450975,
                null,
                MessageDisplayType.Text,
                false,
                false,
                null,false
            )
        )

list.add(
            MessageDto(
                "3",
                true,
                "Кирилл Степанов",
                "Геннадий Белов",
                "What are you doing?",
                MessageSendingState.Read,
               5623450975,
                null,
                MessageDisplayType.Text,
                false,
                false,
                null,false
            )
        )
 list.add(
            MessageDto(
                "3",
                true,
                "Кирилл Степанов",
                "Геннадий Белов",
                "What are you doing?",
                MessageSendingState.Read,
               5623450975,
                null,
                MessageDisplayType.Text,
                false,
                false,
                null,false
            )
        )
list.add(
            MessageDto(
                "3",
                true,
                "Кирилл Степанов",
                "Геннадий Белов",
                "What are you doing?",
                MessageSendingState.Read,
               5623450975,
                null,
                MessageDisplayType.Text,
                false,
                false,
                null,false
            )
        )
 list.add(
            MessageDto(
                "3",
                true,
                "Кирилл Степанов",
                "Геннадий Белов",
                "What are you doing?",
                MessageSendingState.Read,
               5623450975,
                null,
                MessageDisplayType.Text,
                false,
                false,
                null,false
            )
        )

        list.add(
            MessageDto(
                "1",
                true,
                "Кирилл Степанов",
                "Геннадий Белов",
                "2Apps often need to provide the user with a consistent experience for executing actions that are contextually specific. The most common actions for an activity live on the default Action Bar but actions that are more specific to an item or element can be displayed contextually using menus and popups. In addition to menus, apps often need to display popup overlays that contain informational content. This guide covers how to manage menus and popups within your Android apps.",
                MessageSendingState.Read,
                System.currentTimeMillis() + 2000000,
                null,
                MessageDisplayType.Text,
                false,
                false, null,false
            )
        )
list.sort()
        adapter.submitList(list)
      binding.messageList.scrollToPosition(0)
        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
            binding.messageList.scrollBy(0, -182)
            }
        },200)

//        binding.messageList.scrollBy(0, -626)
       val manager = binding.messageList.layoutManager as LinearLayoutManager
        val first = manager.findFirstVisibleItemPosition()
       Log.d("listState", "${first}")
// val manager = binding.messageList.layoutManager as LinearLayoutManager
//       manager.scrollToPositionWithOffset(0, 1819)
        //  fillAdapter()
        initAnswer()
        initButton()


        val replySwipeCallback = ReplySwipeCallback(binding.messageList.context)
        replySwipeCallback.setSwipeEnabled(true)
        replySwipeCallback.replySwipeCallback()
        ItemTouchHelper(replySwipeCallback).attachToRecyclerView(binding.messageList)

        binding.messageList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                replySwipeCallback.onDraw(c)
            }
        })

    }


    private fun updateTopDateIfNeed() {
        val layoutManager = binding.messageList.layoutManager as LinearLayoutManager
        val position = layoutManager.findFirstVisibleItemPosition()
       val message : MessageDto = messageAdapter!!.getItem(position)
        if (message != null)
            binding.tvTopDate.setText(StringUtils.getDateStringForMessage(message.t)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initButton() {
        binding.buttonRecord.setOnTouchListener { view, motionEvent ->
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

        binding.buttonAttach.setOnClickListener {

        }

        binding.btnDownward.setOnClickListener {
            binding.messageList.scrollToPosition(0)
        }

        binding.messageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy < 0) binding.btnDownward.animate()
                    .translationY(binding.btnDownward.height + binding.btnDownward.marginBottom.toFloat())
                else if (dy > 0) binding.btnDownward.animate()
                    .translationY(0f)
            }
        })


    }


    private fun initAnswer() {
        binding.close.setOnClickListener { binding.answer.isVisible = false }
    }

    private fun initToolbarActions() {
        binding.messageIconBack.setOnClickListener {
            navigator().closeDetail()
        }
    }

    private fun initNavigationBar() {


        with(binding) {
            chatInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun afterTextChanged(p0: Editable?) {
                    if (p0.toString().trim().isNotEmpty()) {
                        buttonRecord.visibility = View.GONE
                        buttonAttach.visibility = View.GONE
                        buttonSendMessage.visibility = View.VISIBLE
                    } else {
                        buttonRecord.visibility = View.VISIBLE
                        buttonAttach.visibility = View.VISIBLE
                        buttonSendMessage.visibility = View.GONE
                    }
                }
            })
        }


    }


    private fun fillAdapter() {
        viewModel.messages.observe(viewLifecycleOwner) {
            messageAdapter!!.submitList(it)
        }

    }

    override fun onDestroy() {

val manager = binding.messageList.layoutManager as LinearLayoutManager
        AudioRecorder.releaseRecorder()
        messageAdapter = null
      val a = manager.findFirstVisibleItemPosition()
          val firstChild = binding.messageList.getChildAt(0)
        val firstVisiblePosition = binding.messageList.getChildAdapterPosition(firstChild)
        val offset = firstChild.top
        Log.d("listState", "a = $a")
        super.onDestroy()
    }

    fun showReplyUI(position: Int) {
        binding.answer.isVisible = true
        val v = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v.vibrate(500)
        }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("name", name)
//        val manager = binding.messageList.layoutManager as LinearLayoutManager
//        val current = manager.findFirstVisibleItemPosition()
 //      val firstChild = binding.messageList.getChildAt(current)
  // val firstVisiblePosition = binding.messageList.getChildAdapterPosition(firstChild)
//        val offset = firstChild.top
//        outState.putInt("position", firstVisiblePosition)
//        outState.putInt("offset", offset)
  //      Log.d("listState","current = $current, offset = $offset, position = $position")
    }

    private fun sendMessage() {
        val text = binding.chatInput.text.toString().trim()
        binding.chatInput.text?.clear()

    }

    override fun editMessage(primary: String) {
       Toast.makeText(context, primary, Toast.LENGTH_SHORT).show()
    }

}