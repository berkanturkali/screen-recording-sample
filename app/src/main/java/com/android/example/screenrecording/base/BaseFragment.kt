package com.android.example.screenrecording.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewbinding.ViewBinding
import com.android.example.screenrecording.Inflate
import com.android.example.screenrecording.ViewBindingHolder
import com.android.example.screenrecording.viewmodel.MainActivityViewModel


abstract class BaseFragment<VB : ViewBinding>(
    inflate: Inflate<VB>
) : Fragment() {

    private val holder = ViewBindingHolder(inflate)

    protected val binding: VB
        get() = holder.binding

    protected val activityViewModel by activityViewModels<MainActivityViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return holder.createBinding(inflater, container)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        holder.destroyBinding()
    }

}