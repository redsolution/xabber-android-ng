package com.xabber.presentation.application.fragments.message

import android.annotation.SuppressLint
import android.content.Context
import android.os.*
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
import com.xabber.databinding.FragmentMessageBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.xmpp.messages.MessageDisplayType
import com.xabber.xmpp.messages.MessageSendingState


class MessageFragment : DetailBaseFragment(R.layout.fragment_message), SwipeControllerActions {
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
        if (savedInstanceState != null) name = savedInstanceState.getString("name", "")

        val messageSwipeController =
            MessageSwipeController(requireContext(), object : SwipeControllerActions {
                override fun showReplyUI(position: Int) {
                    binding.answer.isVisible = true
                }
            })

        val itemTouchHelper = ItemTouchHelper(messageSwipeController)
        itemTouchHelper.attachToRecyclerView(binding.messageList)
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
        val adapter = MessageTestAdapter()

        binding.messageList.adapter = adapter
        binding.messageList.layoutManager = LinearLayoutManager(context)
       val list = ArrayList<MessageDto>()
        list.add(MessageDto(
            "1",
            false,
            "Алескей Иванов",
            "Геннадий Белов",
            "Hi! What are you doing? I am go to school. It is very cold today. It is rain",
            MessageSendingState.Sending,
            System.currentTimeMillis(),
            null,
            MessageDisplayType.Text,
            false,
            false))
           list.add(MessageDto(
                "1",
                false,
                "Алескей Иванов",
                "Геннадий Белов",
                "Hi!",
                MessageSendingState.Sending,
                System.currentTimeMillis(),
                null,
                MessageDisplayType.Text,
                false,
                false))

        list.add(MessageDto(
            "1",
            true,
            "Алескей Иванов",
            "Геннадий Белов",
            "Как сажать картофель Лунки копают на штык лопаты. \n В каждую кладут по 1 клубню, осторожно, чтобы не поломать ростки. Затем лунки засыпают землей. \n После того, как весь картофель посажен, участок боронуют граблями.",
            MessageSendingState.Sending,
            System.currentTimeMillis(),
            null,
            MessageDisplayType.Text,
            false,
            false))

        list.add(MessageDto(
            "1",
            true,
            "Алескей Иванов",
            "Геннадий Белов",
            "yes",
            MessageSendingState.Sending,
            System.currentTimeMillis(),
            null,
            MessageDisplayType.Text,
            false,
            false))
        for (i in 0..1000) {
            list.add(MessageDto(
            "1",
            true,
            "Алескей Иванов",
            "Геннадий Белов",
            "yes",
            MessageSendingState.Sending,
            System.currentTimeMillis(),
            null,
            MessageDisplayType.Text,
            false,
            false))
               list.add(MessageDto(
            "1",
            true,
            "Алескей Иванов",
            "Геннадий Белов",
            "Как сажать картофель Лунки копают на штык лопаты. \n В каждую кладут по 1 клубню, осторожно, чтобы не поломать ростки. Затем лунки засыпают землей. \n После того, как весь картофель посажен, участок боронуют граблями.",
            MessageSendingState.Sending,
            System.currentTimeMillis(),
            null,
            MessageDisplayType.Text,
            false,
            false))
        }
        adapter.updateList(list)
      //  fillAdapter()
        initAnswer()
        initButton()

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
                    if (p0.toString() != "") {
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
        AudioRecorder.releaseRecorder()
        messageAdapter = null
        super.onDestroy()
    }

    override fun showReplyUI(position: Int) {
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
    }

    private fun sendMessage() {
        val text = binding.chatInput.text.toString().trim()
       binding.chatInput.text?.clear()
    }


}