package com.xabber.presentation.application.fragments.contacts

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter

class StatusModeAdapter : BaseAdapter() {
    private val statusModes = ArrayList<StatusMode>()

    init {
        statusModes.add(StatusMode.CHAT)
        statusModes.add(StatusMode.AVAILABLE)
        statusModes.add(StatusMode.AWAY)
        statusModes.add(StatusMode.XA)
        statusModes.add(StatusMode.DND)
        statusModes.add(StatusMode.UNAVAILABLE)
    }

    override fun getCount(): Int = statusModes.size

    override fun getItem(position: Int): Any = statusModes[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, view: View?, parent: ViewGroup?): View {
    return view!!
    }
}