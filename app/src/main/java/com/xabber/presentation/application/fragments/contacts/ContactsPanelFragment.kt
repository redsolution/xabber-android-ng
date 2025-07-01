package com.xabber.presentation.application.fragments.contacts

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentContactsPanelBinding
import com.xabber.dto.GroupDto
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.manage.DisplayManager

class ContactsPanelFragment : BaseFragment(R.layout.fragment_contacts_panel), GroupAdapter.Listener {
    private val binding by viewBinding(FragmentContactsPanelBinding::bind)
    private val groupViewModel by lazy { GroupViewModel() }
    private val contactsViewModel: ContactsViewModel by activityViewModels()
    private var groupAdapter: GroupAdapter? = null
    private val activeFragment: Fragment?
        get() = childFragmentManager.findFragmentById(R.id.detail_container)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.contactsToolbar.navigationIcon = null
        activeLinks()
        filtersActions()
        initGroupList()
        subscribeViewModel()
        ifOrientationIsPortrait()
        binding.contactsToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.contactsToolbar.setNavigationOnClickListener { navigator().goBack() }
    }

    private fun initGroupList() {
        groupAdapter = GroupAdapter(this)
        binding.groupsRecyclerView.adapter = groupAdapter
        groupViewModel.getGroupList()
    }

    private fun subscribeViewModel() {
        groupViewModel.groupList.observe(viewLifecycleOwner) {
            groupAdapter?.submitList(it)
        }
        groupViewModel.initDataListener()
    }

    private fun ifOrientationIsPortrait() {
        // Existing logic unchanged
    }

    private fun filtersActions() {
        binding.contactsFilterLayout.setOnClickListener {
            contactsViewModel.showAllContacts()
            // Notify ContactsFragment to reset selectedGroup
            parentFragmentManager.setFragmentResult(
                "group_filter",
                Bundle().apply { putString("selected_group", null) }
            )
        }
    }

    private fun activeLinks() {
        binding.tvAdvertisement.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onGroupClick(group: GroupDto) {
        contactsViewModel.filterContactsByGroup(group.name)
        // Notify ContactsFragment of selected group
        parentFragmentManager.setFragmentResult(
            "group_filter",
            Bundle().apply { putString("selected_group", group.groupName) }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        groupAdapter = null
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        groupAdapter?.notifyDataSetChanged()
    }
}