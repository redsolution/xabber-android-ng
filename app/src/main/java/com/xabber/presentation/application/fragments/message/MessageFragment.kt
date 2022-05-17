package com.xabber.presentation.application.fragments.message

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private var quotedMessagePos = -1

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

        val messageSwipeController =
            MessageSwipeController(requireContext(), object : SwipeControllerActions {
                override fun showReplyUI(position: Int) {
                    //    quotedMessagePos = position
                  binding.answer.isVisible = true
                    // showQuotedMessage("messageList[position]")
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


    private fun showQuotedMessage(message: String) {
        //binding.answer.isVisible = true
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

        val simpleCallback = object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
                return super.getSwipeEscapeVelocity(defaultValue)
            }


            override fun getSwipeVelocityThreshold(defaultValue: Float): Float {
                return 0.5f
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX / 2,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
                val context = recyclerView.context
                val icon =
                    ResourcesCompat.getDrawable(context.resources, R.drawable.reply, context.theme)

                val itemView = viewHolder.itemView
                val typedValue = TypedValue()

                val iconMargin = (itemView.height - icon!!.intrinsicHeight) / 2
                val iconTop = itemView.top + (itemView.height - icon.intrinsicHeight) / 2
                val iconBottom = iconTop + icon.intrinsicHeight

                if (dX > 0) {
                    val iconLeft = itemView.left + iconMargin
                    val iconRight = itemView.left + iconMargin + icon.intrinsicWidth
                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

                }
            }


            override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
                return super.convertToAbsoluteDirection(flags, layoutDirection)
            }
        }
        //   val itemTouchHelper = ItemTouchHelper(simpleCallback)
        //  itemTouchHelper.attachToRecyclerView(binding.messageList)
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



}