package com.example.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri

object SoundHelper {
    fun playStartShiftSound(context: Context, customUri: String? = null) {
        playSound(context, true, customUri)
    }

    fun playEndShiftSound(context: Context, customUri: String? = null) {
        playSound(context, false, customUri)
    }

    private fun playSound(context: Context, isStart: Boolean, customUri: String?) {
        try {
            val soundUri: Uri = if (!customUri.isNullOrEmpty()) {
                Uri.parse(customUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(context, soundUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                prepare()
                setOnCompletionListener {
                    it.release()
                }
            }
            mediaPlayer.start()
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to ToneGenerator
            Thread {
                try {
                    val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
                    if (isStart) {
                        toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 120)
                        Thread.sleep(150)
                        toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_HIGH_L, 150)
                    } else {
                        toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_LOW_L, 220)
                        Thread.sleep(220)
                        toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ABBR_ALERT, 180)
                    }
                    Thread.sleep(200)
                    toneGen.release()
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }.start()
        }
    }
}
