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

    private val _screenRecordButtonAction = MutableLiveData<ScreenRecordButtonActions>()

    val screenRecordButtonAction: LiveData<ScreenRecordButtonActions> get() = _screenRecordButtonAction

    private val _time = MutableLiveData<String>()

    val time: LiveData<String> get() = _time

    fun setScreenRecordButtonAction(action: ScreenRecordButtonActions) {
        _screenRecordButtonAction.value = action
    }

    fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
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

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }

}