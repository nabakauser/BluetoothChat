package com.example.bluetoothchat

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import com.example.bluetoothchat.broadcastreceiver.BluetoothStateReceiver
import com.example.bluetoothchat.broadcastreceiver.DiscoverabilityReceiver
import com.example.bluetoothchat.broadcastreceiver.FoundDeviceReceiver
import com.example.bluetoothchat.mapper.toBluetoothDeviceDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*

@SuppressLint("MissingPermission")
class AndroidBluetoothController(
    private val context: Context
) : BluetoothController {

    private val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean>
        get() = _isConnected.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val scannedDevices: StateFlow<List<BluetoothDeviceDomain>>
        get() = _scannedDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val pairedDevices: StateFlow<List<BluetoothDeviceDomain>>
        get() = _pairedDevices.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    override val errors: SharedFlow<String>
        get() = _errors.asSharedFlow()

    private val foundDeviceReceiver = FoundDeviceReceiver { device ->
        _scannedDevices.update { devices ->
            val newDevice = device.toBluetoothDeviceDomain()
            if (newDevice in devices) devices else devices + newDevice
        }
    }

    private val bluetoothStateReceiver = BluetoothStateReceiver { isConnected, bluetoothDevice ->
        if (bluetoothAdapter?.bondedDevices?.contains(bluetoothDevice) == true) {
            _isConnected.update { isConnected }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                _errors.emit("Cannot connect to a non-paired device.")
            }
        }
    }

    private val discoverabilityReceiver = DiscoverabilityReceiver()

    private var currentServerSocket: BluetoothServerSocket? = null
    private var currentClientSocket: BluetoothSocket? = null

    init {
        updatePairedDevices()
        context.registerReceiver(
            bluetoothStateReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                addAction(BluetoothAdapter.EXTRA_SCAN_MODE)
            }
        )
        Log.d("checkFor10", "init: permissionError")
    }

    override fun startDiscovery() {
        if (hasPermission(Manifest.permission.BLUETOOTH) || (hasPermission(Manifest.permission.BLUETOOTH_SCAN))) {
//            Log.d("checkFor10", "startDiscovery: permissionError")
           context.registerReceiver(
               discoverabilityReceiver,
               IntentFilter(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
           )
            context.registerReceiver(
                foundDeviceReceiver,
                IntentFilter(android.bluetooth.BluetoothDevice.ACTION_FOUND)
            )
            updatePairedDevices()
            bluetoothAdapter?.startDiscovery()
        } else return
    }

    override fun stopDiscovery() {
        if (hasPermission(Manifest.permission.BLUETOOTH_SCAN) || (hasPermission(Manifest.permission.BLUETOOTH))) {
            bluetoothAdapter?.cancelDiscovery()
        } else return
    }

    override fun startBluetoothService(): Flow<ConnectionResult> {
        return flow {
            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT) || (hasPermission(Manifest.permission.BLUETOOTH))) {
                currentServerSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                    "chat_service",
                    UUID.fromString(SERVICE_UUID)

                )
                var shouldLoop = true
                while (shouldLoop) {
                    currentClientSocket = try {
                        currentServerSocket?.accept()
                    } catch (e: IOException) {
                        shouldLoop = false
                        null
                    }
                    emit(ConnectionResult.ConnectionEstablished)
                    currentClientSocket?.let {
                        currentServerSocket?.close()
                    }
                }
            } else {
                Log.d("checkFor10", "startBluetoothService: No Bluetooth Connection")
            }
        }.onCompletion {
            closeConnection()
        }.flowOn(Dispatchers.IO)
    }

    override fun connectTODevice(device: BluetoothDevice): Flow<ConnectionResult> {
        return flow {
            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT) || (hasPermission(Manifest.permission.BLUETOOTH))) {
//                Log.d("checkFor10", "startBluetoothService: No Bluetooth Connection")
                currentClientSocket = bluetoothAdapter
                    ?.getRemoteDevice(device.address)
                    ?.createRfcommSocketToServiceRecord(UUID.fromString(SERVICE_UUID))
                stopDiscovery()

                currentClientSocket?.let {
                    try {
                        it.connect()
                        emit(ConnectionResult.ConnectionEstablished)

                    } catch (e: IOException) {
                        it.close()
                        currentClientSocket = null
                        emit(ConnectionResult.Error("Connection was interrupted"))
                    }
                }
            } else {
                Log.d("checkFor10", "startBluetoothService: No Bluetooth Connection")
            }
        }.onCompletion {
            closeConnection()
        }.flowOn(Dispatchers.IO)
    }

    override fun closeConnection() {
        currentClientSocket?.close()
        currentServerSocket?.close()
        currentClientSocket = null
        currentServerSocket = null
    }

    override fun releaseDevices() {
        context.unregisterReceiver(foundDeviceReceiver)
        context.unregisterReceiver(bluetoothStateReceiver)
        closeConnection()
    }

    private fun updatePairedDevices() {
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT) || (hasPermission(Manifest.permission.BLUETOOTH))) {
            bluetoothAdapter
                ?.bondedDevices
                ?.map { it.toBluetoothDeviceDomain() }
                ?.also { devices ->
                    _pairedDevices.update { devices }
                }
        } else return
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val SERVICE_UUID = "7fec70b8-b851-43cc-b8f0-d12dd117f3db"
    }
}