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
                    MessageChanger.typeValue, !binding.switchSide.isChecked
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
                    R.id.bubble -> 1
                    R.id.corner -> 2
                    R.id.curvy -> 3
                    R.id.smooth -> 4
                    R.id.stripes -> 5
                    R.id.wedge -> 6
                    else -> {
                        4
                    }
                }
                MessageChanger.defineMessageDrawable(binding.seekBar.progress, type, !binding.switchSide.isChecked)
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
                    R.id.corner -> 2
                    R.id.curvy -> 3
                    R.id.smooth -> 4
                    R.id.stripes -> 5
                    R.id.wedge -> 6
                    else -> {
                        4
                    }
                }
                MessageChanger.defineMessageDrawable(binding.seekBar.progress, type, !binding.switchSide.isChecked)
                adapter?.notifyDataSetChanged()
            }

        }


        binding.radioGroup.setOnCheckedChangeListener { radioGroup, checkedId ->
            radioGroup.jumpDrawablesToCurrentState()
        }
        binding.radioGroup.check(getCheckedItemId())

        binding.bubble.setOnClickListener {
            val type =  1
            MessageChanger.defineMessageDrawable(binding.seekBar.progress, type, !binding.switchSide.isChecked)
            adapter?.notifyDataSetChanged()
        }

        binding.corner.setOnClickListener {
            val type = 2
            MessageChanger.defineMessageDrawable(binding.seekBar.progress, type, !binding.switchSide.isChecked)
            adapter?.notifyDataSetChanged()
        }

        binding.curvy.setOnClickListener {
            val type = 3
            MessageChanger.defineMessageDrawable(binding.seekBar.progress, type, !binding.switchSide.isChecked)
            adapter?.notifyDataSetChanged()
        }

        binding.smooth.setOnClickListener {
            val type = 4
            MessageChanger.defineMessageDrawable(binding.seekBar.progress, type, !binding.switchSide.isChecked)
            adapter?.notifyDataSetChanged()
        }

        binding.stripes.setOnClickListener {
            val type = 5
            MessageChanger.defineMessageDrawable(binding.seekBar.progress, type, !binding.switchSide.isChecked)
            adapter?.notifyDataSetChanged()
        }

        binding.wedge.setOnClickListener {
            val type = 6
            MessageChanger.defineMessageDrawable(binding.seekBar.progress, type, !binding.switchSide.isChecked)
            adapter?.notifyDataSetChanged()
        }
    }

    private fun getCheckedItemId(): Int {
        return when (MessageChanger.typeValue) {
            1 -> R.id.bubble
            2 -> R.id.corner
            3 -> R.id.curvy
            4 -> R.id.smooth
            5 -> R.id.stripes
            6 -> R.id.wedge
            else -> {
                R.id.smooth
            }
        }
    }

    private fun getTypeChecked(): Int {
        return when (binding.radioGroup.checkedRadioButtonId) {
            R.id.bubble ->  1
            R.id.corner -> 2
            R.id.curvy -> 3
            R.id.smooth -> 4
            R.id.stripes -> 5
            R.id.wedge -> 6
            else -> {
                4
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
        val botPref =
            activity?.getSharedPreferences("bottom", Context.MODE_PRIVATE)
                ?: return
        val bi = !binding.switchSide.isChecked
        cornerPref.edit()?.putInt(AppConstants.CORNER_KEY, binding.seekBar.progress)?.apply()
        typePref.edit()?.putInt(AppConstants.TYPE_TAIL_KEY, getTypeChecked())?.apply()
        botPref.edit()?.putBoolean("bot", bi)?.apply()
    }

}
