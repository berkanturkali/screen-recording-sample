package com.android.example.screenrecording.model

interface ScreenRecordingServiceInteractionListener {

    fun onTimeUpdate(time: String)

    fun onTimerReachedMaxLimit()
}