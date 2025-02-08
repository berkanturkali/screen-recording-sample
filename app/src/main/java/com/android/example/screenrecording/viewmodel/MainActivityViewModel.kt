package com.android.example.screenrecording.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.example.screenrecording.R
import com.android.example.screenrecording.model.ScreenRecordButtonActions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivityViewModel : ViewModel() {

    var navigationActionId = R.id.action_fragmentA_to_fragmentB

    private var timeInSeconds = 0
    private var timerJob: Job? = null


    var dX = 0f
    var dY = 0f


    private val _screenRecordButtonAction = MutableLiveData<ScreenRecordButtonActions>()

    val screenRecordButtonAction: LiveData<ScreenRecordButtonActions> get() = _screenRecordButtonAction

    private val _time = MutableLiveData<String>()

    val time: LiveData<String> get() = _time

    private val _statusBarColor = MutableLiveData<Int>()
    val statusBarColor: LiveData<Int> get() = _statusBarColor

    fun setScreenRecordButtonAction(action: ScreenRecordButtonActions) {
        when (action) {
            ScreenRecordButtonActions.ACTION_START -> {
                startTimer()
                setStatusBarColor(R.color.recording_indicator_red)
            }

            ScreenRecordButtonActions.ACTION_STOP -> {
                stopTimer()
                setStatusBarColor(R.color.primary)
            }
        }
        _screenRecordButtonAction.value = action
    }

    fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                if (timeInSeconds == 60) {
                    stopTimer()
                    setScreenRecordButtonAction(ScreenRecordButtonActions.ACTION_STOP)
                    return@launch
                }
                _time.postValue(formatTime(timeInSeconds))
                timeInSeconds++
                delay(1000)
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timeInSeconds = 0
        _time.value = formatTime(timeInSeconds)
    }

    private fun formatTime(seconds: Int): String {
        val minutest = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutest, remainingSeconds)
    }

    private fun setStatusBarColor(color: Int) {
        _statusBarColor.value = color
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }

}