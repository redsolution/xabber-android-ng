package com.xabber.presentation.application.fragments.chat

import android.content.Intent
import android.os.Bundle
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentSearchBinding
import com.xabber.domain.entity.AccountJid
import com.xabber.presentation.application.fragments.DetailBaseFragment

class SearchFragment: DetailBaseFragment(R.layout.fragment_search) {
    private val binding by viewBinding(FragmentSearchBinding::bind)
    private var action: String? = null
    private var forwardedIds: ArrayList<String>? = null
    private var accountJid: AccountJid? = null
    private var sendText: String? = null

    companion object {

        /* Constants for in app Intents */
        private const val ACTION_FORWARD =
            "com.xabber.android.ui.activity.SearchActivity.ACTION_FORWARD"
        private const val ACTION_SEARCH =
            "com.xabber.android.ui.activity.SearchActivity.ACTION_SEARCH"

        /* Intent extras ids */
        private const val FORWARDED_IDS_EXTRA =
            "com.xabber.android.ui.activity.SearchActivity.FORWARDED_IDS_EXTRA"

        /* Constants for saving state bundle */
        private const val SAVED_ACTION =
            "com.xabber.android.ui.activity.SearchActivity.SAVED_ACTION"
        private const val SAVED_FORWARDED_IDS =
            "com.xabber.android.ui.activity.SearchActivity.SAVED_FORWARDED_IDS"
        private const val SAVED_SEND_TEXT =
            "com.xabber.android.ui.activity.SearchActivity.SAVED_SEND_TEXT"

//        fun createSearchIntent(context: Context) =
//            Intent(context, SearchActivity::class.java).also { it.action = ACTION_SEARCH }
//
//        fun createForwardIntent(
//            context: Context, accountJid: AccountJid, forwardedIds: List<String>
//        ) = createAccountIntent(context, SearchActivity::class.java, accountJid).apply {
//                action = ACTION_FORWARD
//                putStringArrayListExtra(FORWARDED_IDS_EXTRA, ArrayList(forwardedIds))
//        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.apply {
            action?.let { putString(SAVED_ACTION, it) }
            forwardedIds?.let { putStringArrayList(SAVED_FORWARDED_IDS, it) }
            sendText?.let { putString(SAVED_SEND_TEXT, it) }
        }
        super.onSaveInstanceState(outState)
    }

    private fun initActions(savedInstanceState: Bundle?, intent: Intent?) {
        if (savedInstanceState != null) {
            savedInstanceState.getString(SAVED_ACTION)?.let { action = it }
            savedInstanceState.getStringArrayList(SAVED_FORWARDED_IDS)?.let { forwardedIds = it }
            savedInstanceState.getString(SAVED_SEND_TEXT)?.let { sendText = it }
        } else {
            action = intent?.action
            forwardedIds = intent?.getStringArrayListExtra(FORWARDED_IDS_EXTRA)
            sendText = intent?.getStringExtra(Intent.EXTRA_TEXT)
            //  accountJid = intent?.getAccountJid()
        }
    }

}

