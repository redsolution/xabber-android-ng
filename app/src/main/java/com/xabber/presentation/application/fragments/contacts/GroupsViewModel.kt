package com.xabber.presentation.application.fragments.contacts

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.roster.RosterGroupStorageItem
import com.xabber.dto.GroupDto
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GroupViewModel : ViewModel() {
    private val realm = Realm.open(defaultRealmConfig())
    private val _groupList = MutableLiveData<List<GroupDto>>()
    val groupList: LiveData<List<GroupDto>> = _groupList
    private var groups = ArrayList<GroupDto>()

    fun initDataListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val request = realm.query(RosterGroupStorageItem::class, "isSystemGroup = false")
            request.asFlow().collect { changes: ResultsChange<RosterGroupStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        val dataSource = ArrayList<GroupDto>()
                        dataSource.addAll(changes.list.map { group ->
                            GroupDto(
                                primary = group.primary,
                                owner = group.owner,
                                name = group.name,
                                groupName = group.groupName,
                                isSystemGroup = group.isSystemGroup,
                                isCollapsed = group.isCollapsed,
                                order = group.order,
                                contactCount = group.contacts.size
                            )
                        })
                        groups = dataSource
                        groups.sort()
                        launch(Dispatchers.Main) {
                            _groupList.postValue(groups)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun getGroupList() {
        viewModelScope.launch(Dispatchers.IO) {
            val realmList = realm.query(RosterGroupStorageItem::class, "isSystemGroup = false").find()
            val dataSource = ArrayList<GroupDto>()
            dataSource.addAll(realmList.map { group ->
                GroupDto(
                    primary = group.primary,
                    owner = group.owner,
                    name = group.name,
                    groupName = group.groupName,
                    isSystemGroup = group.isSystemGroup,
                    isCollapsed = group.isCollapsed,
                    order = group.order,
                    contactCount = group.contacts.size
                )
            })
            groups = dataSource
            groups.sort()
            withContext(Dispatchers.Main) {
                _groupList.value = groups
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }
}