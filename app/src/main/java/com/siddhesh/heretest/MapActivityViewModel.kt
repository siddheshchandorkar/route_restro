package com.siddhesh.heretest

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData

class MapActivityViewModel( application: Application): AndroidViewModel(application) {
    var source=MutableLiveData("")
    var destination=MutableLiveData("")
    var currentLocation=MutableLiveData<Location>()
}