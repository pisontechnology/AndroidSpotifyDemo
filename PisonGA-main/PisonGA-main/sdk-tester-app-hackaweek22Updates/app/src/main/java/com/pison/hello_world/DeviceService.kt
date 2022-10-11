package com.pison.hello_world

import android.animation.Animator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.badoo.reaktive.disposable.CompositeDisposable
import com.badoo.reaktive.observable.flatMapIterable
import com.badoo.reaktive.observable.observeOn
import com.badoo.reaktive.observable.subscribe
import com.badoo.reaktive.scheduler.mainScheduler
import com.pison.core.client.PisonRemoteClassifiedDevice
import com.pison.core.client.PisonRemoteDevice
import com.pison.core.client.monitorConnectedDevices
import com.pison.core.shared.haptic.*
import com.pison.core.shared.imu.EulerAngles
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.spotify.protocol.types.Track;
import kotlinx.coroutines.Runnable
import java.util.*
import java.util.concurrent.Executor

const val SERVER_INITIALIZE_COMMAND = 1
private const val TAG = "Device Service"
private const val EULER_TAG = "EULER"
private const val GESTURES_TAG = "GESTURES"

private const val HAPTICCT = 5
private const val HAPTIC_PULSE = 2
private const val HAPTIC_BURST = 3
private const val HAPTIC_OFF = 0

private const val DURATION_MS_DEFAULT = 245
private const val INTENSITY_UnlockLock = 100
private const val INTENSITY_BASIC = 70
private const val NUMBER_DEFAULT_UnlockLock = 2
private const val NUMBER_DEFAULT_BASIC = 1

private const val INCREASE_DELAY = 100



@Suppress("DEPRECATION")
@ExperimentalStdlibApi
class DeviceService: Service(){
    private var pisonRemoteDevice: PisonRemoteDevice? = null

    private var masterDisposable = CompositeDisposable()

    lateinit var audioManager: AudioManager

    lateinit var runnable: Runnable

    val mHandler = Handler(Looper.getMainLooper())

//    private val _gestureReceived = MutableLiveData<String>()
//    val gestureReceived: LiveData<String>
//        get() = _gestureReceived

    private val _eulerReceived = MutableLiveData<EulerAngles>()
    val eulerReceived: LiveData<EulerAngles>
        get() = _eulerReceived

    private val _errorReceived = MutableLiveData<Throwable>()
    val errorReceived: LiveData<Throwable>
        get() = _errorReceived

    private var wakeword = false
    private var isPlaying = false
    private var isUpward = false
    private var isDownward = false
    private var isIndexed = false
    private var debounce = false
    private var isShuffled = false
    public var swipedUp = false
    public var swipedDown = false
    private var curTrack: Track? = null
    private var justStartedUp = true

    companion object {
        private const val ACTION_START_SERVICE = "action_start_service"
        private const val ACTION_STOP_SERVICE = "action_stop_service"

        fun getStartIntent(context: Context): Intent {
            return with(Intent(context, DeviceService::class.java)) {
                action = ACTION_START_SERVICE
                println("INTENT STARTING*************")
                this
            }
        }

        fun getStopIntent(context: Context): Intent {
            return with(Intent(context, DeviceService::class.java)) {
                action = ACTION_STOP_SERVICE
                this
            }
        }
    }
    private fun sendHaptic(hapticCommandCode: Int, durationMs: Int, intensity: Int, numberBursts: Int) {
        GlobalScope.launch {
            val haptic = getHapticCommand(
                hapticCommandCode,
                durationMs,
                intensity,
                numberBursts
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
            HAPTICCT -> HapticOnCommand(intensity)
            HAPTIC_PULSE -> HapticPulseCommand(intensity, durationMs)
            HAPTIC_BURST -> HapticBurstCommand(intensity, durationMs, number)
            else -> HapticOffCommand
        }
    }

    private fun moniter() {
        Log.d(TAG, "connect called")
        val deviceDisposable = Application.sdk.monitorConnectedDevices().observeOn(mainScheduler).subscribe(
            onNext = { pisonDevice ->
                Log.d(TAG, "$TAG SUCCESS")
                inTakeData(pisonDevice)
                pisonRemoteDevice = pisonDevice
            },
            onError = { throwable ->
                Log.d(TAG, "$TAG Error:$throwable")
                _errorReceived.postValue(throwable)
            }
        )
        masterDisposable.add(deviceDisposable)
    }

    private fun inTakeData(pisonRemoteDevice: PisonRemoteClassifiedDevice){
        monitorGestures(pisonRemoteDevice)
        monitorEuler(pisonRemoteDevice)
        monitorImuState(pisonRemoteDevice)
    }
    override fun onCreate() {
        super.onCreate()
        audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        moniter()

        mHandler.post(object: Runnable{
            override fun run() {
                //println("am I called?")
                println(swipedUp)
                if(swipedUp){
                    println("Steady increase volume")
                    audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                }
                else if(swipedDown){
                    audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                }
                mHandler.postDelayed(this,1200)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        disposeDisposables()
    }


    override fun onBind(p0: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    private fun monitorImuState(pisonRemoteDevice: PisonRemoteClassifiedDevice){
        val imuStateDisposable =
            pisonRemoteDevice.monitorImu().observeOn(mainScheduler).subscribe(
                onNext = { imu ->
                    if(imu.acceleration.y < -8.0f){
                        //println(imu.acceleration.x)
                        if(imu.acceleration.x < -0.7f){
                            isUpward = true
                            isDownward = false
                        }
                        else if(imu.acceleration.x > -0.3f){
                            isUpward = false
                            isDownward = true
                        }
                    }
                    if(imu.acceleration.y - imu.acceleration.z > -4.0f){
                        if(imu.acceleration.x < -4.0f){
                            isUpward = true
                            isDownward = false
                        }
                        else{ //if (imu.acceleration.x > 5.0f){
                            isUpward = false
                            isDownward = true
                        }
                    }
                },
                onError = { throwable ->
                    Log.d("IMU STATES", "ERROR: $throwable")
                }
            )
        masterDisposable.add(imuStateDisposable)
    }

    private fun monitorGestures(pisonRemoteDevice: PisonRemoteClassifiedDevice) {
        val gestureDisposable =
            pisonRemoteDevice.monitorFrameTags().flatMapIterable { it }.observeOn(mainScheduler).subscribe(
                onNext = { gesture ->
                    Log.d(TAG, "$GESTURES_TAG $gesture")
                    //print(gesture)
                    if (gesture == "SHAKE_N_INEH"){
                        wakeword = !wakeword
                        if(wakeword){
                            println("WOKE UP")
                            debounce = true
                            sendHaptic(
                                HAPTIC_BURST,
                                DURATION_MS_DEFAULT,
                                INTENSITY_UnlockLock,
                                NUMBER_DEFAULT_UnlockLock
                            )
                        }else{
                            println("NAP TIME")
                            debounce = false
                            isIndexed = false
                            sendHaptic(
                                HAPTIC_BURST,
                                DURATION_MS_DEFAULT,
                                INTENSITY_UnlockLock,
                                NUMBER_DEFAULT_UnlockLock
                            )
                        }
                    }
                    if (wakeword) {
                        if(gesture == "DEBOUNCE_LDA_INEH"){
                            isIndexed = true // trigger user is Indexing at the moment
                        }

                        if(gesture == "DEBOUNCE_LDA_TEH" && isUpward && !debounce){
                            isIndexed = false
                            debounce = true
                            println("Liked Song")

                            Application.spotifyAppRemote.playerApi.playerState.setResultCallback {
                                if(it.track.name != null){
                                    Application.spotifyAppRemote.userApi.addToLibrary(it.track.uri)
                                }
                            }

                            sendHaptic(
                                HAPTIC_BURST,
                                DURATION_MS_DEFAULT,
                                INTENSITY_BASIC,
                                NUMBER_DEFAULT_BASIC
                            )
                        }
                        else if(gesture == "DEBOUNCE_LDA_TEH" && isDownward && !debounce){
                            isIndexed = false
                            debounce = true
                            println("Unliked Song")

                            Application.spotifyAppRemote.playerApi.playerState.setResultCallback {
                                if(it.track.name != null){
                                    Application.spotifyAppRemote.userApi.removeFromLibrary(it.track.uri)
                                }
                            }

                            sendHaptic(
                                HAPTIC_BURST,
                                DURATION_MS_DEFAULT,
                                INTENSITY_BASIC,
                                NUMBER_DEFAULT_BASIC
                            )
                        }
                        else if(gesture == "INEH_SWIPE_RIGHT"){
                            isIndexed = false
                            debounce = true
                            println("Play Next Song")
                            Application.spotifyAppRemote.playerApi.skipNext()
                        }
                        else if(gesture == "INEH_SWIPE_LEFT"){
                            isIndexed = false
                            debounce = true
                            println("Play Prev Song")
                            Application.spotifyAppRemote.playerApi.skipPrevious()
                        }
                        else if(gesture == "INEH_SWIPE_UP"){
                            //isIndexed = false
                            debounce = true
                            swipedUp = true
                            println("Increase Volume")
                            audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND)
                            //audioManager.adjustVolume(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + 5, AudioManager.FLAG_SHOW_UI)
                        }
                        else if(gesture == "INEH_SWIPE_DOWN"){
                            //isIndexed = false
                            debounce = true
                            swipedDown = true
                            println("Decrease Volume")
                            audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        }
                        /*else if(gesture == "DEBOUNCE_LDA_FHEH"){
                            isIndexed = false
                            debounce = true
                            println("Shuffle Playlist")
                            Application.spotifyAppRemote.playerApi.toggleShuffle()
                        }*/ // TODO: Possible way to add shuffling (just find a way to get current Shuffle State)
                        else if(gesture != "DEBOUNCE_LDA_INEH" && isIndexed &&
                            !debounce && !swipedDown && !swipedUp){
                            // User closed hand without preforming any other gesture
                            isIndexed = false
                            //println("CLICKED")

                            // Check if spotify is currently playing or paused and trigger opposite
                            Application.spotifyAppRemote.playerApi.playerState.setResultCallback {
                                //Log.d(TAG, "isPaused = " + it.isPaused)
                                if(it.isPaused){
                                    isPlaying = false
                                }
                                else{
                                    isPlaying = true
                                }
                            }

                            // Call oposite based off of previous information
                            Handler(Looper.getMainLooper()).postDelayed({
                                if(!isPlaying){
                                    println("Play Song")
                                    Application.spotifyAppRemote.playerApi.resume()
                                }
                                else{
                                    println("Pause Song")
                                    Application.spotifyAppRemote.playerApi.pause()
                                }

                                sendHaptic(
                                    HAPTIC_BURST,
                                    DURATION_MS_DEFAULT,
                                    INTENSITY_BASIC,
                                    NUMBER_DEFAULT_BASIC
                                )
                            }, 70)
                        }
                        else if(gesture == "DEBOUNCE_LDA_INEH" && swipedUp ||
                                gesture == "DEBOUNCE_LDA_INEH" && swipedDown){
                            mHandler.post(object: Runnable{
                                override fun run() {
                                    mHandler.postDelayed({
                                        if(swipedUp){
                                            println("Steady increase volume")
                                            audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                                        }

                                        if(swipedDown){
                                            audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                                        }
                                    }, 500)
                                }
                            })
                        }
                        else if(gesture == "DEBOUNCE_LDA_INACTIVE" && debounce){
                            debounce = false
                            isIndexed = false
                            swipedDown = false
                            swipedUp = false
                        }
                    }
                },
                onError = { throwable ->
                    Log.d(TAG, "ERROR $GESTURES_TAG $throwable")
                    _errorReceived.postValue(throwable)
                }
            )
        masterDisposable.add(gestureDisposable)
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
    private fun disposeDisposables() {
        Log.d(TAG, "disposed of all disposables")
        masterDisposable.clear(dispose = true)
    }
}