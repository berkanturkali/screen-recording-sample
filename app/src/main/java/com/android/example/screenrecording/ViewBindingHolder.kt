package com.android.example.screenrecording

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

typealias Inflate<T> = (LayoutInflater, ViewGroup?, Boolean) -> T

class ViewBindingHolder<VB : ViewBinding>(
    private val inflate: Inflate<VB>
) {
    private var _binding: VB? = null
    val binding: VB
        get() = _binding!!

    fun createBinding(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = inflate(inflater, container, false)
        return binding.root
    }

    fun destroyBinding() {
        _binding = null
    }
}