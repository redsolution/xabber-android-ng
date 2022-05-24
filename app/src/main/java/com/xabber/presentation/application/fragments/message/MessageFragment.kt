package com.xabber.presentation.application.fragments.message

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentMessageBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment


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

        val lm = LinearLayoutManager(requireContext())
        lm.reverseLayout = true
        with(binding.messageList) {
            this.layoutManager = lm
            this.adapter = MessageAdapter().also { messageAdapter = it }
        }
        fillAdapter()
        initAnswer()

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


}