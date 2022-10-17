package com.pison.hello_world

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pison.hello_world.Application.Companion.spotifyAppRemote
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.ImageUri
import com.pison.hello_world.DeviceService

private const val PISON_PACKAGE = "com.pison.hello_word"
private const val PISON_CLASS = "$PISON_PACKAGE.DeviceService"

@OptIn(ExperimentalStdlibApi::class)
class MainActivity : AppCompatActivity() {

    private val clientId = "fc1ec90c567f4687b700ec8f0f237871"
    private val redirectUri = "http://com.pison.hello_world/callback"

    val mHandler = Handler(Looper.getMainLooper())

    var deviceService = DeviceService()

    lateinit var currentImage: ImageUri

    var uiWakeWord = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    @SuppressLint("WrongViewCast")
    override fun onStart(){
        super.onStart()

        val spotifyText = findViewById<EditText>(R.id.spotifyState) as TextView

        startService()

        // Set the connection parameters
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()
        //Log.d("MainActivity", "Connected! Yay!")

        val listener = object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d("MainActivity", "Connected! Yay!")
                // Now you can start interacting with App Remote
                connected()

                spotifyText.setText("Spotify: Connected")
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("MainActivity", throwable.message, throwable)
//                Log.d("Fuck you man...")
                // Something went wrong when attempting to connect! Handle errors here
                spotifyText.setText("Spotify: Disconnected")
            }
        }

        SpotifyAppRemote.connect(this, connectionParams, listener)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun connected() {
        // Then we will write some more code here.
//        spotifyAppRemote.apply {
//            this.playerApi.play("spotify:playlist:37i9dQZF1DX2sUQwD7tbmL")
//        }



        mHandler.post(object: Runnable{
            override fun run() {
                val wakewordText = findViewById<EditText>(R.id.wakeWordText) as TextView

                if(Application.wakeword == true){
                    wakewordText.text = "Wakeword: Awake"
                }
                else if(Application.wakeword == false){
                    wakewordText.text = "Wakeword: Asleep"
                }

                Application.spotifyAppRemote.playerApi.playerState.setResultCallback {
                    //if(it.track.imageUri != currentImage){
                        //val imgV = findViewById<EditText>(R.id.songImage) as ImageView
                        //imgV.setImageURI(Uri.parse(it.track.imageUri.toString()))
                    //    currentImage = it.track.imageUri
                    //}
                }
                mHandler.postDelayed(this,200)
            }
        })
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        // Aaand we will finish off here.
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun startService() {
        val startIntent = DeviceService.getStartIntent(this)
        ContextCompat.startForegroundService(this, startIntent)
        startService(startIntent)

        val bindIntent = Intent().apply {
            component = ComponentName(PISON_PACKAGE, PISON_CLASS)
        }
        bindService(bindIntent, connection, Context.BIND_AUTO_CREATE)
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, serviceBind: IBinder) {
            val service = Messenger(serviceBind)
            val message = Message.obtain(null, SERVER_INITIALIZE_COMMAND)
            service.send(message)
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            TODO("Not yet implemented")
        }
    }

    fun setWakeword(newWakeword: Boolean) {
        //uiWakeWord = newWakeword
    }
}

