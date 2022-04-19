package com.xabber.presentation.application.fragments.message

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.xabber.R
import com.xabber.databinding.FragmentMessageBinding
import com.xabber.presentation.application.contract.FragmentAction
import com.xabber.presentation.application.contract.applicationToolbarChanger
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger


class MessageFragment : Fragment() {
    private var binding: FragmentMessageBinding? = null
    private var messageAdapter: MessageAdapter? = null
    private val viewModel = MessageViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMessageBinding.inflate(inflater, container, false)
        return binding!!.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applicationToolbarChanger().showNavigationView(false)
        initToolbarActions()
        // binding?.tvUserName?.text = userName
        //   applicationToolbarChanger().toolbarIconChange(FragmentAction(R.drawable.ic_material_check_24, R.string.signup_username_subtitle))
        initNavigationBar()
        val lm = LinearLayoutManager(requireContext())
        lm.reverseLayout = true
        with(binding?.messageList) {
            this?.layoutManager = lm
            this?.adapter = MessageAdapter().also { messageAdapter = it }
        }

        messageAdapter!!.submitList(viewModel.dataset)
    }

    private fun initToolbarActions() {
        binding?.messageIconBack?.setOnClickListener {
            navigator().goBack()
        }
    }

    private fun initNavigationBar() {


        with(binding!!) {
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
     override fun onDestroy() {
        super.onDestroy()
        applicationToolbarChanger().showNavigationView(true)
    }

}