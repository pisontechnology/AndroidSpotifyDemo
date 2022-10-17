package com.pison.hello_world

import android.app.Application
import com.pison.core.client.PisonRemoteServer
import com.pison.core.client.bindToLocalServer
import com.pison.core.client.newPisonSdkInstance
import com.spotify.android.appremote.api.SpotifyAppRemote

class Application : Application() {
    companion object {
        lateinit var sdk: PisonRemoteServer
        lateinit var spotifyAppRemote: SpotifyAppRemote
    }

    override fun onCreate() {
        super.onCreate()

        sdk = newPisonSdkInstance(applicationContext).bindToLocalServer()
    }
}
