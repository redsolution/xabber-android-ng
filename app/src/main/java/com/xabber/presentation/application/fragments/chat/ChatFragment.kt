package com.xabber.presentation.application.fragments.chat

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.databinding.FragmentChatBinding
import com.xabber.presentation.application.contract.applicationToolbarChanger
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.message.MessageFragment
import com.xabber.presentation.onboarding.fragments.signup.AvatarBottomSheet
import io.reactivex.rxjava3.internal.disposables.DisposableHelper.replace


class ChatFragment() : Fragment(), ChatAdapter.ShowMessage {
    private var binding: FragmentChatBinding? = null
    lateinit var userName: String
    private val viewModel = ChatViewModel()
     private var chatAdapter: ChatAdapter? = null

    companion object {
        fun newInstance(_userName: String) = ChatFragment().apply {
            userName = _userName
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applicationToolbarChanger().showNavigationView(true)
        // binding?.tvUserName?.text = userName
        applicationToolbarChanger().setShowBack(true)
        applicationToolbarChanger().setTitle(R.string.bottom_nav_chat_label)
        val adapter = ChatAdapter(this)
binding?.chatList?.adapter = adapter
        adapter.submitList(viewModel.chat.sortedBy { !it.isPinned })

val simpleCallback = object :
ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        when (direction) {
            ItemTouchHelper.LEFT -> {
                Toast.makeText(context, "Left", Toast.LENGTH_SHORT).show()
            }
            ItemTouchHelper.RIGHT -> {
                Toast.makeText(context, "Right", Toast.LENGTH_SHORT).show()

                adapter.notifyItemChanged(viewHolder.absoluteAdapterPosition)
            }
        }
    }

    override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {

        return super.convertToAbsoluteDirection(flags, layoutDirection)
    }
}
  val  itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(binding?.chatList)
// binding?.chatList?.setOnTouchListener { view, motionEvent ->  }
}

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {

        super.onCreateContextMenu(menu, v, menuInfo)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return super.onContextItemSelected(item)
    }



         //   chatAdapter!!.submitList(viewModel.chat.sortedBy { !it.isPinned })


    override fun onClick() {
        navigator().goToMessage()
    }

    override fun onClickMenu() {
        NotificationBottomSheet().show(parentFragmentManager, null)
    }
}
