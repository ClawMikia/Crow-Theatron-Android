package com.crowtheatron.app.settings

import android.text.InputType
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.crowtheatron.app.R
import com.crowtheatron.app.data.AppPrefs
import com.crowtheatron.app.databinding.ActivitySettingsBinding
import com.crowtheatron.app.ui.showCrowMessage
import com.crowtheatron.app.ui.setContentWithCrowInsets
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { AppPrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentWithCrowInsets(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        bindValues()

        binding.seekBarInterval.max = 59
        binding.seekBarInterval.progress = (prefs.defaultSeekJumpSec - 1).coerceIn(0, 58)
        binding.seekValue.setOnClickListener { editNumber("Skip interval (sec)", prefs.defaultSeekJumpSec.toDouble(), false) { prefs.defaultSeekJumpSec = it.toInt() } }
        binding.speedValue.setOnClickListener { editNumber("Speed step (x)", prefs.defaultSpeedStep.toDouble(), true) { prefs.defaultSpeedStep = it.toFloat() } }
        binding.pitchValue.setOnClickListener { editNumber("Pitch step (st)", prefs.defaultPitchStepSemitones.toDouble(), true) { prefs.defaultPitchStepSemitones = it.toFloat() } }
        binding.trimValue.setOnClickListener { editNumber("Trim step (ms)", prefs.defaultTrimStepMs.toDouble(), false) { prefs.defaultTrimStepMs = it.toLong() } }
        binding.volumeValue.setOnClickListener { editNumber("Volume step (%)", prefs.defaultVolumeStepPercent.toDouble(), false) { prefs.defaultVolumeStepPercent = it.toInt() } }
        binding.ffrwValue.setOnClickListener { editNumber("Fast forward / rewind (sec)", prefs.defaultSeekJumpSec.toDouble(), false) { prefs.defaultSeekJumpSec = it.toInt() } }

        binding.seekBarInterval.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val value = (progress + 1).coerceAtLeast(1)
                prefs.defaultSeekJumpSec = value
                bindValues()
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.btnResetPlaybackDefaults.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset Display & Playback")
                .setMessage("Restore all values in this section to their default settings?")
                .setPositiveButton("Reset") { _, _ ->
                    resetPlaybackDefaults()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun bindValues() {
        binding.seekValue.text = prefs.defaultSeekJumpSec.toString()
        binding.pitchValue.text = formatNumber(prefs.defaultPitchStepSemitones.toDouble(), true) + " st"
        binding.speedValue.text = formatNumber(prefs.defaultSpeedStep.toDouble(), true) + " x"
        binding.trimValue.text = prefs.defaultTrimStepMs.toString() + " ms"
        binding.volumeValue.text = prefs.defaultVolumeStepPercent.toString() + " %"
        binding.ffrwValue.text = prefs.defaultSeekJumpSec.toString() + " sec"
    }

    private fun editNumber(title: String, current: Double, allowDecimal: Boolean, onSave: (Double) -> Unit) {
        val layout = TextInputLayout(this).apply {
            hint = title
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val input = TextInputEditText(layout.context).apply {
            setText(formatNumber(current, allowDecimal))
            inputType = if (allowDecimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_NUMBER
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padH = (20 * resources.displayMetrics.density).toInt()
            val padV = (14 * resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)
            addView(layout, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        layout.addView(input)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val parsed = input.text?.toString()?.trim()?.toDoubleOrNull()
                if (parsed == null || parsed <= 0.0) {
                    showCrowMessage("Invalid value", "Enter a positive number.")
                    return@setPositiveButton
                }
                onSave(parsed)
                bindValues()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetPlaybackDefaults() {
        prefs.defaultSeekJumpSec = 10
        prefs.defaultPitchStepSemitones = 1f
        prefs.defaultSpeedStep = 0.1f
        prefs.defaultTrimStepMs = 10000L
        prefs.defaultVolumeStepPercent = 5
        bindValues()
        binding.seekBarInterval.progress = (prefs.defaultSeekJumpSec - 1).coerceIn(0, 58)
        showCrowMessage("Reset complete", "Display & Playback values were restored.")
    }

    private fun formatNumber(value: Double, allowDecimal: Boolean): String {
        return if (allowDecimal) {
            val s = "%.3f".format(value).trimEnd('0').trimEnd('.')
            if (s.isBlank()) "0" else s
        } else {
            value.toLong().toString()
        }
    }
}
