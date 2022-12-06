package com.example.spotifyvulcancontrol

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons.Filled
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
//import com.adamratzman.spotify.spotifyAppApi
import com.example.spotifyvulcancontrol.Application.Companion.spotifyAppRemote
import com.example.spotifyvulcancontrol.ui.theme.SpotifyVulcanControlTheme
import com.example.spotifyvulcancontrol.view.WaveView
import com.example.spotifyvulcancontrol.viewModel.WaveProcessor
import com.pison.core.shared.imu.EulerAngles
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import io.ktor.http.*
//import kaaes.spotify.webapi.android.SpotifyApi
//import kaaes.spotify.webapi.android.SpotifyService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

val defaultEulers: EulerAngles = object : EulerAngles {
    override val pitch: Float = 0.0f
    override val roll: Float = 0.0f
    override val yaw: Float = 0.0f
}

private const val PISON_PACKAGE = "com.example.spotifyvulcancontrol"
private const val PISON_CLASS = "$PISON_PACKAGE.DeviceService"

private var spotifyConnected = false

var lastSwipe = ""
var lastShake = ""
var lastGesture = ""

val waveProcessor: WaveProcessor = WaveProcessor()


class MainActivity : ComponentActivity() {

    private val clientId = "fc1ec90c567f4687b700ec8f0f237871"
    private val redirectUri = "https://com.example.spotifyvulcancontrol/callback"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent(){
            SpotifyVulcanControlTheme() {
                OnboardingScreen(onContinueClicked = {})
            }
        }
    }

    override fun onStart() {
        super.onStart()

        startService() // Set up background service

        connectToSpotify()

        Application.mMainActivity = this
    }

    public fun connectToSpotify(){
        // Set the connection parameters
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        val listener = object : Connector.ConnectionListener{
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d("MainActivity", "Connected! Yay!")
                // Now you can start interacting with App Remote
                connected()
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("MainActivity", throwable.message, throwable)
                // Something went wrong when attempting to connect! Handle errors here
            }
        }

        SpotifyAppRemote.connect(this, connectionParams, listener)
    }

    private fun connected(){
        // just called when we have a success ful connection will be useful for UI stuff
        spotifyConnected = true

        setContent {
            SpotifyVulcanControlTheme {
                OnboardingScreen(onContinueClicked = {})
            }
        }

        spotifyAppRemote.playerApi.playerState.setResultCallback {

        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun startService(){
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
}

//region Compose Functions

@Composable
private fun OnboardingScreen(
    onContinueClicked: () -> Unit
) {
    val songName = remember { mutableStateOf("") }
    val artistName = remember { mutableStateOf("")}
    val isPlaying = remember { mutableStateOf(false) }
    val songImage = remember { mutableStateOf(createBitmap(1000,1000))}
    val songMax = remember { mutableStateOf(0f)}
    val wakewordVal = remember { mutableStateOf(false)}
    val expanded = remember { mutableStateOf(false) }
    val songPosition = remember { mutableStateOf(0f)}
    val gesture = remember { mutableStateOf("")}
    val eulerAngles = remember { mutableStateOf(defaultEulers)}
    val positionText = remember { mutableStateOf("")}
    val maxText = remember { mutableStateOf("")}
    val songLiked = remember { mutableStateOf(false)}
    val helpPopUp = remember { mutableStateOf(false)}
    val adjustedStrength = remember{ mutableStateOf(0f)}
    val quitPopUp = remember{ mutableStateOf(false)}

    print(helpPopUp.value)

    // FIX LATER
    // Hacky solution: called way to often (if experiencing lag this is probably why)
    // updates the current song position variable to keep slider and everything looking nice
    Handler(Looper.getMainLooper()).post(object: Runnable{
        override fun run() {
            if(spotifyConnected){
                //println("Hacky Update Called")
                wakewordVal.value = Application.wakeword
                gesture.value = Application.currentGesture
                eulerAngles.value = Application.eulerAngles

                adjustedStrength.value = Application.rawAdcAverage
                waveProcessor.setAmplitude(adjustedStrength.value)

                spotifyAppRemote.playerApi.playerState.setResultCallback {
                    songPosition.value = it.playbackPosition.toFloat()
                    spotifyAppRemote.userApi.getLibraryState(it.track.uri).setResultCallback {
                        songLiked.value = it.isAdded
                    }
                }
            }
            Handler(Looper.getMainLooper()).postDelayed(this,50)
        }
    })

    // Getting needed info from spotify when events are called
    if(spotifyConnected){
        spotifyAppRemote.playerApi.subscribeToPlayerState().setEventCallback {
            songName.value = it.track.name
            artistName.value = it.track.artist.name
            isPlaying.value = it.isPaused
            songMax.value = it.track.duration.toFloat()
            songPosition.value = it.playbackPosition.toFloat()

            spotifyAppRemote.imagesApi.getImage(it.track.imageUri).setResultCallback {
                songImage.value = it
            }
        }
    }
    else{
        songName.value = "Pendinggggggggggggg..."
        artistName.value = "Pending..."
    }

    // Song Image Display
    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(30.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if(spotifyConnected){
                Image(
                    bitmap = songImage.value.asImageBitmap(),
                    contentDescription = "Test Cover",
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.TopCenter)
            }
            else{
                Image(
                    painter = painterResource(id = R.drawable.ic_testalbumcover_background),
                    contentDescription = "Test Cover",
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.TopCenter)
            }
        }

        WakewordDisplay(wakewordVal)

        // ************ Shuffle UI ***********
        /*
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 125.dp, horizontal = 30.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.Start
        ){
            Text("", modifier = Modifier.padding(14.dp))
            if(isShuffling.value){
                Image(painter = painterResource(id = R.drawable.shuffle_selected),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp)
                )
            }
            else{
                Image(painter = painterResource(id = R.drawable.shuffle_unslected),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp)
                )
            }
        }*/

        // Pop Up icon Interface
        PopupIcons(helpPopUp, quitPopUp)

        // Song section Includes
        // Song name, artist name, heart img
        SongAndLikeDisplay(songName, artistName, songLiked)

        SliderDisplay(songPosition, songMax, positionText, maxText)

        // Play, Pausing, Skip, Pev Interface
        PlayPauseSkipDisplay(isPlaying)
        
        // EMI Interface
        EMIPanel(expanded, gesture, eulerAngles)
        
        // Pop Up Interfaces
        if(quitPopUp.value){
            QuitPopup(quitPopUp)
        }
        
        if(helpPopUp.value){
            HelpPopup()
        }
    }
}

//region Wakeword Display

@Composable
private fun WakewordDisplay(
    wakewordVal: MutableState<Boolean>
){
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 120.dp, horizontal = 30.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.Start
    ){
        Text("", modifier = Modifier.padding(14.dp))
        if(wakewordVal.value){
            Image(painter = painterResource(id = R.drawable.wakeword_awake),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }
        else{
            Image(painter = painterResource(id = R.drawable.wakeword_asleep),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

//endregion

//region Song/Like Display

@Composable
private fun SongAndLikeDisplay(
    songName: MutableState<String>,
    artistName: MutableState<String>,
    songLiked: MutableState<Boolean>
){
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically
    ){
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Text("", modifier = Modifier.padding(vertical = 70.dp))
            if(songName.value.length >= 22){
                Box(modifier = Modifier.size(width = 305.dp, height = 52.dp)){
                    AutoScrollingLazyRow(list = (1..8).take(1)) {
                        Text(
                            "${songName.value}    ", style = MaterialTheme.typography.h6,
                            modifier = Modifier
                                .padding(vertical = 10.dp),
                            fontSize = 23.sp,
                        )
                    }
                }
            }
            else{
                Text(
                    "${songName.value}", style = MaterialTheme.typography.h6,
                    modifier = Modifier
                        .padding(vertical = 10.dp),
                    fontSize = 23.sp,
                )
            }
            Text("${artistName.value}", style = MaterialTheme.typography.caption, fontSize = 15.sp)
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End) {
            Text("", modifier = Modifier.padding(vertical = 71.dp))
            if(songLiked.value){
                Image(painter = painterResource(id = R.drawable.heart_full),
                    contentDescription = null,
                    modifier = Modifier.size(23.dp)
                )
            }
            else{
                Image(painter = painterResource(id = R.drawable.heart_empty),
                    contentDescription = null,
                    modifier = Modifier.size(23.dp)
                )
            }

        }
    }
}

//endregion

//region Slider Display

@Composable
private fun SliderDisplay(
    songPosition: MutableState<Float>,
    songMax: MutableState<Float>,
    positionText: MutableState<String>,
    maxText: MutableState<String>
){
    var positionMin: Long = 0
    var positionSec: Long = 0
    var maxMin: Long = 0
    var maxSec: Long = 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp, vertical = 210.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Slider(
            value = songPosition.value,
            onValueChange = { songPosition.value = it},
            valueRange = 0f..songMax.value,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colors.onSurface,
                activeTrackColor = MaterialTheme.colors.onSurface
            )
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp, vertical = 203.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Start
    ){
        positionMin = songPosition.value.toLong() / 1000 / 60
        positionSec = songPosition.value.toLong() / 1000 % 60
        positionText.value = String.format("%01d:%02d", positionMin, positionSec)
        Text(
            "${positionText.value}", // put current time here
            style = MaterialTheme.typography.caption,
            fontSize = 10.sp
        )

    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp, vertical = 203.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.End
    ){
        maxMin = songMax.value.toLong() / 1000 / 60
        maxSec = songMax.value.toLong() / 1000 % 60
        maxText.value = String.format("%01d:%02d", maxMin, maxSec)
        Text("${maxText.value}", // put full song duration here
            style = MaterialTheme.typography.caption,
            fontSize = 10.sp
        )
    }
}

//endregion

//region Play Pause Display

@Composable
private fun PlayPauseSkipDisplay(
    isPlaying: MutableState<Boolean>
){
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ){
        Image(painter = painterResource(id = R.drawable.ic_skip_previous),
            contentDescription = null,
            modifier = Modifier
                .padding(vertical = 110.dp, horizontal = 15.dp)
                .size(60.dp)
        )
        if(isPlaying.value){
            Image(painter = painterResource(id = R.drawable.play_button),
                contentDescription = null,
                modifier = Modifier
                    .padding(vertical = 110.dp, horizontal = 15.dp)
                    .size(60.dp)
            )
        }
        else{
            Image(painter = painterResource(id = R.drawable.pauce_button),
                contentDescription = null,
                modifier = Modifier
                    .padding(vertical = 110.dp, horizontal = 15.dp)
                    .size(60.dp)
            )
        }
        Image(painter = painterResource(id = R.drawable.ic_skip),
            contentDescription = null,
            modifier = Modifier
                .padding(vertical = 110.dp, horizontal = 15.dp)
                .size(60.dp)
        )
    }
}

//endregion

//region Pop-ups Display

@Composable
private fun PopupIcons(
    helpPopUp: MutableState<Boolean>,
    quitPopUp: MutableState<Boolean>
){
    Column(modifier = Modifier
        .padding(horizontal = 20.dp, vertical = 10.dp)
        .fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.End
    ) {
        IconButton(
            onClick = { helpPopUp.value = !helpPopUp.value }
        ) {
            Image(
                painter = painterResource(id = R.drawable.help_icon),
                contentDescription = null,
                modifier = Modifier.size(23.dp),
            )
        }
    }

    Column(modifier = Modifier
        .padding(horizontal = 20.dp, vertical = 10.dp)
        .fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        IconButton(
            onClick = {
                //QuitPopup() System.exit(0)
                quitPopUp.value = !quitPopUp.value
            }
        ) {
            Image(
                painter = painterResource(id = R.drawable.cancel_icon),
                contentDescription = null,
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

@Composable
private fun HelpPopup(){
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier
            .background(color = MaterialTheme.colors.primary)
            .size(width = 300.dp, height = 330.dp)
        ){
            Column() {
                Text(text = "How to Use: *TEMP*",
                    style = MaterialTheme.typography.h6,
                    fontSize = 23.sp,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                )
                Text("Shake Index - activate/deactivate gestures", modifier = Modifier.padding(horizontal = 13.dp, vertical = 4.dp))
                Text("Index - Play/Pause Music", modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp))
                Text("Index Swipe Left/Right - Prev/Next Song", modifier = Modifier.padding(horizontal = 13.dp, vertical = 4.dp))
                Text("Index Swipe Up/Down - Volume Up/Down", modifier = Modifier.padding(horizontal = 13.dp, vertical = 4.dp))
                Text("Index Swipe Up/Down Hold - Continuous Volume Up/Down", modifier = Modifier.padding(horizontal = 13.dp, vertical = 4.dp))
                Text("Thumb Up - Like/UnLike Song", modifier = Modifier.padding(horizontal = 13.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun QuitPopup(quitPopUp: MutableState<Boolean>){
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(backgroundColor = MaterialTheme.colors.primary,
            modifier = Modifier.size(height = 160.dp, width = 330.dp)
        ){
            Column(verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 20.dp, horizontal = 20.dp)) {
                Text("Shut down Vulcan Spotify?", style = MaterialTheme.typography.h6,
                    fontSize = 18.sp,
                color = Color.White)
            }
            Row(verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(vertical = 23.dp, horizontal = 15.dp)){
                Text("", modifier = Modifier.padding(horizontal = 11.5.dp))
                Button(
                    onClick = { System.exit(0) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                    modifier = Modifier.size(width = 100.dp, height = 40.dp)
                ) {
                    Text("Yes", color = Color.Black)
                }
                Text("", modifier = Modifier.padding(horizontal = 25.dp))
                Button(
                    onClick = { quitPopUp.value = false },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                    modifier = Modifier.size(width = 100.dp, height = 40.dp)
                ) {
                    Text("No", color = Color.Black)
                }
            }
        }
    }
}

//endregion

//region EMI Display

@Composable
private fun EMIPanel(
    expanded: MutableState<Boolean>,
    gesture: MutableState<String>,
    eulerAngles: MutableState<EulerAngles>
){

    val extraPadding by animateDpAsState(
        if (expanded.value) 50.dp else 30.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )


    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            backgroundColor = MaterialTheme.colors.primary,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Row() {
                Column(
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        "Inferences and EMI",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(extraPadding)
                ) {
                }
                Column(
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.End
                ) {
                    IconButton(
                        onClick = { expanded.value = !expanded.value }
                    ) {
                        Icon(
                            imageVector = if (expanded.value) Filled.ExpandMore else Filled.ExpandLess,
                            contentDescription = if (expanded.value) {
                                stringResource(R.string.show_more)
                            } else {
                                stringResource(R.string.show_less)
                            }
                        )
                    }
                }
            }
            if(expanded.value){
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RealTimePortrait(gesture, eulerAngles)
                }
            }
        }
    }
}

@Composable
private fun RealTimePortrait(
    gesture: MutableState<String>,
    eulerStream: MutableState<EulerAngles>
) {
    Column(
        //modifier = Modifier
        //    .fillMaxSize()
    ) {
        Box(modifier = Modifier
            .weight(1f)
            .padding(bottom = 10.dp)) {
            SignalDisplay(gesture)
        }
        Box(modifier = Modifier
            .weight(0.8f)
            .padding(bottom = 20.dp)) {
            EulersDisplay(eulerStream = eulerStream)
        }
    }
}

@Composable
private fun EulersDisplay(
    eulerStream: MutableState<EulerAngles>
){
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        @Composable
        fun Dial(rotation: Float, label: String) {
            Column(
                Modifier
                    .width(100.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 20.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_dial_arrow),
                    contentDescription = "inner compass",
                    modifier = Modifier
                        .rotate(rotation)
                        .aspectRatio(1f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        //val eulers by eulerStream.subscribeAsState(initial = defaultEulers)
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth())
                    {
                Dial(rotation = -(eulerStream.value.roll - 90f), label = "Roll")
                Row(modifier = Modifier.padding(horizontal = 60.dp)) {
                    Dial(rotation = eulerStream.value.yaw + 90f, label = "Yaw")
                    Row(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Dial(rotation = eulerStream.value.pitch - 180f, label = "Pitch")
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalDisplay(
    gesture:  MutableState<String>
    //adjustedStrength: MutableState<Float>
    //waveProcessor: WaveProcessor,
) {
    Column {
        VerdictHeader(gesture)
        BoxWithConstraints{
            //println("reconstraint")
            // Is an import for com.pison.neohub.view.WaveView
            // it.float will be the adc I think
            WaveView(
                waveProcessor = waveProcessor,
                width = maxWidth,
                height = maxHeight
            )
        }

    }
}

@Composable
private fun VerdictHeader(
    gesture:  MutableState<String>
) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(180.dp)) {
        Text(
            text = gestureInputs(gesture.value).first,
            color = if(gestureInputs(gesture.value).second) Color.DarkGray else Color.White,
            fontSize = 24.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 40.dp)
        )
        Text(
            text = shakeInputs(gesture.value).first,
            color = if(shakeInputs(gesture.value).second) Color.DarkGray else Color.White,
            fontSize = 24.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp)
        )
        Text(
            text = swipeInputs(gesture.value).first,
            color = if(swipeInputs(gesture.value).second) Color.DarkGray else Color.White,
            fontSize = 24.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp)
        )
    }
}

//endregion

//region gestureDisplay

fun gestureInputs(gesture: String): Pair<String, Boolean>{
    when (gesture){
        "DEBOUNCE_LDA_INEH" -> { lastGesture = "INEH"
            return "INEH" to false }
        "DEBOUNCE_LDA_FHEH" -> { lastGesture = "FHEH"
            return "FHEH" to false }
        "DEBOUNCE_LDA_TEH" -> { lastGesture = "TEH"
            return "TEH" to false }
        else -> {
            return lastGesture to true
        }
    }
}

fun shakeInputs(gesture: String): Pair<String, Boolean>{
    when (gesture){
        "SHAKE" -> { lastShake = gesture
            return gesture to false }
        "SHAKE_N_INEH" -> { lastShake = gesture
            return gesture to false }
        "SHAKE_N_FHEH" -> { lastShake = gesture
            return gesture to false }
        "SHAKE_N_TEH" -> { lastShake = gesture
            return gesture to false }
        else -> {
            return lastShake to true
        }
    }
}

fun swipeInputs(gesture: String): Pair<String, Boolean> {
    when (gesture){
        "INEH_SWIPE_RIGHT" -> { lastSwipe = gesture
            return gesture to false }
        "INEH_SWIPE_LEFT" -> { lastSwipe = gesture
            return gesture to false }
        "FHEH_SWIPE_RIGHT" -> { lastSwipe = gesture
            return gesture to false }
        "FHEH_SWIPE_LEFT" -> { lastSwipe = gesture
            return gesture to false }
        "TEH_SWIPE_RIGHT" -> { lastSwipe = gesture
            return gesture to false }
        "TEH_SWIPE_LEFT" -> { lastSwipe = gesture
            return gesture to false }
        "INEH_SWIPE_UP" -> { lastSwipe = gesture
            return gesture to false }
        "INEH_SWIPE_DOWN" -> { lastSwipe = gesture
            return gesture to false }
        "FHEH_SWIPE_UP" -> { lastSwipe = gesture
            return gesture to false }
        "FHEH_SWIPE_DOWN" -> { lastSwipe = gesture
            return gesture to false }
        "TEH_SWIPE_UP" -> { lastSwipe = gesture
            return gesture to false }
        "TEH_SWIPE_DOWN" -> { lastSwipe = gesture
            return gesture to false }
        else -> {
            return lastSwipe to true
        }
    }
}

//endregion

@Preview
@Preview(device = Devices.AUTOMOTIVE_1024p, widthDp = 740, heightDp = 360)
@Composable
fun OnboardingPreview() {
    SpotifyVulcanControlTheme {
        OnboardingScreen(onContinueClicked = {})
    }
}

//endregion