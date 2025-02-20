package com.xabber.presentation.application.fragments.contacts

import android.content.Context
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class ContactAccountParams(val id: String, val avatar: Int?,): Parcelable

