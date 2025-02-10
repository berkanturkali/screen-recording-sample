package com.android.example.screenrecording.ui

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import com.android.example.screenrecording.base.BaseDialogFragment
import com.android.example.screenrecording.databinding.DialogPreviewBinding
import com.android.example.screenrecording.service.ScreenRecordingService
import java.io.File

class PreviewDialog : BaseDialogFragment<DialogPreviewBinding>(DialogPreviewBinding::inflate) {

    private lateinit var player: ExoPlayer

    private lateinit var outputPath: String

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        outputPath = requireArguments().getString(ScreenRecordingService.OUTPUT_PATH)!!
        setupUI()
        setClickListeners()
    }

    @OptIn(UnstableApi::class)
    private fun setupUI() {
        player = ExoPlayer.Builder(requireContext())
            .setSeekForwardIncrementMs(5_000)
            .build()
        binding.previewPv.player = player
        binding.previewPv.setShowNextButton(false)
        binding.previewPv.setShowPreviousButton(false)
        player.setMediaItem(MediaItem.fromUri(outputPath))
        binding.thumbnailLayout.thumbnailIv.setImageBitmap(getThumbnail())
    }

    private fun setClickListeners() {
        binding.apply {
            closeIv.setOnClickListener {
                deleteTheVideoThenNavigateUp()
            }

            thumbnailLayout.playIv.setOnClickListener {
                thumbnailLayout.root.visibility = View.GONE
                previewPv.visibility = View.VISIBLE
                player.prepare()
                player.play()
            }
        }
    }

    private fun deleteTheVideoThenNavigateUp() {
        try {
            val file = File(outputPath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        findNavController().navigateUp()
    }

    private fun getThumbnail(): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val createVideoThumbnail = ThumbnailUtils.createVideoThumbnail(
                File(outputPath),
                android.util.Size(512, 512),
                null
            )
            createVideoThumbnail
        } else {
            ThumbnailUtils.createVideoThumbnail(
                outputPath,
                MediaStore.Video.Thumbnails.MINI_KIND
            )
        }
    }
}