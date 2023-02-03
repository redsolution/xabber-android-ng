package com.xabber.presentation.application.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewModelScope
import com.google.android.material.appbar.AppBarLayout
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.AccountDto
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.presentation.application.activity.ColorManager
//import com.xabber.model.xmpp.account.AccountViewModel
import com.xabber.presentation.application.activity.DisplayManager
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseFragment(@LayoutRes contentLayoutId: Int) : Fragment(contentLayoutId) {
    private var appbar: AppBarLayout? = null
    private var color: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appbar = view.findViewById(R.id.appbar)
        setupAppbarPadding()
        val realm = Realm.open(defaultRealmConfig())
        realm.writeBlocking {
            val acc = this.query(AccountStorageItem::class).first().find()
            color = acc!!.colorKey
        }

        val colorInt = ColorManager.convertColorNameToId(color)
        Log.d("color", " base = $colorInt")
        //if (accountViewModel.primaryColor.value != null) accountViewModel.primaryColor.value else
        //  R.color.blue_500
        setupColor(colorInt)
        GlobalScope.launch {
            val request =
                realm.query(AccountStorageItem::class)
            request.asFlow().collect { changes: ResultsChange<AccountStorageItem> ->
                when (changes) {

                    is UpdatedResults -> {
                        Log.d("bbb", "chsnges")
                      val k =  changes.list[0].colorKey
                        val colord = ColorManager.convertColorNameToId(k)
                        setupColor(colord)

                    }
                    else -> {}
                }
            }

        }


    }

    private fun setupAppbarPadding() {
        if (appbar != null) appbar!!.setPadding(0, DisplayManager.getHeightStatusBar(), 0, 0)
    }

    private fun setupColor(colorId: Int) {
        appbar?.setBackgroundResource(colorId)
    }






}
