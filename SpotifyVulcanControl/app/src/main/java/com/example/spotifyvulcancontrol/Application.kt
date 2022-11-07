package com.example.spotifyvulcancontrol

import android.app.Application
import com.pison.core.client.PisonRemoteServer
import com.pison.core.client.bindToLocalServer
import com.pison.core.client.newPisonSdkInstance
import com.pison.core.shared.imu.EulerAngles
import com.spotify.android.appremote.api.SpotifyAppRemote

class Application : Application() {
    companion object {
        lateinit var sdk: PisonRemoteServer
        lateinit var spotifyAppRemote: SpotifyAppRemote
        var wakeword = false
        var currentGesture = ""
        var eulerAngles: EulerAngles = object: EulerAngles{
            override val pitch: Float = 0.0f
            override val roll: Float = 0.0f
            override val yaw: Float = 0.0f
        }
    }

    override fun onCreate() {
        super.onCreate()

        sdk = newPisonSdkInstance(applicationContext).bindToLocalServer()
    }
}