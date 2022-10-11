package com.pison.hello_world

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.badoo.reaktive.disposable.CompositeDisposable
import com.badoo.reaktive.observable.subscribe
import com.pison.core.client.PisonRemoteDevice
import com.pison.core.client.monitorConnectedDevices
import com.pison.core.shared.connection.ConnectedDeviceUpdate
import com.pison.core.shared.connection.ConnectedFailedUpdate
import com.pison.core.shared.connection.DisconnectedDeviceUpdate
import com.pison.core.shared.haptic.*
import kotlinx.coroutines.*

private const val TAG = "HAPTICS VIEWMODEL"
private const val CONNECTION_TO_DEVICE_TAG = "CONNECTION TO DEVICE"
private const val CONNECTION_STATES = "CONNECTION STATES"

private const val HAPTIC_ON = 1
private const val HAPTIC_PULSE = 2
private const val HAPTIC_BURST = 3
private const val HAPTIC_OFF = 0

private const val DURATION_MS_DEFAULT = 50
private const val INTENSITY_DEFAULT = 50
private const val NUMBER_DEFAULT = 1

class HapticsViewModel : ViewModel() {

    private var masterDisposable = CompositeDisposable()

    private var pisonRemoteDevice: PisonRemoteDevice? = null

    private val _isDeviceConnected = MutableLiveData<Boolean>()
    val deviceConnected: LiveData<Boolean>
        get() = _isDeviceConnected

    private val _errorReceived = MutableLiveData<Throwable>()
    val errorReceived: LiveData<Throwable>
        get() = _errorReceived

    private val _hapticCommandType = MutableLiveData<Int>()
    val hapticCommandType: LiveData<Int>
        get() = _hapticCommandType

    private val _durationMs = MutableLiveData<Int>()
    val durationMs: LiveData<Int>
        get() = _durationMs

    private val _intensity = MutableLiveData<Int>()
    val intensity: LiveData<Int>
        get() = _intensity

    private val _numberBursts = MutableLiveData<Int>()
    val numberBursts: LiveData<Int>
        get() = _numberBursts

    private val _onOffSwitchState = MutableLiveData<Boolean>()
    val onOffSwitchState: LiveData<Boolean>
        get() = _onOffSwitchState

    fun onHapticCommandTypeSelected(hapticCommandCode: Int) {
        _hapticCommandType.value = hapticCommandCode
    }

    fun onDurationUpdated(duration: Int) {
        _durationMs.value = duration
    }

    fun onIntensityUpdated(intensity: Int) {
        _intensity.value = intensity
    }

    fun onNumberOfBurstsUpdated(numberBursts: Int) {
        _numberBursts.value = numberBursts
    }

    fun disposeDisposables() {
        Log.d(TAG, "disposed of all disposables")
        if (!masterDisposable.isDisposed) {
            masterDisposable.clear(dispose = true)
        }
    }

    fun connectToDevice() {
        val deviceDisposable = Application.sdk.monitorConnectedDevices().subscribe(
            onNext = { pisonDevice ->
                Log.d(TAG, "$CONNECTION_TO_DEVICE_TAG SUCCESS")
                _isDeviceConnected.postValue(true)
                pisonRemoteDevice = pisonDevice
            },
            onError = { throwable ->
                Log.d(TAG, "$CONNECTION_TO_DEVICE_TAG ERROR $throwable")
                _errorReceived.postValue(throwable)
            }
        )
        masterDisposable.add(deviceDisposable)
    }

    fun monitorConnections() {
        val connectionsDisposable = Application.sdk.monitorConnections().subscribe(
            onNext = { connection ->
                val isConnected = when (connection) {
                    is ConnectedDeviceUpdate -> true
                    is ConnectedFailedUpdate -> false
                    DisconnectedDeviceUpdate -> false
                }
                Log.d(TAG, "$CONNECTION_STATES $connection")
                _isDeviceConnected.postValue(isConnected)
            },
            onError = { throwable ->
                Log.d(TAG, "$CONNECTION_STATES Error: $throwable")
                onOnOffSwitchStateChanged(false)
                _errorReceived.postValue(throwable)
            }
        )
        masterDisposable.add(connectionsDisposable)
    }

    fun sendHaptic(hapticCommandCode: Int, durationMs: Int, intensity: Int, numberBursts: Int) {
        GlobalScope.launch {
            val haptic = getHapticCommand(
                hapticCommandCode,
                durationMs,
                intensity,
                numberBursts
            )
            Log.d(
                TAG,
                "send Haptic with $hapticCommandCode, $durationMs, $intensity, $numberBursts resulting in $haptic"
            )
            pisonRemoteDevice?.sendHaptic(haptic)
        }
    }

    private fun getHapticCommand(
        hapticCommandCode: Int,
        durationMs: Int,
        intensity: Int,
        number: Int
    ): HapticCommand {
        return when (hapticCommandCode) {
            HAPTIC_ON -> HapticOnCommand(intensity)
            HAPTIC_PULSE -> HapticPulseCommand(intensity, durationMs)
            HAPTIC_BURST -> HapticBurstCommand(intensity, durationMs, number)
            else -> HapticOffCommand
        }
    }

    fun clearHapticValues() {
        onDurationUpdated(DURATION_MS_DEFAULT)
        onIntensityUpdated(INTENSITY_DEFAULT)
        onNumberOfBurstsUpdated(NUMBER_DEFAULT)

        Log.d(
            TAG,
            "Cleared live data values: hapticCode: ${_hapticCommandType.value}, " +
                    "Duration: ${durationMs.value}, Intensity: ${intensity.value}, " +
                    "Number of Bursts: ${numberBursts.value}"
        )
    }

    fun onOnOffSwitchStateChanged(onOffSwitchChecked: Boolean) {
        Log.d(TAG, "switch state changed, switch checked: $onOffSwitchChecked")
        _onOffSwitchState.postValue(onOffSwitchChecked)
        when (onOffSwitchChecked) {
            true -> sendHaptic(
                HAPTIC_ON,
                DURATION_MS_DEFAULT,
                INTENSITY_DEFAULT,
                NUMBER_DEFAULT
            )
            else -> sendHaptic(
                HAPTIC_OFF,
                DURATION_MS_DEFAULT,
                INTENSITY_DEFAULT,
                NUMBER_DEFAULT
            )
        }
    }
}