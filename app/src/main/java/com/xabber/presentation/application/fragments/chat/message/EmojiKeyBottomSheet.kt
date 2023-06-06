package com.xabber.presentation.application.fragments.chat.message

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.FragmentEmojiKeyboardBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.manage.DisplayManager.requireArguments
import com.xabber.presentation.onboarding.fragments.signup.emoji.EmojiAvatarBottomSheet
import com.xabber.presentation.onboarding.fragments.signup.emoji.EmojiKeyAdapter
import com.xabber.presentation.onboarding.fragments.signup.emoji.EmojiKeyboardViewModel
import com.xabber.presentation.onboarding.fragments.signup.emoji.EmojiTypeAdapter
import com.xabber.utils.dp
import com.xabber.utils.setFragmentResult

class EmojiKeyBottomSheet : BottomSheetDialogFragment() {
    private val binding by viewBinding(FragmentEmojiKeyboardBinding::bind)
    private val viewModel = EmojiKeyboardViewModel()
    private var keysAdapter: EmojiKeyAdapter? = null
    private var typesAdapter: EmojiTypeAdapter? = null
    private lateinit var dataset: Map<Int, List<String>>
    private var currentEmojiType: Int? = null

    companion object {
        fun newInstance(accountId: String) = EmojiKeyBottomSheet().apply {
            arguments = Bundle().apply {
                putString("oo", accountId)
            }
        }

        val emojiTypes = mapOf(
            "smileysAndPeople" to R.string.smileysAndPeople,
            "animalsAndNature" to R.string.animalsAndNature,
            "foodAndDrink" to R.string.foodAndDrink,
            "activity" to R.string.activity,
            "travelAndPlaces" to R.string.travelAndPlaces,
            "objects" to R.string.objects,
            "symbols" to R.string.symbols,
            "flags" to R.string.flags
        )
    }

override fun getTheme(): Int = R.style.Theme_NoWiredStrapInNavigationBar
private fun getAccountId(): String =
    requireArguments().getString("oo")!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_emoji_keyboard, container, false)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { setupBottomSheet(it) }
        return dialog
    }

    private fun setupBottomSheet(dialogInterface: DialogInterface) {
        val bottomSheetDialog = dialogInterface as BottomSheetDialog
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
            ?: return
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        val behavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(
            bottomSheet
        )
        bottomSheet.updateLayoutParams {
            this.height = 360.dp
            this.width = resources.getDimension(R.dimen.container_width_onboarding).toInt()
        }
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentEmojiType = savedInstanceState?.getInt("emojiType") ?: R.string.smileysAndPeople

        binding.recyclerViewKeys.layoutManager = GridLayoutManager(context, 8)
        binding.recyclerViewKeysTypes.scrollBarFadeDuration = 0
        dataset = viewModel.getEmojiMap(resources)
        with(binding) {
            with(recyclerViewKeys) {
                adapter = EmojiKeyAdapter {
                    onEmojiClick(it)
                }.also { keysAdapter = it }
            }
            keysAdapter?.submitList(dataset[currentEmojiType])
            with(recyclerViewKeysTypes) {
                adapter = EmojiTypeAdapter {
                    onEmojiTypeClick(it)
                }.also { typesAdapter = it }
            }
            typesAdapter?.submitList(dataset.keys.toMutableList())
        }
    }

    private fun onEmojiClick(emoji: String) {
        setFragmentResult(
            AppConstants.REQUEST_EMOJI_KEY,
            bundleOf(AppConstants.RESPONSE_EMOJI_KEY to emoji)
        )
        dismiss()
        EmojiBottomSheet.newInstance(getAccountId()).show(parentFragmentManager, null)
    }

    private fun onEmojiTypeClick(emojiType: Int) {
        currentEmojiType = emojiType
        keysAdapter?.submitList(dataset[emojiType])
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("emojiType", currentEmojiType ?: R.string.smileysAndPeople)
    }

    override fun onDestroy() {
        keysAdapter = null
        typesAdapter = null
        super.onDestroy()
    }

}
