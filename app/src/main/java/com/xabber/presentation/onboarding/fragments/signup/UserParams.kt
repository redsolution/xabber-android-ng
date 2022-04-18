package com.xabber.presentation.onboarding.fragments.signup

import android.os.Parcel
import android.os.Parcelable

data class UserParams(val username: String, val host: String) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(username)
        parcel.writeString(host)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<UserParams> {
        override fun createFromParcel(parcel: Parcel): UserParams {
            return UserParams(parcel)
        }

        override fun newArray(size: Int): Array<UserParams?> {
            return arrayOfNulls(size)
        }
    }
}