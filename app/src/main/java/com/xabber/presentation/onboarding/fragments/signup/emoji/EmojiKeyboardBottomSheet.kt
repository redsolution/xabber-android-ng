package com.xabber.presentation.onboarding.fragments.signup.emoji

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.updateLayoutParams
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.FragmentEmojiKeyboardBinding
import com.xabber.presentation.application.util.AppConstants
import com.xabber.presentation.application.util.dp
import com.xabber.presentation.application.util.setFragmentResult

class EmojiKeyboardBottomSheet : BottomSheetDialogFragment() {
    private val binding by viewBinding(FragmentEmojiKeyboardBinding::bind)
    private val viewModel = EmojiKeyboardViewModel()
    private var keysAdapter: EmojiKeyAdapter? = null
    private var typesAdapter: EmojiTypeAdapter? = null
    private lateinit var dataset: Map<Int, List<String>>

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

        dataset = viewModel.getEmojiMap(resources)
        with(binding) {
            with(recyclerViewKeys) {
                adapter = EmojiKeyAdapter {
                    onEmojiClick(it)
                }.also { keysAdapter = it }
            }
            keysAdapter!!.submitList(dataset[R.drawable.smileysandpeople])
            with(recyclerViewKeysTypes) {
                adapter = EmojiTypeAdapter {
                    onEmojiTypeClick(it)
                }.also { typesAdapter = it }
            }
            typesAdapter!!.submitList(dataset.keys.toMutableList())
        }
    }

    override fun onDestroy() {
        keysAdapter = null
        typesAdapter = null
        super.onDestroy()
    }

    private fun onEmojiClick(emoji: String) {
        setFragmentResult(
            AppConstants.REQUEST_EMOJI_KEY,
            bundleOf(AppConstants.RESPONSE_EMOJI_KEY to emoji)
        )
        dismiss()
        EmojiAvatarBottomSheet().show(parentFragmentManager, null)
    }

    private fun onEmojiTypeClick(emojiType: Int) {
        keysAdapter?.submitList(dataset[emojiType])
    }

    companion object {

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
}