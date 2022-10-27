package com.example.spotifyvulcancontrol

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.media.AudioManager
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons.Filled
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pison.core.client.newPisonSdkInstance
import com.example.spotifyvulcancontrol.ui.theme.SpotifyVulcanControlTheme
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.example.spotifyvulcancontrol.Application.Companion.spotifyAppRemote
import com.spotify.android.appremote.api.SpotifyAppRemote
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.Track

private const val PISON_PACKAGE = "com.example.spotifyvulcancontrol"
private const val PISON_CLASS = "$PISON_PACKAGE.DeviceService"

private var spotifyConnected = false

class MainActivity : ComponentActivity() {

    private val clientId = "fc1ec90c567f4687b700ec8f0f237871"
    private val redirectUri = "https://com.example.spotifyvulcancontrol/callback"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Handler(Looper.getMainLooper()).post(object: Runnable{
            override fun run() {
                setContent {
                    println("did i get this far????")
                    SpotifyVulcanControlTheme {
                        MyApp()
                    }
                }
                Handler(Looper.getMainLooper()).postDelayed(this,500)
            }
        })
    }

    override fun onStart() {
        super.onStart()

        startService() // Set up background service

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
private fun MyApp() {
    var shouldShowOnboarding by rememberSaveable { mutableStateOf(true) }

    if (shouldShowOnboarding) {
        OnboardingScreen(onContinueClicked = { shouldShowOnboarding = false })
    } else {
        Greetings()
    }
}

@Composable
private fun OnboardingScreen(onContinueClicked: () -> Unit) {

    val expanded = remember { mutableStateOf(false) }

    val extraPadding = if (expanded.value) 300.dp else 30.dp

    var songName = ""
    var artistName = ""
    var songCoverUri: ImageUri // TODO: Uri not working with current image compose
    var likeSong = false
    var songLength: Long // TODO: The fuck is Long? (Note: some shit in kotlin)
    var currentLength = 0 // TODO: For some reason this is soooo much more work then it should be

    println("shiiiiiittttttttttt")

    /*if(spotifyConnected){
        spotifyAppRemote.playerApi.playerState.setResultCallback {
            songName = it.track.name
            artistName = it.track.artist.name
            songCoverUri = it.track.imageUri
            songLength = it.track.duration
        }
    }
    else{
        songName = "Pending..."
        artistName = "Pending..."
    }

    if(songName == ""){
        println("shiiiiiittttttttttt")
    }*/

    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(30.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_testalbumcover_background),
                contentDescription = "Test Cover",
                modifier = Modifier.fillMaxSize(),
                alignment = Alignment.TopCenter)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ){
            Text("", modifier = Modifier.padding(14.dp))
            Text("Wake Word Here?", style = MaterialTheme.typography.subtitle1)
        }

        // Song section Includes
        // Song name, artist name, heart img, slider
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically
        ){
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text("", modifier = Modifier.padding(vertical = 60.dp))
                Text(songName, style = MaterialTheme.typography.h6,
                                       modifier = Modifier.padding(vertical = 10.dp),
                                       fontSize = 23.sp
                )
                Text(artistName, style = MaterialTheme.typography.caption, fontSize = 15.sp)
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End) {
                Text("", modifier = Modifier.padding(vertical = 66.dp))
                Image(painter = painterResource(id = R.drawable.heart_empty),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.Start
            ){
                Text("Slider Alignment")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ){
            Text("***********Slider Location here*************",
                modifier = Modifier.padding(vertical = 220.dp))
        }

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
            Image(painter = painterResource(id = R.drawable.play_button),
                  contentDescription = null,
                  modifier = Modifier
                      .padding(vertical = 110.dp, horizontal = 15.dp)
                      .size(60.dp)
            )
            Image(painter = painterResource(id = R.drawable.ic_skip),
                contentDescription = null,
                modifier = Modifier
                    .padding(vertical = 110.dp, horizontal = 15.dp)
                    .size(60.dp)
            )
        }


        // EMI Inferences stuff here *****

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
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(extraPadding), //bottom = extraPadding)
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
                        RealTimePortrait()
                    }
                }
            }
        }
    }
}

@Composable
private fun Greetings(names: List<String> = List(1000) { "$it" } ) {

    /*LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
        items(items = names) { name ->
            Greeting(name = name)
        }
    }*/
}

@Composable
private fun RealTimePortrait(
    /*eulerStream: Observable<EulerAngles>,
    waveProcessor: WaveProcessor,
    ldaOutput: Observable<LdaVerdict>,
    eventOutput: Observable<HighlightedInference>,
    swipeOutput: Observable<HighlightedInference>,
    sqiOutput: Observable<SqiErrorVerdict>,
    rssiOutput: Observable<RssiDisplay>*/
) {
    Column(
        //modifier = Modifier
        //    .fillMaxSize()
    ) {
        Box(modifier = Modifier
            .weight(1f)
            .padding(bottom = 10.dp)) {
            SignalDisplay()//waveProcessor = waveProcessor, ldaOutput, eventOutput, swipeOutput, rssiOutput)
        }
        Box(modifier = Modifier
            .weight(0.8f)
            .padding(bottom = 20.dp)) {
            EulersDisplay()//eulerStream = eulerStream, sqiOutput = sqiOutput)
        }
    }
}

@Composable
private fun EulersDisplay(
    //eulerStream: Observable<EulerAngles>,
    //sqiOutput: Observable<SqiErrorVerdict>
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
                    .padding(top = 40.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_dial_arrow),
                    contentDescription = "inner compass",
                    modifier = Modifier
                        .rotate(rotation)
                        .aspectRatio(1f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        //val eulers by eulerStream.subscribeAsState(initial = defaultEulers)
        //val sqi by sqiOutput.subscribeAsState(initial = SqiErrorVerdict(listOf("[No Verdict]")))
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            /*Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()) {
                Text(
                    text = "EEMI",
                    color = Color.DarkGray,//if (sqi.values.contains("High EMI")) MaterialTheme.colors.primary else Color.DarkGray,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .padding(top = 30.dp, start = 30.dp)
                )
                Text(
                    text = "ENAC",
                    color = Color.DarkGray,//if (sqi.values.contains("Not a class")) MaterialTheme.colors.primary else Color.DarkGray,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .padding(top = 30.dp, start = 30.dp)
                )
                Text(
                    text = "ENOISE",
                    color = Color.DarkGray,//if (sqi.values.contains("High noise")) MaterialTheme.colors.primary else Color.DarkGray,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .padding(top = 30.dp, start = 30.dp)
                )
                Text(
                    text = "EBLE",
                    color = Color.DarkGray,//if (sqi.values.contains("BLE packet drop rate")) MaterialTheme.colors.primary else Color.DarkGray,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .padding(top = 30.dp, start = 30.dp)
                )
                Text(
                    text = "ELOSNR",
                    color = Color.DarkGray,//if (sqi.values.contains("Low SNR")) MaterialTheme.colors.primary else Color.DarkGray,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .padding(top = 30.dp, start = 30.dp)
                )
            }*/
            Column(
                Modifier
                    .padding(start = 50.dp)
                    .weight(1f)
                    .fillMaxWidth()){
                Row() {
                    Dial(rotation = 0 + 90f, label = "Yaw")//eulers.yaw + 90f, label = "Yaw")
                    Dial(rotation = 0 - 180f, label = "Pitch")//eulers.pitch - 180f, label = "Pitch")
                }
                Dial(rotation = -(0 - 90f), label = "Roll")//-(eulers.roll - 90f), label = "Roll")
            }
        }
    }
}

@Composable
private fun SignalDisplay(
    /*waveProcessor: WaveProcessor,
    ldaOutput: Observable<LdaVerdict>,
    eventOutput: Observable<HighlightedInference>,
    swipeOutput: Observable<HighlightedInference>,
    rssiOutput: Observable<RssiDisplay>*/
) {
    Column {
        VerdictHeader()//ldaOutput = ldaOutput, eventOutput = eventOutput, swipeOutput = swipeOutput, rssiOutput = rssiOutput)
        BoxWithConstraints {
            //println("reconstraint")
            // Is an import for com.pison.neohub.view.WaveView
            /*WaveView(
                waveProcessor = waveProcessor,
                width = maxWidth,
                height = maxHeight
            )*/
        }

    }
}

@Composable
private fun VerdictHeader(
    /*ldaOutput: Observable<LdaVerdict>,
    eventOutput: Observable<HighlightedInference>,
    swipeOutput: Observable<HighlightedInference>,
    rssiOutput: Observable<RssiDisplay>*/
) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(150.dp)) {
        //val ldaVerdict by ldaOutput.subscribeAsState(initial = LdaVerdict("[No Verdict]", 0.0f))
        //val event by eventOutput.subscribeAsState(initial = HighlightedInference("[No Event]", false))
        //val swipe by swipeOutput.subscribeAsState(initial = HighlightedInference("[No Swipe]", false))
        //val rssi by rssiOutput.subscribeAsState(initial = RssiDisplay("", false))
        Text(
            text = "[No Verdict]", //ldaVerdict.name,
            color = Color.White,
            fontSize = 24.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 40.dp)
        )
        Text(
            text = "[No Event]", //event.inference,
            color = Color.White, //if (event.highlight) MaterialTheme.colors.primary else Color.DarkGray,
            fontSize = 24.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp)
        )
        Text(
            text = "[No Swipe]", //swipe.inference,
            color = Color.White,//if (swipe.highlight) MaterialTheme.colors.primary else Color.DarkGray,
            fontSize = 24.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp)
        )
        /*Text(
            text = "?",//rssi.rssi,
            color = Color.Red,//if (rssi.highlight) Color.Red else Color.White,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp)
        )*/
    }
}

@Composable
private fun Greeting(name: String) {
    Card(
        backgroundColor = MaterialTheme.colors.primary,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        CardContent(name)
    }
}

@Composable
private fun CardContent(name: String) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .padding(12.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp)
        ) {
            Text(text = "Hello, ")
            Text(
                text = name,
                style = MaterialTheme.typography.h4.copy(
                    fontWeight = FontWeight.ExtraBold
                )
            )
            if (expanded) {
                Text(
                    text = ("Composem ipsum color sit lazy, " +
                            "padding theme elit, sed do bouncy. ").repeat(4),
                )
            }
        }
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = if (expanded) Filled.ExpandLess else Filled.ExpandMore,
                contentDescription = if (expanded) {
                    stringResource(R.string.show_less)
                } else {
                    stringResource(R.string.show_more)
                }

            )
        }
    }
}

// Preview for EMI stuff
/*
@Preview(showBackground = true, widthDp = 320)
@Composable
fun DefaultPreview() {
    SpotifyVulcanControlTheme {
        RealTimePortrait()//eulerStream, waveProcessor, ldaOutput, eventOutput, swipeOutput, sqiOutput, rssiOutput)
    }
}*/

/* For later
class RealTimeVizViewModelAdapter(delegate: RealTimeVizViewModel) :
    AndroidViewModelAdapter<RealTimeVizViewModel>(delegate)

class RealTimeVizActivity : BaseActivity<RealTimeVizViewModel, RealTimeVizViewModelAdapter>() {
    override val viewModelAdapterClass: Class<out RealTimeVizViewModelAdapter> =
        RealTimeVizViewModelAdapter::class.java

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RealTimeVizView(
                viewModel.eulerAngles,
                viewModel.waveProcessor,
                viewModel.ldaOutput,
                viewModel.eventOutput,
                viewModel.swipeOutput,
                viewModel.sqiOutput,
                viewModel.signalRssiOutput
            )
        }
    }
}
 */

@Preview
@Preview(device = Devices.AUTOMOTIVE_1024p, widthDp = 740, heightDp = 360)
@Composable
fun OnboardingPreview() {
    SpotifyVulcanControlTheme {
        OnboardingScreen(onContinueClicked = {})
    }
}

//endregion