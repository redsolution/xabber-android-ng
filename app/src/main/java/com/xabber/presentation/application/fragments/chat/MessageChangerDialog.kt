package com.xabber.presentation.application.fragments.chat

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.databinding.DialogMessageChangerBinding
import com.xabber.dto.MessageDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.test.MessageAdapter

class MessageChangerDialog : DetailBaseFragment(R.layout.dialog_message_changer) {
    private val binding by viewBinding(DialogMessageChangerBinding::bind)
    private var adapter: MessageAdapter? = null
    val list = ArrayList<MessageDto>()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvChatDemonstration.layoutManager = LinearLayoutManager(requireContext())
        list.add(
            MessageDto(
                ",hjj",
                false,
                "hh",
                opponentJid = "jhg",
                "Как дела?",
                messageSendingState = MessageSendingState.Sending,
                System.currentTimeMillis(),
                canEditMessage = false,
                isGroup = false,
                canDeleteMessage = false
            )
        )
        list.add(
            MessageDto(
                ",hjj",
                true,
                "hh",
                opponentJid = "jhg",
                "Отлично!  \uD83D\uDE0E",
                messageSendingState = MessageSendingState.Read,
                System.currentTimeMillis(),
                canEditMessage = false,
                isGroup = false,
                canDeleteMessage = false
            )
        )
        adapter = MessageAdapter(context = requireContext(), messageRealmObjects = list)
        binding.rvChatDemonstration.adapter = adapter
        binding.seekBar.progress = MessageChanger.cornerValue
        binding.tvProgressValue.text = MessageChanger.cornerValue.toString()
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = binding.seekBar.progress
                binding.tvProgressValue.text = value.toString()
                MessageChanger.defineMessageDrawable(
                    binding.seekBar.progress,
                    MessageChanger.typeValue
                )
//                Log.d("sss","adapter = $adapter")
//                val g = ArrayList<MessageDto>()
//                g.addAll(list)
//                adapter?.submitList(g)
//                binding.rvExampleChat.invalidate()
                adapter?.notifyDataSetChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }

        })

        if (MessageChanger.typeValue % 2 == 0) {
            binding.tvTop.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_500))
            binding.tvBottom.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.grey_600
                )
            )
        } else {
            binding.tvBottom.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.blue_500
                )
            )
            binding.tvTop.setTextColor(ContextCompat.getColor(requireContext(), R.color.grey_600))
        }

        binding.switchSide.isChecked = MessageChanger.typeValue % 2 == 0
        binding.switchSide.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.tvTop.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.blue_500
                    )
                )
                binding.tvBottom.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.grey_600
                    )
                )
                val type = when (binding.radioGroup.checkedRadioButtonId) {
                    R.id.bubble -> 2
                    R.id.corner -> 4
                    R.id.curvy -> 6
                    R.id.smooth -> 8
                    R.id.stripes -> 10
                    R.id.wedge -> 12
                    else -> {
                        8
                    }
                }
                MessageChanger.defineMessageDrawable(binding.seekBar.progress, type)
                adapter?.notifyDataSetChanged()
            } else {
                binding.tvBottom.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.blue_500
                    )
                )
                binding.tvTop.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.grey_600
                    )
                )
                val type = when (binding.radioGroup.checkedRadioButtonId) {
                    R.id.bubble -> 1
                    R.id.corner -> 3
                    R.id.curvy -> 5
                    R.id.smooth -> 7
                    R.id.stripes -> 9
                    R.id.wedge -> 11
                    else -> {
                        7
                    }
                }
                MessageChanger.defineMessageDrawable(binding.seekBar.progress, type)
                adapter?.notifyDataSetChanged()
            }

        }


        binding.radioGroup.setOnCheckedChangeListener { radioGroup, checkedId ->
            radioGroup.jumpDrawablesToCurrentState()
        }
        binding.radioGroup.check(getCheckedItemId())

        binding.bubble.setOnClickListener {
            val type = if (binding.switchSide.isChecked) 2 else 1
            MessageChanger.defineMessageDrawable(binding.seekBar.progress, type)
            adapter?.notifyDataSetChanged()
        }

        binding.corner.setOnClickListener {
            val type = if (binding.switchSide.isChecked) 4 else 3
            MessageChanger.defineMessageDrawable(binding.seekBar.progress, type)
            adapter?.notifyDataSetChanged()
        }

        binding.curvy.setOnClickListener {
            val type = if (binding.switchSide.isChecked) 6 else 5
            MessageChanger.defineMessageDrawable(binding.seekBar.progress, type)
            adapter?.notifyDataSetChanged()
        }

        binding.smooth.setOnClickListener {
            val type = if (binding.switchSide.isChecked) 8 else 7
            MessageChanger.defineMessageDrawable(binding.seekBar.progress, type)
            adapter?.notifyDataSetChanged()
        }

        binding.stripes.setOnClickListener {
            val type = if (binding.switchSide.isChecked) 10 else 9
            MessageChanger.defineMessageDrawable(binding.seekBar.progress, type)
            adapter?.notifyDataSetChanged()
        }

        binding.wedge.setOnClickListener {
            val type = if (binding.switchSide.isChecked) 12 else 11
            MessageChanger.defineMessageDrawable(binding.seekBar.progress, type)
            adapter?.notifyDataSetChanged()
        }
    }

    private fun getCheckedItemId(): Int {
        return when (MessageChanger.typeValue) {
            1 -> R.id.bubble
            2 -> R.id.bubble
            3 -> R.id.corner
            4 -> R.id.corner
            5 -> R.id.curvy
            6 -> R.id.curvy
            7 -> R.id.smooth
            8 -> R.id.smooth
            9 -> R.id.stripes
            10 -> R.id.stripes
            11 -> R.id.wedge
            12 -> R.id.wedge
            else -> {
                R.id.smooth
            }
        }
    }

    private fun getTypeChecked(): Int {
        return when (binding.radioGroup.checkedRadioButtonId) {
            R.id.bubble -> if (binding.switchSide.isChecked) 2 else 1
            R.id.corner -> if (binding.switchSide.isChecked) 4 else 3
            R.id.curvy -> if (binding.switchSide.isChecked) 6 else 5
            R.id.smooth -> if (binding.switchSide.isChecked) 8 else 7
            R.id.stripes -> if (binding.switchSide.isChecked) 10 else 9
            R.id.wedge -> if (binding.switchSide.isChecked) 12 else 11
            else -> {
                if (binding.switchSide.isChecked) 8 else 7
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val cornerPref =
            activity?.getSharedPreferences(AppConstants.SHARED_PREF_CORNER, Context.MODE_PRIVATE)
                ?: return
        val typePref =
            activity?.getSharedPreferences(AppConstants.SHARED_PREF_TYPE, Context.MODE_PRIVATE)
                ?: return
        cornerPref.edit()?.putInt(AppConstants.CORNER_KEY, binding.seekBar.progress)?.apply()
        typePref.edit()?.putInt(AppConstants.TYPE_TAIL_KEY, getTypeChecked())?.apply()
    }


}