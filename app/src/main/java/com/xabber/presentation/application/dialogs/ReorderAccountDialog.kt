package com.xabber.presentation.application.dialogs

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentReorderAccountBinding
import com.xabber.dto.AccountDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.account.reorder.ReorderAccountAdapter
import com.xabber.presentation.application.fragments.account.reorder.ReorderAccountsViewModel
import com.xabber.presentation.application.fragments.account.reorder.SimpleItemTouchHelperCallback

class ReorderAccountsDialog : DialogFragment(R.layout.fragment_reorder_account) {
    private val binding by viewBinding(FragmentReorderAccountBinding::bind)
    private var reorderAccountAdapter: ReorderAccountAdapter? = null
    private var touchHelper: ItemTouchHelper? = null
    private val viewModel: ReorderAccountsViewModel by viewModels()
    private var accounts = ArrayList<AccountDto>()

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val width = (resources.displayMetrics.widthPixels * 0.8).toInt() // 90% of screen width
            val height = (resources.displayMetrics.heightPixels * 0.95).toInt()
            dialog.window?.setLayout(width, height)
            dialog.window?.setGravity(Gravity.CENTER) // Center the dialog
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reorder_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        accounts =
            if (savedInstanceState != null) {
                ((if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    savedInstanceState.getParcelableArrayList(
                        AppConstants.REORDER_ACCOUNTS,
                        AccountDto::class.java
                    )
                        ?: ArrayList()
                } else {
                    savedInstanceState.getParcelableArrayList(AppConstants.REORDER_ACCOUNTS)
                        ?: ArrayList()
                }))
            } else ArrayList(viewModel.getAccounts())
        initToolbarActions()
        initAccountList()
    }

    private fun initToolbarActions() {
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.check -> {
                    saveAccountOrder()
                    navigator().goBack()
                }
            }
            true
        }
    }

    private fun saveAccountOrder() {
        val accounts = reorderAccountAdapter?.accounts ?: return
        viewModel.changeAccountOrder(accounts)
    }

    private fun initAccountList() {
        binding.rvReorderAccounts.layoutManager = LinearLayoutManager(context)
        reorderAccountAdapter = ReorderAccountAdapter(accounts) { onStartDrag(it) }
        binding.rvReorderAccounts.adapter = reorderAccountAdapter
        addTouchHelper()
    }

    private fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        touchHelper?.startDrag(viewHolder)
    }

    private fun addTouchHelper() {
        val callback = SimpleItemTouchHelperCallback(reorderAccountAdapter)
        touchHelper = ItemTouchHelper(callback)
        touchHelper?.attachToRecyclerView(binding.rvReorderAccounts)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(AppConstants.REORDER_ACCOUNTS,
            reorderAccountAdapter?.accounts?.let { ArrayList(it) })
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.rvReorderAccounts.adapter = null
    }

}
