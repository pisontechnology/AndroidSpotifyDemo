package com.example.spotifyvulcancontrol.view


import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.spotifyvulcancontrol.viewModel.WaveProcessor
import kotlin.math.roundToInt

/*
@Preview
@Composable
fun WavePreview() {
    BaseAlertView(
        headerTitle = "Turn on bluetooth",
        contentText = "Turn on your phone’s bluetooth to connect the device.",
        positiveButtonDef = null,
        negativeButtonDef = ButtonDef("Help") { },
        content = {
            WaveView()
        })
}*/



@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WaveView(
    width: Dp = 400.dp,
    height: Dp = 400.dp,
    waveWidthRatio: Int = 20,
    waveProcessor: WaveProcessor = WaveProcessor()
) {
    val originalY = height / 2
    val path = Path()
    val waveWidth = width / waveWidthRatio
    val halfWaveWidth = waveWidth / 2
    val waves = ((width / halfWaveWidth) - 1).roundToInt()
    val deltaXAnim = rememberInfiniteTransition()
    val dx by deltaXAnim.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(80, easing = LinearEasing)
        )
    )
    var previousDx = 0.0f
    Canvas(
        modifier = Modifier
            .width(width)
            .height(height),
        onDraw = {
            val sizePx = height.toPx()
            drawPath(path = path, color = Color.White, style = Stroke(width = 4f))
            path.reset()
            path.moveTo((waveWidth.toPx() * -dx) + waveWidth.toPx(), originalY.toPx())
            if (dx < previousDx) {
                waveProcessor.tick()
            }
            previousDx = dx
            (0 until waves - 1).forEach { index ->
                path.relativeQuadraticBezierTo(
                    halfWaveWidth.toPx() / 2,
                    waveProcessor.getWave(index) * sizePx,
                    halfWaveWidth.toPx(),
                    0f
                )
            }
            path.quadraticBezierTo(
                width.toPx() - 10,
                originalY.toPx() + 20,
                width.toPx(),
                originalY.toPx()
            )

        }
    )
}