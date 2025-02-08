package com.android.example.screenrecording.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.android.example.screenrecording.R
import com.android.example.screenrecording.model.ScreenRecordButtonActions

class MainActivityViewModel : ViewModel() {

    var navigationActionId = R.id.action_fragmentA_to_fragmentB

    private val _screenRecordButtonAction = MutableLiveData<ScreenRecordButtonActions>()

    val screenRecordButtonAction: LiveData<ScreenRecordButtonActions> get() = _screenRecordButtonAction

    fun setScreenRecordButtonAction(action: ScreenRecordButtonActions) {
        _screenRecordButtonAction.value = action
    }

}