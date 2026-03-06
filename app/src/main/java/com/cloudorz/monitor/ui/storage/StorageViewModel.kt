package com.cloudorz.monitor.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.monitor.core.data.repository.StorageRepository
import com.cloudorz.monitor.core.model.storage.StorageInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(
    storageRepository: StorageRepository,
) : ViewModel() {

    val storageInfo: StateFlow<StorageInfo> = storageRepository
        .observeStorageInfo(5000L)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StorageInfo(),
        )
}
