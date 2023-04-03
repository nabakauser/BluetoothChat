package com.example.bluetoothchat.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothchat.BluetoothController
import com.example.bluetoothchat.BluetoothDevice
import com.example.bluetoothchat.BluetoothDeviceDomain
import com.example.bluetoothchat.ConnectionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val bluetoothController: BluetoothController
) : ViewModel() {
    private val _state = MutableStateFlow(BluetoothUiState())
    val state = combine(
        bluetoothController.scannedDevices,
        bluetoothController.pairedDevices,
        _state
    ) { scannedDevices, pairedDevices, state ->
        state.copy(
            scannedDevices = scannedDevices,
            pairedDevices = pairedDevices,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    private var deviceConnectionJob: Job? = null
    init {
        bluetoothController.isConnected.onEach { isConnected ->
            _state.update {
                it.copy(isConnected = isConnected)
            }
        }.launchIn(viewModelScope)
    }

    fun connectToDevice(device: BluetoothDeviceDomain) {
        deviceConnectionJob = bluetoothController
            .connectTODevice(device)
            .listen()
    }

    fun disconnectFromDevice() {
        deviceConnectionJob?.cancel()
        bluetoothController.closeConnection()
        _state.update { it.copy(
            isConnected = false
        ) }
    }

    fun waitForIncomingConnections() {
        deviceConnectionJob = bluetoothController
            .startBluetoothService()
            .listen()
    }

    fun startScan() {
        bluetoothController.startDiscovery()
        Log.d("startScanning10", "startScan: ${bluetoothController.startDiscovery()}")
    }

    fun stopScan() {
        bluetoothController.stopDiscovery()
    }

    fun Flow<ConnectionResult>.listen(): Job {
        return onEach {
            when(it) {
                ConnectionResult.ConnectionEstablished -> {
                    _state.update {
                        it.copy(
                            isConnected = true,
                        )
                    }
                }
                is ConnectionResult.Error -> {
                    _state.update {
                        it.copy(
                            isConnected = false
                        )
                    }
                }
            }
        }
            .catch { throwable ->
                bluetoothController.closeConnection()
                _state.update {
                    it.copy(
                        isConnected = true
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothController.releaseDevices()
    }
}

data class BluetoothUiState(
    val scannedDevices: List<BluetoothDevice> = emptyList(),
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val isConnected: Boolean = false,
)