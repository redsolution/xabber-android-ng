package com.xabber.presentation.application.fragments.chat.geo

import android.content.Context
import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider

class ObservableOsmLocationProvider(context: Context): GpsMyLocationProvider(context) {
    private val _stateLiveData = MutableLiveData<LocationState>()
    val stateLiveData: LiveData<LocationState> get() = _stateLiveData

    override fun onLocationChanged(location: Location) {
        _stateLiveData.value = LocationState.LocationReceived
        super.onLocationChanged(location)
    }

    override fun onProviderDisabled(provider: String) {
        super.onProviderDisabled(provider)
        _stateLiveData.value = LocationState.LocationNotFound
    }

    enum class LocationState {
        LocationReceived, LocationNotFound
    }
}