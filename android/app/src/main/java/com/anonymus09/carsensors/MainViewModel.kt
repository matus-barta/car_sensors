package com.anonymus09.carsensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anonymus09.carsensors.data.TelemetryDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModelProvider

class MainViewModel(
    private val dao: TelemetryDao
) : ViewModel() {
    val pendingCount = dao.getPendingCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastUploadTime = dao.getLastUploadTimeFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}


class MainViewModelFactory(
    private val dao: TelemetryDao
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

