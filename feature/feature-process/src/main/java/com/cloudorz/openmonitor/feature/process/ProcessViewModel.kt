package com.cloudorz.openmonitor.feature.process

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.repository.ProcessRepository
import com.cloudorz.openmonitor.core.model.process.ProcessInfo
import com.cloudorz.openmonitor.core.model.process.ThreadInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortBy { CPU, MEMORY, NAME, PID }

data class ProcessUiState(
    val processes: List<ProcessInfo> = emptyList(),
    val filteredProcesses: List<ProcessInfo> = emptyList(),
    val selectedProcess: ProcessInfo? = null,
    val threads: List<ThreadInfo> = emptyList(),
    val searchQuery: String = "",
    val sortBy: SortBy = SortBy.CPU,
    val isLoading: Boolean = true,
)

@HiltViewModel
class ProcessViewModel @Inject constructor(
    private val processRepository: ProcessRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sortBy = MutableStateFlow(SortBy.CPU)
    private val selectedProcess = MutableStateFlow<ProcessInfo?>(null)
    private val threads = MutableStateFlow<List<ThreadInfo>>(emptyList())

    private val processFlow = processRepository.observeProcessList(3000L)

    val uiState: StateFlow<ProcessUiState> = combine(
        processFlow,
        searchQuery,
        sortBy,
        selectedProcess,
        threads,
    ) { processes, query, sort, selected, threadList ->
        val filtered = processes
            .filter { process ->
                if (query.isBlank()) true
                else {
                    val lowerQuery = query.lowercase()
                    process.name.lowercase().contains(lowerQuery) ||
                        process.cmdline.lowercase().contains(lowerQuery) ||
                        process.displayName.lowercase().contains(lowerQuery) ||
                        process.pid.toString().contains(lowerQuery)
                }
            }
            .sortedWith(
                when (sort) {
                    SortBy.CPU -> compareByDescending { it.cpuPercent }
                    SortBy.MEMORY -> compareByDescending { it.rssKB }
                    SortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                    SortBy.PID -> compareBy { it.pid }
                }
            )

        val refreshedSelected = if (selected != null) {
            processes.firstOrNull { it.pid == selected.pid } ?: selected
        } else null

        ProcessUiState(
            processes = processes,
            filteredProcesses = filtered,
            selectedProcess = refreshedSelected,
            threads = threadList,
            searchQuery = query,
            sortBy = sort,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProcessUiState(),
    )

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onSortByChanged(sort: SortBy) {
        sortBy.value = sort
    }

    fun onProcessSelected(process: ProcessInfo) {
        selectedProcess.value = process
        threads.value = emptyList()
        viewModelScope.launch {
            threads.value = processRepository.getThreads(process.pid)
        }
    }

    fun onProcessDismissed() {
        selectedProcess.value = null
        threads.value = emptyList()
    }
}
