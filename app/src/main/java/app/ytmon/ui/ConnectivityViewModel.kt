package app.ytmon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ytmon.data.HealthResponse
import app.ytmon.data.YtRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ServerState(
    val reachable: Boolean = false,
    val degraded: Boolean = false,
    val health: HealthResponse? = null,
    val checked: Boolean = false,
)

/**
 * Polls GET /health to drive the connectivity banner and the status panel.
 *
 * Deliberately slow (30s) and sequential: the Termux server sleeps with the
 * phone, so hammering it drains the battery for no benefit. Never spams retries.
 */
class ConnectivityViewModel(private val repo: YtRepository) : ViewModel() {

    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        start()
    }

    fun refreshNow() {
        job?.cancel()
        job = viewModelScope.launch { poll() }
    }

    private fun start() {
        job = viewModelScope.launch { poll() }
    }

    private suspend fun poll() {
        while (viewModelScope.isActive) {
            val result = repo.health()
            result.onSuccess { h ->
                _state.value = ServerState(
                    reachable = true,
                    degraded = h.status != "ok",
                    health = h,
                    checked = true,
                )
            }.onFailure {
                _state.value = ServerState(reachable = false, checked = true)
            }
            delay(30_000)
        }
    }
}