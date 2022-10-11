package com.pison.hello_world

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.*
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.pison.hello_world.databinding.HapticsFragmentBinding
import kotlinx.android.synthetic.main.haptics_fragment.*

class HapticsFragment : Fragment(), OnSeekBarChangeListener {

    private lateinit var viewModel: HapticsViewModel
    private lateinit var binding: HapticsFragmentBinding

    private var hapticCommandType = 5
    private var durationMs = 50
    private var intensity = 0
    private var numberOfBursts = 0

    companion object {
        const val TAG = "HAPTICS FRAGMENT"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.haptics_fragment,
            container,
            false
        )
        viewModel = ViewModelProvider(this).get(HapticsViewModel::class.java)

        binding.viewModel = viewModel

        binding.durationSeekbar.setOnSeekBarChangeListener(this)
        binding.intensitySeekbar.setOnSeekBarChangeListener(this)
        binding.numberSeekbar.setOnSeekBarChangeListener(this)

        showHideHapticOptions(false)

        viewModel.onOffSwitchState.observe(viewLifecycleOwner, { isSwitchOnOrOff ->
            showHideHapticOptions(isSwitchOnOrOff)
        })

        viewModel.hapticCommandType.observe(viewLifecycleOwner, { hapticCommandTypeLiveData ->
            hapticCommandType = hapticCommandTypeLiveData
            Log.d(TAG, "Haptic code updated with $hapticCommandTypeLiveData")
            Log.d(TAG, "Haptic local data updated with $hapticCommandType")
        })

        viewModel.durationMs.observe(viewLifecycleOwner, { durationLiveData ->
            Log.d(TAG, "Duration updated with $durationLiveData")
            durationMs = durationLiveData
            binding.durationValue.text = durationLiveData.toString()
            binding.durationSeekbar.progress = convertDurationToProgress(durationLiveData)
        })

        viewModel.intensity.observe(viewLifecycleOwner, { intensityLiveData ->
            Log.d(TAG, "Intensity updated with $intensityLiveData")
            intensity = intensityLiveData
            binding.intensityValue.text = intensityLiveData.toString()
            binding.intensitySeekbar.progress = convertIntensityToProgress(intensityLiveData)
        })

        viewModel.numberBursts.observe(viewLifecycleOwner, { numberOfBurstsLiveData ->
            Log.d(TAG, "Number of bursts updated with $numberOfBurstsLiveData")
            numberOfBursts = numberOfBurstsLiveData
            binding.numberValue.text = numberOfBurstsLiveData.toString()
            binding.numberSeekbar.progress = numberOfBurstsLiveData
        })

        viewModel.deviceConnected.observe(viewLifecycleOwner, { isDeviceConnected ->
            if (!isDeviceConnected) {
                hideHapticsViews()
            }
            val connectedTextResId = if (isDeviceConnected) {
                R.string.device_status_connected
            } else {
                R.string.device_status_disconnected
            }
            sendHapticButton.isEnabled = isDeviceConnected
            binding.onOffSwitch.isEnabled = isDeviceConnected
            binding.deviceStatusText.text = getString(connectedTextResId)
        })

        viewModel.errorReceived.observe(viewLifecycleOwner, { error ->
            binding.errorText.text = error.message
        })

        binding.onOffSwitch.setOnClickListener {
            val isSwitchOn = binding.onOffSwitch.isChecked
            viewModel.onOnOffSwitchStateChanged(isSwitchOn)
            viewModel.clearHapticValues()
            Log.d(TAG, "onOffSwitch clicked swtichOn is $isSwitchOn")
        }

        binding.sendHapticButton.setOnClickListener {
            Log.d(
                TAG,
                "sendHaptic Button clicked with values $hapticCommandType, $durationMs, $intensity, $numberOfBursts"
            )
            viewModel.sendHaptic(hapticCommandType, durationMs, intensity, numberOfBursts)
            viewModel.clearHapticValues()
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        viewModel.connectToDevice()
    }

    override fun onStop() {
        super.onStop()
        viewModel.disposeDisposables()
        viewModel.clearHapticValues()
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        Log.d(TAG, "onProgressChanged clicked with $progress")
        if (!fromUser) {
            return
        }
        when (seekBar.id) {
            durationSeekbar.id -> viewModel.onDurationUpdated(convertProgressToDuration(progress))
            intensitySeekbar.id -> viewModel.onIntensityUpdated(convertProgressToIntensity(progress))
            numberSeekbar.id -> viewModel.onNumberOfBurstsUpdated(progress)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
    }

    private fun convertProgressToDuration(progress: Int): Int {
        val durationMs = (progress * 195) + 50
        Log.d(TAG, "Progress: to $progress -> duration: $durationMs ")
        return durationMs
    }

    private fun convertDurationToProgress(durationMs: Int): Int {
        return (durationMs - 50) / 195
    }

    private fun convertProgressToIntensity(progress: Int):Int {
        return progress * 10
    }

    private fun convertIntensityToProgress(intensity: Int): Int {
        return intensity / 10
    }

    private fun showHideHapticOptions(shouldShow: Boolean) {
        Log.d(TAG, "showHideHapticsOptions clicked with $shouldShow")
        when (shouldShow) {
            true -> showHapticsViews()
            else -> hideHapticsViews()
        }
    }

    private fun showHapticsViews() {
        binding.hapticTitle.visibility = VISIBLE

        binding.onOffSwitch.text = getString(R.string.on)
        binding.onOffSwitch.isChecked = true

        binding.hapticTypesRadioButtons.clearCheck()
        binding.hapticTypesRadioButtons.visibility = VISIBLE
        binding.intensitySeekbar.visibility = VISIBLE
        binding.numberSeekbar.visibility = VISIBLE
        binding.durationSeekbar.visibility = VISIBLE

        binding.intensityValue.visibility = VISIBLE
        binding.burstIntensityTitle.visibility = VISIBLE
        binding.durationTitle.visibility = VISIBLE

        binding.numberValue.visibility = VISIBLE
        binding.numberTitle.visibility = VISIBLE
        binding.durationValue.visibility = VISIBLE

        binding.sendHapticButton.visibility = VISIBLE
    }

    private fun hideHapticsViews() {
        binding.hapticTitle.visibility = GONE

        binding.onOffSwitch.text = getString(R.string.off)
        binding.onOffSwitch.isChecked = false

        binding.hapticTypesRadioButtons.visibility = GONE
        binding.intensitySeekbar.visibility = GONE
        binding.numberSeekbar.visibility = GONE
        binding.durationSeekbar.visibility = GONE

        binding.intensitySeekbar.progress = 0

        binding.intensityValue.visibility = GONE
        binding.burstIntensityTitle.visibility = GONE
        binding.durationTitle.visibility = GONE

        binding.numberValue.visibility = GONE
        binding.numberTitle.visibility = GONE
        binding.durationValue.visibility = GONE

        binding.sendHapticButton.visibility = GONE
    }
}