package com.pison.hello_world

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.badoo.reaktive.disposable.CompositeDisposable
import com.badoo.reaktive.observable.flatMapIterable
import com.badoo.reaktive.observable.observeOn
import com.badoo.reaktive.observable.subscribe
import com.badoo.reaktive.scheduler.mainScheduler
import com.pison.core.client.PisonRemoteClassifiedDevice
import com.pison.core.client.monitorConnectedDevices
import com.pison.core.generated.*
import com.pison.core.shared.connection.ConnectedDeviceUpdate
import com.pison.core.shared.connection.ConnectedFailedUpdate
import com.pison.core.shared.connection.DisconnectedDeviceUpdate
import com.pison.core.shared.imu.EulerAngles
import com.pison.hello_world.Application.Companion.sdk

private const val TAG = "LANDING VIEWMODEL"
private const val CONNECTION_TO_DEVICE_TAG = "CONNECTION TO DEVICE"
private const val CONNECTION_STATES = "CONNECTION STATES"
private const val ACTIVATION_STATES_TAG = "ACTIVATION STATES"
private const val EULER_TAG = "EULER"
private const val GESTURES_TAG = "GESTURES"
private const val LOCK_TAG = "LOCK STATES"

class LandingViewModel : ViewModel() {

    private var masterDisposable = CompositeDisposable()

    private val _deviceConnected = MutableLiveData<Boolean>()
    val deviceConnected: LiveData<Boolean>
        get() = _deviceConnected

    private val _deviceStateBatteryReceived = MutableLiveData<DeviceState>()
    val deviceStateBatteryReceived: LiveData<DeviceState>
        get() = _deviceStateBatteryReceived

    private val _lockState = MutableLiveData<Boolean>()
    val lockState: LiveData<Boolean>
        get() = _lockState

    private val _gestureReceived = MutableLiveData<String>()
    val gestureReceived: LiveData<String>
        get() = _gestureReceived

    private val _activationStatesReceived = MutableLiveData<ActivationStates>()
    val activationStates: LiveData<ActivationStates>
        get() = _activationStatesReceived

    private val _eulerReceived = MutableLiveData<EulerAngles>()
    val eulerReceived: LiveData<EulerAngles>
        get() = _eulerReceived

    private val _errorReceived = MutableLiveData<Throwable>()
    val errorReceived: LiveData<Throwable>
        get() = _errorReceived

    private val _enableHapticsButton = MutableLiveData<Boolean>()
    val enableHapticsButton: LiveData<Boolean>
        get() = _enableHapticsButton

    private val _clearGesture = MutableLiveData<Boolean>()
    val clearGesture: LiveData<Boolean>
        get() = _clearGesture


    fun disposeDisposables() {
        Log.d(TAG, "disposed of all disposables")
        masterDisposable.clear(dispose = true)
    }

    fun connectToDevice() {
        Log.d(TAG, "connect called")
        val deviceDisposable = sdk.monitorConnectedDevices().observeOn(mainScheduler).subscribe(
            onNext = { pisonDevice ->
                Log.d(TAG, "$CONNECTION_TO_DEVICE_TAG SUCCESS")
                _deviceConnected.postValue(true)
                onDeviceConnected(pisonDevice)
            },
            onError = { throwable ->
                Log.d(TAG, "$CONNECTION_TO_DEVICE_TAG Error:$throwable")
                _errorReceived.postValue(throwable)
            }
        )
        masterDisposable.add(deviceDisposable)
    }

    fun monitorConnections() {
        Log.d(TAG, "monitor called")
        val connectionsDisposable = sdk.monitorConnections().observeOn(mainScheduler).subscribe(
            onNext = { connection ->
                val isConnected = when (connection) {
                    is ConnectedDeviceUpdate -> true
                    is ConnectedFailedUpdate -> false
                    DisconnectedDeviceUpdate -> false
                }
                Log.d(TAG, "$CONNECTION_STATES $connection")
                _deviceConnected.postValue(isConnected)
                _enableHapticsButton.postValue(isConnected)
            },
            onError = { throwable ->
                Log.d(TAG, "$CONNECTION_STATES ERROR $throwable")
                _errorReceived.postValue(throwable)
            }
        )
        masterDisposable.add(connectionsDisposable)
    }

    private fun monitorLockState(pisonRemoteDevice: PisonRemoteClassifiedDevice) {
        /*val lockDeviceDisposable = pisonRemoteDevice.monitorLockState().observeOn(mainScheduler).subscribe(
            onNext = { deviceLockState ->
                val deviceLockStateBoolean = when (deviceLockState.name) {
                    "LOCKED" -> true
                    "UNLOCKED" -> false
                    else -> false
                }
                Log.d(TAG, "$LOCK_TAG $deviceLockState")
                _lockState.postValue(deviceLockStateBoolean)
            },
            onError = { throwable ->
                Log.d(TAG, "ERROR: $throwable")
                _errorReceived.postValue(throwable)
            }
        )
        masterDisposable.add(lockDeviceDisposable)*/
    }

    private fun onDeviceConnected(pisonRemoteDevice: PisonRemoteClassifiedDevice) {
        Log.d(TAG, "ON DEVICE CONNECTED CALLED")
        monitorConnections()
        enableHapticsButton()
        monitorDeviceState(pisonRemoteDevice)
        //monitorLockState(pisonRemoteDevice)
        monitorGestures(pisonRemoteDevice)
        //monitorActivationStates(pisonRemoteDevice)
        monitorEuler(pisonRemoteDevice)
    }

    private fun enableHapticsButton() {
        _enableHapticsButton.postValue(true)
    }


    private fun monitorDeviceState(pisonRemoteDevice: PisonRemoteClassifiedDevice) {
        val deviceStateDisposable =
            pisonRemoteDevice.monitorDeviceState().observeOn(mainScheduler).subscribe(
                onNext = { deviceState ->
                    //Log.d(TAG, "Device State: $deviceState")
                    _deviceStateBatteryReceived.postValue(deviceState)
                    val deviceLockStateBoolean = when (deviceState.deviceLocked.value) {
                        1 -> true
                        2 -> false
                        else -> false
                    }
                    //Log.d(TAG, "$LOCK_TAG $deviceLockStateBoolean")
                    _lockState.postValue(deviceLockStateBoolean)
                },
                onError = { throwable ->
                    Log.d("MONITOR DEVICE STATES", "ERROR: $throwable")
                    _errorReceived.postValue(throwable)
                }
            )
        masterDisposable.add(deviceStateDisposable)
    }

    private fun monitorGestures(pisonRemoteDevice: PisonRemoteClassifiedDevice) {

        val gestureDisposable =
            pisonRemoteDevice.monitorFrameTags().flatMapIterable { it }.observeOn(mainScheduler).subscribe(
                onNext = { gesture ->
                    //Log.d(TAG, "$GESTURES_TAG $gesture")
                    _gestureReceived.postValue(gesture)
                },
                onError = { throwable ->
                    Log.d(TAG, "ERROR $GESTURES_TAG $throwable")
                    _errorReceived.postValue(throwable)
                }
            )
        masterDisposable.add(gestureDisposable)
    }

    private fun monitorActivationStates(pisonRemoteDevice: PisonRemoteClassifiedDevice) {
        val activationStatesDisposable =
            pisonRemoteDevice.monitorActivationStates().observeOn(mainScheduler).subscribe(
                onNext = { activationStates ->
                    Log.d(
                        TAG,
                        "$ACTIVATION_STATES_TAG $activationStates"
                    )
                    _activationStatesReceived.postValue(activationStates)
                },
                onError = { throwable ->
                    Log.d(TAG, "$ACTIVATION_STATES_TAG ERROR: $throwable")
                    _errorReceived.postValue(throwable)
                }
            )
        masterDisposable.add(activationStatesDisposable)
    }


    private fun monitorEuler(pisonRemoteDevice: PisonRemoteClassifiedDevice) {
        val eulerDisposable =
            pisonRemoteDevice.monitorEulerAngles().observeOn(mainScheduler).subscribe(
                onNext = { euler ->
                    //Log.d(TAG, "$EULER_TAG $euler")
                    _eulerReceived.postValue(euler)
                },
                onError = { throwable ->
                    Log.d(TAG, " $EULER_TAG ERROR $throwable")
                    _errorReceived.postValue(throwable)
                }
            )
        masterDisposable.add(eulerDisposable)
    }
}