package com.xabber.presentation.application.fragments.account.qrcode

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class QRCodeParams(val name: String, val jid: String, val color: Int) : Parcelable