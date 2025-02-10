package com.android.example.screenrecording.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.distinctUntilChanged
import com.android.example.screenrecording.R
import com.android.example.screenrecording.model.ScreenRecordButtonActions

class MainActivityViewModel : ViewModel() {

    var navigationActionId = R.id.action_fragmentA_to_fragmentB

    var dX = 0f
    var dY = 0f
    private val _screenRecordButtonAction = MutableLiveData<ScreenRecordButtonActions>()

    val screenRecordButtonAction: LiveData<ScreenRecordButtonActions>
        get() = _screenRecordButtonAction
            .distinctUntilChanged()

    private val _time = MutableLiveData<String>()

    val time: LiveData<String> get() = _time

    private val _statusBarColor = MutableLiveData<Int>()
    val statusBarColor: LiveData<Int> get() = _statusBarColor

    init {
        setStatusBarColor(R.color.primary)
    }

    fun setScreenRecordButtonAction(action: ScreenRecordButtonActions) {
        when (action) {
            ScreenRecordButtonActions.ACTION_START -> {
                setStatusBarColor(R.color.recording_indicator_red)
            }

            ScreenRecordButtonActions.ACTION_STOP -> {
                setStatusBarColor(R.color.primary)
            }
        }
        _screenRecordButtonAction.value = action
    }

    fun setTime(time: String) {
        _time.value = time
    }


    private fun setStatusBarColor(color: Int) {
        _statusBarColor.value = color
    }

}