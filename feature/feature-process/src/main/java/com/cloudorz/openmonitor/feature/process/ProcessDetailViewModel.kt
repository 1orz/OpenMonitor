package com.cloudorz.openmonitor.feature.process

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.repository.ProcessRepository
import com.cloudorz.openmonitor.core.model.process.ProcessInfo
import com.cloudorz.openmonitor.core.model.process.ThreadInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProcessDetailViewModel @Inject constructor(
    private val processRepository: ProcessRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val pid: Int = savedStateHandle.get<String>("pid")?.toIntOrNull() ?: 0

    private val _process = MutableStateFlow<ProcessInfo?>(null)
    val process: StateFlow<ProcessInfo?> = _process.asStateFlow()

    private val _threads = MutableStateFlow<List<ThreadInfo>>(emptyList())
    val threads: StateFlow<List<ThreadInfo>> = _threads.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _killed = MutableStateFlow(false)
    val killed: StateFlow<Boolean> = _killed.asStateFlow()

    fun killProcess() {
        viewModelScope.launch {
            val success = processRepository.killProcess(pid)
            if (success) _killed.value = true
        }
    }

    init {
        viewModelScope.launch {
            val detail = processRepository.getProcessDetail(pid)
            _process.value = detail
            if (detail != null) {
                _threads.value = processRepository.getThreads(pid)
            }
            _loading.value = false
        }
    }
}
