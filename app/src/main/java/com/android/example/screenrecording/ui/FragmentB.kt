package com.android.example.screenrecording.ui

import android.os.Bundle
import android.view.View
import com.android.example.screenrecording.R
import com.android.example.screenrecording.base.BaseFragment
import com.android.example.screenrecording.databinding.FragmentBBinding

class FragmentB : BaseFragment<FragmentBBinding>(FragmentBBinding::inflate) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activityViewModel.navigationActionId = R.id.action_fragmentB_to_fragmentC
    }

}