package com.cloudorz.openmonitor.feature.hardware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.repository.StorageRepository
import com.cloudorz.openmonitor.core.model.storage.MountInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartitionUiState(
    val mounts: List<MountInfo> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class PartitionViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartitionUiState())
    val uiState: StateFlow<PartitionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val mounts = storageRepository.getAllMounts()
                _uiState.value = PartitionUiState(mounts = mounts, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = PartitionUiState(isLoading = false)
            }
        }
    }
}
