package com.xabber.presentation.application.fragments.chat

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.databinding.FragmentChatSettingsBinding
import com.xabber.dto.MessageDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chat.message.MessageAdapter

class ChatSettingsFragment : DetailBaseFragment(R.layout.fragment_chat_settings),
    GradientAdapter.TryOnWallpaper {
    private val binding by viewBinding(FragmentChatSettingsBinding::bind)
    private var adapter: MessageAdapter? = null
    val list = ArrayList<MessageDto>()
    private var gradientAdapter: GradientAdapter? = null


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvChatDemonstration.layoutManager = LinearLayoutManager(requireContext())
        list.add(
            MessageDto(
                ",hjjp",
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
                "Отлично!  \uD83D\uDE0E\nПриезжай в гости" ,
                messageSendingState = MessageSendingState.Read,
                System.currentTimeMillis(),
                canEditMessage = false,
                isGroup = false,
                canDeleteMessage = false
            )
        )
        list.add(
            MessageDto(
                ",h;ljj",
                false,
                "hh",
                opponentJid = "jhg",
                "Уже лечу))))",
                messageSendingState = MessageSendingState.Sending,
                System.currentTimeMillis(),
                canEditMessage = false,
                isGroup = false,
                canDeleteMessage = false
            )
        )

        adapter = MessageAdapter(layoutInflater, messages = list, isGroup = false)
        binding.rvChatDemonstration.adapter = adapter
        binding.seekBar.progress = ChatSettingsManager.cornerValue
        binding.tvProgressValue.text = ChatSettingsManager.cornerValue.toString()
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = binding.seekBar.progress
                binding.tvProgressValue.text = value.toString()
                ChatSettingsManager.defineMessageDrawable(
                    binding.seekBar.progress,
                    ChatSettingsManager.messageTypeValue!!.rawValue, binding.bottomTails.isChecked
                )
                adapter?.notifyDataSetChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }

        })

        if (ChatSettingsManager.bottom) binding.bottomTails.isChecked =
            true else binding.topTails.isChecked = true
        binding.topTails.setOnClickListener {
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
            ChatSettingsManager.defineMessageDrawable(
                binding.seekBar.progress,
                type,
                binding.bottomTails.isChecked
            )
            adapter?.notifyDataSetChanged()
        }

        binding.bottomTails.setOnClickListener {
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
            ChatSettingsManager.defineMessageDrawable(
                binding.seekBar.progress,
                type,
                binding.bottomTails.isChecked
            )
            adapter?.notifyDataSetChanged()
        }

        binding.radioGroup.setOnCheckedChangeListener { radioGroup, checkedId ->
            radioGroup.jumpDrawablesToCurrentState()
        }
        binding.radioGroup.check(getCheckedItemId())

        binding.bubble.setOnClickListener {
            val type = 1
            ChatSettingsManager.defineMessageDrawable(
                binding.seekBar.progress,
                type,
                binding.bottomTails.isChecked
            )
            adapter?.notifyDataSetChanged()
        }

        binding.corner.setOnClickListener {
            val type = 2
            ChatSettingsManager.defineMessageDrawable(
                binding.seekBar.progress,
                type,
                binding.bottomTails.isChecked
            )
            adapter?.notifyDataSetChanged()
        }

        binding.curvy.setOnClickListener {
            val type = 3
            ChatSettingsManager.defineMessageDrawable(
                binding.seekBar.progress,
                type,
                binding.bottomTails.isChecked
            )
            adapter?.notifyDataSetChanged()
        }

        binding.smooth.setOnClickListener {
            val type = 4
            ChatSettingsManager.defineMessageDrawable(
                binding.seekBar.progress,
                type,
                binding.bottomTails.isChecked
            )
            adapter?.notifyDataSetChanged()
        }

        binding.stripes.setOnClickListener {
            val type = 5
            ChatSettingsManager.defineMessageDrawable(
                binding.seekBar.progress,
                type,
                binding.bottomTails.isChecked
            )
            adapter?.notifyDataSetChanged()
        }

        binding.wedge.setOnClickListener {
            val type = 6
            ChatSettingsManager.defineMessageDrawable(
                binding.seekBar.progress,
                type,
                binding.bottomTails.isChecked
            )
            adapter?.notifyDataSetChanged()
        }
        val drawable = when(ChatSettingsManager.gradient) {
            1 -> R.drawable.gradient_bordo
            2 -> R.drawable.gradient_red
            3 -> R.drawable.gradient_orange
            4 -> R.drawable.gradient_yellish_blue
            5 -> R.drawable.gradient_light_green
            6 -> R.drawable.gradient_light_yellish_blue
            7 -> R.drawable.gradient_blue
            8 -> R.drawable.gradient_purple
            else -> R.drawable.gradient_blue
        }
        binding.frameGradient.setBackgroundResource(drawable)
        val layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvGradients.layoutManager = layoutManager
        val list = ArrayList<Gradient>()
        list.add(Gradient(R.drawable.gradient_bordo, 1))
        list.add(Gradient(R.drawable.gradient_red, 2))
        list.add(Gradient(R.drawable.gradient_orange, 3))
        list.add(Gradient(R.drawable.gradient_yellish_blue, 4))
        list.add(Gradient(R.drawable.gradient_light_green, 5))
        list.add(Gradient(R.drawable.gradient_light_yellish_blue, 6))
        list.add(Gradient(R.drawable.gradient_blue, 7))
        list.add(Gradient(R.drawable.gradient_purple, 8))

        gradientAdapter = GradientAdapter(this, list, arrayListOf(ChatSettingsManager.gradient-1))
        binding.rvGradients.adapter = gradientAdapter

        binding.radioGroupDesign.check(getCheckedDesign())
        binding.rvChatDemonstration.setBackgroundResource(getDrawableDesign(ChatSettingsManager.designType))

        binding.space.setOnClickListener {
            ChatSettingsManager.designType = 1
            binding.rvChatDemonstration.setBackgroundResource(R.drawable.aliens_repeat)
        }

        binding.cats.setOnClickListener {
            ChatSettingsManager.designType = 2
            binding.rvChatDemonstration.setBackgroundResource(R.drawable.cats_repeat)
        }

        binding.hearts.setOnClickListener {
            ChatSettingsManager.designType = 3
            binding.rvChatDemonstration.setBackgroundResource(R.drawable.hearts_repeat)
        }

        binding.flowers.setOnClickListener {
            ChatSettingsManager.designType = 4
            binding.rvChatDemonstration.setBackgroundResource(R.drawable.flowers_repeat)
        }

        binding.meadow.setOnClickListener {
            ChatSettingsManager.designType = 5
            binding.rvChatDemonstration.setBackgroundResource(R.drawable.meadow_repeat)
        }

        binding.summer.setOnClickListener {
            ChatSettingsManager.designType = 6
            binding.rvChatDemonstration.setBackgroundResource(R.drawable.summer_repeat)
        }

    }

    private fun getCheckedDesign(): Int {
        return when (ChatSettingsManager.designType) {
            1 -> R.id.space
            2 -> R.id.cats
            3 -> R.id.hearts
            4 -> R.id.flowers
            5 -> R.id.meadow
            6 -> R.id.summer
            else -> {
                R.id.space
            }
        }
    }

    private fun getDrawableDesign(type: Int): Int {
        return when (type) {
            1 -> R.drawable.aliens_repeat
            2 -> R.drawable.cats_repeat
            3 -> R.drawable.hearts_repeat
            4 -> R.drawable.flowers_repeat
            5 -> R.drawable.meadow_repeat
            6 -> R.drawable.summer_repeat
            else -> R.drawable.aliens_repeat
        }
    }

    private fun getCheckedItemId(): Int {
        return when (ChatSettingsManager.messageTypeValue?.rawValue) {
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        navigator().setDesignBackground()
        saveSettings()
    }

    private fun saveSettings() {
        val cornerPref =
            activity?.getSharedPreferences(AppConstants.SHARED_PREF_CORNER, Context.MODE_PRIVATE)
                ?: return
        val typePref =
            activity?.getSharedPreferences(AppConstants.SHARED_PREF_TYPE, Context.MODE_PRIVATE)
                ?: return
        val botPref =
            activity?.getSharedPreferences(AppConstants.SHARED_PREF_TAIL_POSITION, Context.MODE_PRIVATE)
                ?: return
        val gradientPref =
            activity?.getSharedPreferences(AppConstants.SHARED_PREF_GRADIENT, Context.MODE_PRIVATE) ?: return
        val designPref = activity?.getSharedPreferences(AppConstants.SHARED_PREF_CHAT_DESIGN, Context.MODE_PRIVATE) ?: return

        val bi = binding.bottomTails.isChecked
        cornerPref.edit()?.putInt(AppConstants.CORNER_KEY, binding.seekBar.progress)?.apply()
        typePref.edit()?.putInt(AppConstants.TYPE_TAIL_KEY, getTypeChecked())?.apply()
        botPref.edit()?.putBoolean(AppConstants.TAIL_POSITION, bi)?.apply()
        gradientPref.edit()?.putInt(AppConstants.GRADIENT, ChatSettingsManager.gradient)?.apply()
        designPref.edit()?.putInt(AppConstants.CHAT_DESIGN_TYPE, getDesign())?.apply()
    }

    override fun onClickElement(gradient: Gradient) {
        binding.frameGradient.setBackgroundResource(gradient.background)
        ChatSettingsManager.gradient = gradient.value
    }

    private fun getDesign(): Int {
        return when(binding.radioGroupDesign.checkedRadioButtonId) {
            R.id.space -> 1
            R.id.cats -> 2
            R.id.hearts -> 3
            R.id.flowers -> 4
            R.id.meadow -> 5
            R.id.summer -> 6
            else -> 1
        }
    }


}
