package com.cloudorz.openmonitor.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.repository.NetworkRepository
import com.cloudorz.openmonitor.core.model.network.NetworkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NetworkViewModel @Inject constructor(
    networkRepository: NetworkRepository,
) : ViewModel() {

    val networkInfo: StateFlow<NetworkInfo> = networkRepository
        .observeNetworkInfo(2000L)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NetworkInfo(),
        )
}
