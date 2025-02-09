package com.android.example.screenrecording.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.viewbinding.ViewBinding
import com.android.example.screenrecording.Inflate
import com.android.example.screenrecording.ViewBindingHolder

abstract class BaseDialogFragment<VB : ViewBinding>(
    inflate: Inflate<VB>
) : DialogFragment() {

    private val holder = ViewBindingHolder(inflate)

    protected val binding: VB
        get() = holder.binding

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