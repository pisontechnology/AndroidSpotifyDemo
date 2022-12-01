package com.example.spotifyvulcancontrol.viewModel

import com.badoo.reaktive.observable.observableOfError
import com.badoo.reaktive.observable.switchMap
import com.badoo.reaktive.scheduler.Scheduler
import com.badoo.reaktive.scheduler.computationScheduler
import com.badoo.reaktive.subject.publish.PublishSubject
import com.example.spotifyvulcancontrol.Application
//import com.pison.core.server.device.DeviceManager
//import com.pison.core.server.device.isConnected
//import com.pison.core.server.frame.PisonServerDeviceFrame
import com.badoo.reaktive.observable.*
//import com.pison.neo.permissions.PermissionsManager
//import com.pison.neo.persistence.NeoHubPersistence
//import com.example.spotifyvulcancontrol.util.FftFilter

// outputs a wave where the frequency/waveform "shape" are fixed, but amplitude can be modulated externally
class WaveProcessor(
    // when changing the amplitude externally, this determines how far into the wave to make changes
    // ideally, changes happen "off screen" so that viewer does not see any jumps in the part of the wave
    // that's already rendered.
    private val amplitudeOffset: Int = 40
) {
    // "Random" but "pretty" wave
    private val wave: List<Float> = listOf(
        0.9f, -0.5f, 0.75f, -0.2f,
        0.3f, -0.7f, 0.5f, -0.14f,
        0.9f, -0.5f, 0.55f, -0.62f,
        0.43f, -0.8f, 0.5f, -0.27f,
        0.23f, -0.3f, 0.32f, -0.4f,
        0.86f, -0.45f, 0.3f, -0.9f,
        0.45f, -0.6f, 0.66f, -0.7f,
        0.3f, -0.2f, 0.3f, -0.8f,
        0.4f, -0.4f, 0.3f, -0.4f,
        0.8f, -0.23f, 0.35f, -0.86f,
        0.3f, -0.5f, 0.66f, -0.7f,
        0.53f, -0.8f, 0.5f, -0.34f,
        0.3f, -0.7f, 0.5f, -0.54f,
        1f, -0.5f, 0.75f, -0.42f,
        0.4f, -0.2f, 0.3f, -0.42f,
        0.78f, -0.23f, 0.66f, -0.7f,
        0.86f, -0.45f, 0.13f, -0.9f,
        0.13f, -0.8f, 0.5f, -0.2f,
        0.3f, -0.7f, 0.44f, -0.34f,
        0.4f, -0.2f, 0.67f, -0.8f,
    )
    private val waveSize = wave.size
    private val amplitudeTable = MutableList(waveSize) { 0.05f }
    private var waveHead = 0L

    // slide wave to left one period
    fun tick() {
        waveHead += 2
        if (waveHead >= waveSize) {
            waveHead = 0
        }
    }

    private fun index(offset: Int): Int {
        return ((offset + waveHead) % waveSize).toInt()
    }

    fun setAmplitude(amplitude: Float) {
        amplitudeTable[index(amplitudeOffset)] = amplitude
        amplitudeTable[index(amplitudeOffset + 1)] = amplitude
    }

    // get the next sample
    fun getWave(offset: Int): Float {
        val i = index(offset)
        return wave[i] * amplitudeTable[i]
    }
}

/*
class FirstConnectedViewModelImpl<FrameType>(
    deviceManager: DeviceManager<PisonServerDeviceFrame<FrameType>>,
    //persistence: NeoHubPersistence,
    //permissionsManager: PermissionsManager,
    private val bgScheduler: Scheduler = computationScheduler
) : FirstConnectedViewModel, BaseConnectedNeoViewModel<FrameType>(
    deviceManager, persistence, permissionsManager
) {
    override val navigation: PublishSubject<FirstConnectedViewModel.Navigation> = PublishSubject()
    override val compassState: PublishSubject<Float> = PublishSubject()
    override val waveProcessor: WaveProcessor = WaveProcessor()
    private val fftFilter = FftFilter(16, 2)

    override fun onAlertPositiveButtonClicked(alertDef: AlertDefinition) {
        super.onAlertPositiveButtonClicked(alertDef)
        if (alertDef == AlertDefinition.CONNECTION_UNSUCCESSFUL) {
            navigation.onNext(FirstConnectedViewModel.Navigation.Reconnect)
        }
    }

    override fun onAlertNegativeButtonClicked(alertDef: AlertDefinition) {
        super.onAlertNegativeButtonClicked(alertDef)
        if (alertDef == AlertDefinition.CONNECTION_UNSUCCESSFUL) {
            // TODO Help article
        }
    }

    override fun onViewFirstStarted() {
        super.onViewFirstStarted()
        // TODO may not be the best place for this. Should probably come after they reach calibration
        persistence.onboardingComplete.value = true
    }

    override fun onScoped() {
        super.onScoped()
        Application.deviceManager
            .currentDeviceStream
            .switchMap {
                if (it.isConnected()) {
                    it.deviceFrameStream
                } else {
                    observableOfError(Throwable("Disconnected"))
                }
            }
            .subscribeScoped (onNext = { frame ->
                fftFilter.consume(frame)?.ys?.get(1)?.maxOrNull()?.let {
                    val adjustedStrength = (it.toFloat() / 600f).coerceIn(0.03f..1f)
                    waveProcessor.setAmplitude(adjustedStrength)
                }
                compassState.onNext(frame.eulerAngles.yaw)
            }, onError = {
                // show disconnected screen
                //println(it.stackTraceToString())
                //showAlert(AlertDefinition.CONNECTION_UNSUCCESSFUL)
            })
    }
}*/