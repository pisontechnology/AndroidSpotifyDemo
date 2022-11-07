package com.example.spotifyvulcancontrol.viewModel

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