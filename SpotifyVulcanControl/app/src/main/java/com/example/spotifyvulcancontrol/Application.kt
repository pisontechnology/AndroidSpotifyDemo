package com.example.spotifyvulcancontrol

import android.app.Application
import com.pison.core.client.PisonRemoteServer
import com.pison.core.client.bindToLocalServer
import com.pison.core.client.newPisonSdkInstance
//import com.pison.core.server.device.DeviceManager
//import com.pison.core.server.frame.PisonServerDeviceFrame
import com.pison.core.shared.imu.EulerAngles
import com.spotify.android.appremote.api.SpotifyAppRemote
import io.ktor.http.cio.websocket.*

class Application : Application() {
    companion object {
        //lateinit var deviceManager: DeviceManager<PisonServerDeviceFrame<FrameType>>
        lateinit var sdk: PisonRemoteServer
        lateinit var spotifyAppRemote: SpotifyAppRemote
        var wakeword = false
        var currentGesture = ""
        var eulerAngles: EulerAngles = object: EulerAngles{
            override val pitch: Float = 0.0f
            override val roll: Float = 0.0f
            override val yaw: Float = 0.0f
        }
        var rawAdcAverage = 0f
        var mMainActivity: MainActivity = MainActivity()
    }

    override fun onCreate() {
        super.onCreate()

        sdk = newPisonSdkInstance(applicationContext).bindToLocalServer()
    }
}