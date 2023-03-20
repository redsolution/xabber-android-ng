package com.xabber.presentation.application.fragments.contacts.vcard

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class ContactAccountParams(val id: String, val avatar: Int?): Parcelable {
}