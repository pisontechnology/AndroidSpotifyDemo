package com.example.spotifyvulcancontrol

import android.animation.Animator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AsyncPlayer
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
import com.pison.core.client.*
import com.pison.core.shared.connection.ConnectedDevice
import com.pison.core.shared.haptic.*
import com.pison.core.shared.imu.EulerAngles
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.spotify.protocol.types.Track;
import kotlinx.coroutines.Runnable
import com.pison.core.shared.DEFAULT_PISON_PORT
import java.util.*
import com.pison.core.client.PisonRemoteServer
import com.pison.core.client.PisonSdk
import com.pison.core.client.newPisonSdkInstance
import com.pison.core.shared.connection.ConnectedDeviceUpdate
import com.pison.core.shared.connection.ConnectedFailedUpdate
import com.pison.core.shared.connection.DisconnectedDeviceUpdate
import com.pison.core.transmission.DownlinkFrameVersion
import com.pison.core.transmission.v4.downlink.DownlinkDenormalizedTransmissionPacketV4
import com.pison.core.transmission.v4.downlink.DownlinkTransmissionPacketV4
import com.pison.core.transmission.v4.downlink.denormalize
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import com.spotify.protocol.types.Shuffle
import kotlinx.coroutines.delay

const val SERVER_INITIALIZE_COMMAND = 1
private const val TAG = "DEVICE SERVICES"
private const val EULER_TAG = "EULER"
private const val GESTURES_TAG = "GESTURES"

private const val HAPTICCT = 5
private const val HAPTIC_PULSE = 2
private const val HAPTIC_BURST = 3
private const val HAPTIC_OFF = 0

// Make a sound when it unlocks
private const val DURATION_MS_DEFAULT = 245
private const val INTENSITY_UnlockLock = 100 // Change over time first is heavy then medium then light
private const val INTENSITY_BASIC = 60
private const val NUMBER_DEFAULT_Unlock = 2
private const val NUMBER_DEFAULT_Lock = 3
private const val NUMBER_DEFAULT_BASIC = 1

private const val INCREASE_DELAY = 100

//private val fftFilter = FftFilter(16, 2)

@Suppress("DEPRECATION")
@ExperimentalStdlibApi
class DeviceService: Service(){
    private var pisonRemoteDevice: PisonRemoteDevice? = null

    private var masterDisposable = CompositeDisposable()

    lateinit var audioManager: AudioManager

    val mHandler = Handler(Looper.getMainLooper())

    private val _eulerReceived = MutableLiveData<EulerAngles>()
    val eulerReceived: LiveData<EulerAngles>
        get() = _eulerReceived

    private val _errorReceived = MutableLiveData<Throwable>()
    val errorReceived: LiveData<Throwable>
        get() = _errorReceived

    private var armAtNinede = false
    private var isUpward = false
    private var isDownward = false
    private var isIndexed = false
    private var debounce = false
    var swipedUp = false
    var swipedDown = false
    var connectingToSpotify = false
    private var swiped = false
    private var didAGesture = false

    var autoLock = GlobalScope.launch { }

    // EMI Screen variables
    private var lastStoredFrame: MutableList<Double> = MutableList<Double>(0) {0.0}
    private var isInitialized = false
    private var lastFrameTimestamp = -1.0
    private var lastFrameTimeDiff = -1.0
    private val fftBuffers = List(2) { MutableList(16) { 0.0 } }
    private var fft = FastFourierTransformer(DftNormalization.UNITARY)

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
        monitorDeviceState(pisonRemoteDevice)
    }
    override fun onCreate() {
        super.onCreate()
        audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        moniter()

        // checks every 1.2sec to see if the user is holding a swipe up or swipe down gesture
        // if so will gradually change volume based off of that information
        mHandler.post(object: Runnable{
            override fun run() {
                //println(swipedUp)
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
        return null

    }

    private fun monitorDeviceState(pisonRemoteDevice: PisonRemoteClassifiedDevice){
        val deviceStateDisposable =
            Application.sdk.monitorConnections().observeOn(mainScheduler).subscribe(
                onNext = {
                    when(it) {
                        is ConnectedDeviceUpdate -> {
                            println("Pison device connected: ${it.connectedDevice.deviceName}")
                            monitorRawData(it.connectedDevice)
                        }
                        DisconnectedDeviceUpdate -> println("Pison device disconnected!")
                        is ConnectedFailedUpdate -> println("Error connecting to Pison Device: ${it.reason}")
                    }
                }
            )
        masterDisposable.add(deviceStateDisposable)
    }

    private fun monitorRawData(connectedDevice: ConnectedDevice){
        val deviceMonitorDisposable =
            Application.sdk.monitorRawDevice(connectedDevice).subscribe(
                onNext = { rawDevice ->
            println("monitoring device in raw mode: ${rawDevice.deviceId}")
                    onMonitoringDevice(rawDevice)
        },
                onError = {
            println("device ${connectedDevice.deviceName} has disconnected!")
        })
        masterDisposable.add(deviceMonitorDisposable)
    }

    private fun onMonitoringDevice(rawDevice: PisonRemoteRawDevice) {
        val rawDataDisposable =
            rawDevice.monitorRawData().observeOn(mainScheduler).subscribe(onNext = { rawBytes: ByteArray ->
                val parser = DownlinkFrameVersion.V4.buildNewParser()
                val packets: List<DownlinkTransmissionPacketV4> = parser.parse(rawBytes)
                packets.asSequence()
                    .flatMap { it.denormalize() }
                    .forEach {
                        processDenormalizePacket(it)
                    }
            }, onError = {
                println("Stop streaming raw data SDKVersion=${rawDevice.server.sdkVersionType} " +
                        "deviceId=${rawDevice.deviceId} from ${rawDevice.server.hostAddress}:${rawDevice.server.hostPort}" +
                        " for session ${rawDevice.sessionId} Caused by: ${it.message}")
            }, onComplete = {
                println("${rawDevice.deviceId} from ${rawDevice.server} for session ${rawDevice.sessionId} done")
            })
        masterDisposable.add(rawDataDisposable)
    }

    //region EMI Display data

    private fun processDenormalizePacket(it: DownlinkDenormalizedTransmissionPacketV4) {

        val nowTime = it.timeStampMs

        if(!isInitialized){
            lastStoredFrame = it.contents.adc?.adcRaw!!.toMutableList()
            lastFrameTimestamp = nowTime
            isInitialized = true
        }

        val deltaFrame = it.contents.adc?.adcRaw?.toMutableList()?.zip(lastStoredFrame)?.map { (l, r) -> l -r }
        lastStoredFrame = it.contents.adc?.adcRaw!!.toMutableList()

        lastFrameTimeDiff = nowTime - lastFrameTimestamp
        lastFrameTimestamp = nowTime

        appendInterpolatedPoints(deltaFrame!!.toMutableList())
        val ys = fftBuffers.mapIndexed { index, buffer ->
            buffer.add(deltaFrame[index])
            buffer.subList(0, buffer.size - 16).clear()
            val y = fft.transform(buffer.toDoubleArray(), TransformType.FORWARD)
            List(16){
                (y[it].abs() / 16).toInt()
            }
        }

        ys.get(1).maxOrNull()?.let {
            Application.rawAdcAverage = (it.toFloat() / 600f).coerceIn(0.03f..1f)
        }
    }

    private fun appendInterpolatedPoints(deltaFrame: List<Double>) {
        // Step 0 and Step 1 are the increments expected from the signal every 1ms.
        fftBuffers.forEachIndexed { i, buffer ->
            val last = buffer.last()
            val step: Double = getAbsDiff(last, deltaFrame[i]) / lastFrameTimeDiff
            interpolateAndAddToBuffer(
                last,
                lastFrameTimeDiff.roundToInt(),
                step,
                buffer
            )
        }
    }

    private fun getAbsDiff(last: Double, current: Double): Double {
        return abs(current - last)
    }

    private fun interpolateAndAddToBuffer(
        start_value: Double,
        diffTime: Int,
        step: Double,
        fftBuffer: MutableList<Double>
    ) {
        for (i in 1 until diffTime) {
            fftBuffer.add(start_value + i * floor(step))
        }
    }

    //endregion

    // using IMU accelerameter data will trigger two bools to determine if ether your wrist is forwards or backwards
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

                    if(imu.acceleration.y > 8.0f){
                        armAtNinede = true
                    }
                    else{
                        armAtNinede = false
                    }

                },
                onError = { throwable ->
                    Log.d("IMU STATES", "ERROR: $throwable")
                }
            )
        masterDisposable.add(imuStateDisposable)
    }

    suspend fun autoLockDevice(){
        //println("Auto-lock Called")
        delay(Application.DELAY_AUTOLOCK)
        //println("Auto-lock")

        if(!didAGesture){
            lockWakeword()
        }
    }

    private fun lockWakeword(){
        if(Application.wakeword == true){
            sendHaptic(
                HAPTIC_BURST,
                DURATION_MS_DEFAULT,
                INTENSITY_UnlockLock,
                NUMBER_DEFAULT_Unlock
            )
        }

        Application.wakeword = false
        debounce = false
        isIndexed = false
    }

    private fun monitorGestures(pisonRemoteDevice: PisonRemoteClassifiedDevice) {
        val gestureDisposable =
            pisonRemoteDevice.monitorFrameTags().flatMapIterable { it }.observeOn(mainScheduler).subscribe(
                onNext = { gesture ->
                    if(Application.spotifyAppRemote != null) {
                        connectingToSpotify = false
                        //Log.d(TAG, "$GESTURES_TAG $gesture")
                        Application.currentGesture = gesture
                        if (gesture == "SHAKE_N_INEH") {
                            Application.wakeword = !Application.wakeword
                            if (Application.wakeword) {
                                println("WOKE UP")
                                debounce = true
                                sendHaptic(
                                    HAPTIC_BURST,
                                    DURATION_MS_DEFAULT,
                                    INTENSITY_UnlockLock,
                                    NUMBER_DEFAULT_Unlock
                                )
                            } else {
                                println("NAP TIME")
                                debounce = false
                                isIndexed = false
                                sendHaptic(
                                    HAPTIC_BURST,
                                    DURATION_MS_DEFAULT,
                                    INTENSITY_UnlockLock,
                                    NUMBER_DEFAULT_Unlock
                                )
                            }
                        }
                        if (Application.wakeword) {
                            if (gesture == "DEBOUNCE_LDA_INEH") {
                                didAGesture = true
                                isIndexed = true // trigger user is Indexing at the moment
                            }

                            if (gesture == "DEBOUNCE_LDA_TEH" && isUpward && !debounce ||
                                gesture == "DEBOUNCE_LDA_FHEH" && isUpward && !debounce
                            ) {
                                didAGesture = true
                                isIndexed = false
                                debounce = true

                                // If a swipe wasnt preformed will instead like or unlike the song
                                GlobalScope.launch {
                                    delay(550)

                                    if(!swiped){
                                        println("Liked Song")
                                        // Adding song to liked songs
                                        Application.spotifyAppRemote.playerApi.playerState.setResultCallback {
                                            if (it.track.name != null) {
                                                Application.spotifyAppRemote.userApi.getLibraryState(it.track.uri).setResultCallback {
                                                    if(it.isAdded){
                                                        Application.spotifyAppRemote.userApi.removeFromLibrary(it.uri)
                                                    }
                                                    else{
                                                        Application.spotifyAppRemote.userApi.addToLibrary(it.uri)
                                                    }
                                                }
                                            }
                                        }

                                        sendHaptic(
                                            HAPTIC_BURST,
                                            DURATION_MS_DEFAULT,
                                            INTENSITY_BASIC,
                                            NUMBER_DEFAULT_BASIC
                                        )
                                    }
                                    else{
                                        swiped = false
                                    }
                                }
                            }
                            else if (gesture == "FHEH_SWIPE_RIGHT" ||
                                     gesture == "TEH_SWIPE_RIGHT"
                            ) {
                                // skip to next song
                                println("Play Next Song")

                                didAGesture = true
                                isIndexed = false
                                debounce = true
                                swiped = true
                                Application.spotifyAppRemote.playerApi.skipNext()
                                sendHaptic(
                                    HAPTIC_BURST,
                                    DURATION_MS_DEFAULT,
                                    INTENSITY_BASIC,
                                    NUMBER_DEFAULT_BASIC
                                )
                            } else if (gesture == "FHEH_SWIPE_LEFT" ||
                                        gesture == "TEH_SWIPE_LEFT"
                            ) {
                                // skip to previous song
                                println("Play Prev Song")

                                didAGesture = true
                                isIndexed = false
                                debounce = true
                                swiped = true
                                Application.spotifyAppRemote.playerApi.skipPrevious()
                                sendHaptic(
                                    HAPTIC_BURST,
                                    DURATION_MS_DEFAULT,
                                    INTENSITY_BASIC,
                                    NUMBER_DEFAULT_BASIC
                                )
                            } else if (gesture == "INEH_SWIPE_UP") {
                                // Swipe up will increase volume set amount
                                println("Increase Volume")

                                didAGesture = true
                                debounce = true
                                swipedUp = true // trigger hold functionality
                                audioManager.adjustVolume(
                                    AudioManager.ADJUST_RAISE,
                                    AudioManager.FLAG_PLAY_SOUND
                                )
                                sendHaptic(
                                    HAPTIC_BURST,
                                    DURATION_MS_DEFAULT,
                                    INTENSITY_BASIC,
                                    NUMBER_DEFAULT_BASIC
                                )
                            } else if (gesture == "INEH_SWIPE_DOWN") {
                                // Swipe down will decrease volume set amount
                                didAGesture = true
                                debounce = true
                                swipedDown = true // trigger hold functionality
                                println("Decrease Volume")
                                audioManager.adjustVolume(
                                    AudioManager.ADJUST_LOWER,
                                    AudioManager.FLAG_SHOW_UI
                                )
                                sendHaptic(
                                    HAPTIC_BURST,
                                    DURATION_MS_DEFAULT,
                                    INTENSITY_BASIC,
                                    NUMBER_DEFAULT_BASIC
                                )
                            }
                            else if (gesture != "DEBOUNCE_LDA_INEH" && isIndexed &&
                                !debounce && !swipedDown && !swipedUp
                            ) {
                                // User closed hand without preforming any other gesture
                                didAGesture = true
                                isIndexed = false

                                if(!armAtNinede){
                                    // Check if spotify is currently playing or paused and trigger opposite
                                    Application.spotifyAppRemote.playerApi.playerState.setResultCallback {
                                        if (it.isPaused) {
                                            println("Play Song")
                                            Application.spotifyAppRemote.playerApi.resume()
                                        } else {
                                            println("Pause Song")
                                            Application.spotifyAppRemote.playerApi.pause()
                                        }

                                        sendHaptic(
                                            HAPTIC_BURST,
                                            DURATION_MS_DEFAULT,
                                            INTENSITY_BASIC,
                                            NUMBER_DEFAULT_BASIC
                                        )
                                    }
                                }
                                /*else{
                                    didAGesture = true
                                    println("toggle shuffle")
                                    Application.spotifyAppRemote.playerApi.toggleShuffle()

                                    sendHaptic(
                                        HAPTIC_BURST,
                                        DURATION_MS_DEFAULT,
                                        INTENSITY_BASIC,
                                        NUMBER_DEFAULT_BASIC
                                    )
                                }*/
                            } else if (gesture == "DEBOUNCE_LDA_INEH" && swipedUp ||
                                gesture == "DEBOUNCE_LDA_INEH" && swipedDown
                            ) {
                                didAGesture = true
                                mHandler.post(object : Runnable {
                                    override fun run() {
                                        mHandler.postDelayed({
                                            if (swipedUp) {
                                                println("Steady increase volume")
                                                audioManager.adjustVolume(
                                                    AudioManager.ADJUST_RAISE,
                                                    AudioManager.FLAG_SHOW_UI
                                                )
                                            }

                                            if (swipedDown) {
                                                audioManager.adjustVolume(
                                                    AudioManager.ADJUST_LOWER,
                                                    AudioManager.FLAG_SHOW_UI
                                                )
                                            }
                                        }, 500)
                                    }
                                })
                            }
                            else if (gesture == "DEBOUNCE_LDA_INACTIVE" && debounce) {
                                // when hand is at rest reset everything
                                println("Inactive time")
                                didAGesture = false
                                debounce = false
                                isIndexed = false
                                swipedDown = false
                                swipedUp = false

                                if(autoLock.isActive && Application.shouldAutolock){
                                    autoLock.cancel()
                                    autoLock = GlobalScope.launch { autoLockDevice() }
                                }
                                else if (Application.shouldAutolock) {
                                    autoLock = GlobalScope.launch { autoLockDevice() }
                                }
                            }
                        }
                    }
                    else{
                        if(!connectingToSpotify)
                        {
                            println("Reconnecting to spotify")
                            connectingToSpotify = true
                            Application.mMainActivity.connectToSpotify()
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
                onNext = { eulerAngle ->
                    Application.eulerAngles = eulerAngle
                    _eulerReceived.postValue(eulerAngle)
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