package com.example.spotifyvulcancontrol

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spotifyvulcancontrol.ui.theme.SpotifyVulcanControlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpotifyVulcanControlTheme {
                MyApp()
            }
        }
    }
}

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
    var expanded by remember { mutableStateOf(false) }

    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp, vertical = 80.dp),
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
            Text("Wake Word Here?", style = MaterialTheme.typography.subtitle1)
            Text("", modifier = Modifier.padding(vertical = 18.dp))
        }

        // Song section Includes
        // Song name, artist name, heart img, slider
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ){
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text("", modifier = Modifier.padding(vertical = 20.dp))
                Text("Song Name", style = MaterialTheme.typography.h6,
                                       modifier = Modifier.padding(vertical = 10.dp))
                Text("Artist Name", style = MaterialTheme.typography.caption)
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End) {
                Text("", modifier = Modifier.padding(vertical = 25.dp))
                Text("Heart")
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
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ){
            Column() {
                Text("", modifier = Modifier.padding(vertical = 95.dp))
                Text("Slider Location here******************")
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ){
            Text("Prev Button", modifier = Modifier.padding(vertical = 180.dp, horizontal = 6.dp))
            Text("Play Button", modifier = Modifier.padding(vertical = 180.dp, horizontal = 6.dp))
            Text("Next Button", modifier = Modifier.padding(vertical = 180.dp, horizontal = 6.dp))
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Card(
                backgroundColor = MaterialTheme.colors.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .padding(horizontal = 17.dp),
            ) {
                Text("Inferences and EMI",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { expanded = !expanded },
                    ) {
                        Icon(
                            imageVector = if (expanded) Filled.ExpandMore else Filled.ExpandLess,
                            contentDescription = if (expanded) {
                                stringResource(R.string.show_more)
                            } else {
                                stringResource(R.string.show_less)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongTextSection(){
    // song section of UI here for clearity of ready
}

@Composable
private fun IMUandEMUScreen(){

}

@Composable
private fun Greetings(names: List<String> = List(1000) { "$it" } ) {
    LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
        items(items = names) { name ->
            Greeting(name = name)
        }
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

/*
@Preview(
    showBackground = true,
    widthDp = 320,
    uiMode = UI_MODE_NIGHT_YES,
    name = "DefaultPreviewDark"
)
@Preview(showBackground = true, widthDp = 320)
@Composable
fun DefaultPreview() {
    SpotifyVulcanControlTheme {
        Greetings()
    }
}*/

@Preview(showBackground = true, widthDp = 320)
@Composable
fun OnboardingPreview() {
    SpotifyVulcanControlTheme {
        OnboardingScreen(onContinueClicked = {})
    }
}