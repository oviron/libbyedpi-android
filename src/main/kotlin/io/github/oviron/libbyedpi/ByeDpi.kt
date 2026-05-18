package io.github.oviron.libbyedpi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Lifecycle facade around embedded byedpi; see README for usage. */
object ByeDpi {
    const val EXPECTED_BRIDGE_ABI: Int = 1

    @Volatile
    private var initFailure: Throwable? = null

    @Volatile
    private var loaded: Boolean = false

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var workerJob: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Synchronized
    fun load(nativeLibDir: String) {
        if (loaded) return
        initFailure = try {
            val so = File(nativeLibDir, "libbyedpi.so")
            if (!so.exists()) {
                throw IllegalStateException("missing ${so.absolutePath}")
            }
            System.load(so.absolutePath)
            val abi = nativeBridgeABI()
            if (abi != EXPECTED_BRIDGE_ABI) {
                throw IllegalStateException(
                    "libbyedpi bridge ABI mismatch: facade expects " +
                            "$EXPECTED_BRIDGE_ABI, .so reports $abi"
                )
            }
            loaded = true
            null
        } catch (t: Throwable) {
            t
        }
    }

    fun isLoaded(): Boolean = loaded

    fun assertReady() {
        if (loaded) return
        val err = initFailure
        throw IllegalStateException(
            "ByeDpi not loaded: call ByeDpi.load(nativeLibDir) first" +
                    (err?.let { ": ${it.message}" } ?: ""),
            err,
        )
    }

    suspend fun start(config: ByeDpiConfig) {
        assertReady()
        // Critical section is the state transition only; release the mutex
        // before polling so a concurrent stop() can preempt the bind window.
        mutex.withLock {
            val current = _state.value
            if (current !is State.Idle && current !is State.Failed) {
                throw IllegalStateException(
                    "ByeDpi.start: invalid state $current; stop() first"
                )
            }
            _state.value = State.Starting(config)
            workerJob = scope.launch {
                runProxy(config)
            }
        }
        awaitReadyOrFail()
    }

    suspend fun stop() {
        assertReady()
        mutex.withLock {
            if (_state.value is State.Idle) return
            _state.value = State.Stopping
            nativeStop()
            val joined = withTimeoutOrNull(STOP_JOIN_TIMEOUT_MS) {
                workerJob?.join()
                true
            }
            if (joined == null) {
                nativeForceClose()
                withTimeoutOrNull(STOP_JOIN_TIMEOUT_MS) { workerJob?.join() }
            }
            _state.value = State.Idle
        }
    }

    suspend fun restart(newConfig: ByeDpiConfig) {
        assertReady()
        mutex.withLock {
            if (_state.value !is State.Idle) {
                _state.value = State.Stopping
                nativeStop()
                val joined = withTimeoutOrNull(STOP_JOIN_TIMEOUT_MS) {
                    workerJob?.join()
                    true
                }
                if (joined == null) {
                    nativeForceClose()
                    withTimeoutOrNull(STOP_JOIN_TIMEOUT_MS) { workerJob?.join() }
                }
                _state.value = State.Idle
            }
            _state.value = State.Starting(newConfig)
            workerJob = scope.launch {
                runProxy(newConfig)
            }
        }
        awaitReadyOrFail()
    }

    fun forceClose() {
        assertReady()
        nativeForceClose()
    }

    private suspend fun runProxy(config: ByeDpiConfig) {
        val argv = (listOf("byedpi") + config.args).toTypedArray()
        val exit = nativeStart(argv)
        _state.update { current ->
            if (current is State.Stopping) State.Idle
            else State.Failed(exitCode = exit, config = config)
        }
    }

    private suspend fun awaitReadyOrFail() {
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val current = _state.value
            if (current is State.Failed) {
                throw IllegalStateException(
                    "ByeDpi.start failed: exit=${current.exitCode}"
                )
            }
            if (nativeIsListening()) {
                val starting = _state.value as? State.Starting ?: return
                _state.value = State.Running(starting.config)
                return
            }
            delay(POLL_INTERVAL_MS)
        }
        nativeStop()
        workerJob?.join()
        _state.value = State.Idle
        throw IllegalStateException(
            "ByeDpi.start: proxy did not bind within ${READY_TIMEOUT_MS}ms"
        )
    }

    private const val READY_TIMEOUT_MS = 5_000L
    private const val POLL_INTERVAL_MS = 50L
    private const val STOP_JOIN_TIMEOUT_MS = 3_000L

    @JvmStatic
    private external fun nativeBridgeABI(): Int

    @JvmStatic
    private external fun nativeStart(args: Array<String>): Int

    @JvmStatic
    private external fun nativeStop(): Int

    @JvmStatic
    private external fun nativeForceClose(): Int

    @JvmStatic
    private external fun nativeIsListening(): Boolean

    sealed class State {
        object Idle : State()
        data class Starting(val config: ByeDpiConfig) : State()
        data class Running(val config: ByeDpiConfig) : State()
        object Stopping : State()
        data class Failed(val exitCode: Int, val config: ByeDpiConfig) : State()
    }
}
