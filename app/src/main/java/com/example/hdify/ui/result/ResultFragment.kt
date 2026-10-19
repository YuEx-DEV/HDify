package com.example.hdify.ui.result

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.hdify.R
import com.example.hdify.databinding.FragmentResultBinding
import com.example.hdify.logging.HdLogger
import com.example.hdify.util.safePopBackStack
import com.example.hdify.util.setOnSingleClickListener
import com.example.hdify.video.VideoPostProcessor
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class ResultFragment : Fragment() {

    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!

    private var isVideo = false
    private var isSaved = false
    private var isSavingInProgress = false
    private var currentVideoUri: Uri? = null
    private var tempFilePath: String? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var videoNativeWidth = 0
    private var videoNativeHeight = 0
    private var isPreviewVideo = false
    private var currentRotation = 0
    private var isMirrored = false

    private var mediaPlayer: MediaPlayer? = null
    private var textureSurface: Surface? = null

    companion object {
        private const val TAG = "ResultFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        bindData()
        setupListeners()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExit()
            }
        })
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.resultRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.resultRoot.setPadding(systemBars.left, 0, systemBars.right, 0)
            val minBottom = (16 * resources.displayMetrics.density).toInt()
            binding.resultBottomBar.setPadding(
                binding.resultBottomBar.paddingLeft,
                binding.resultBottomBar.paddingTop,
                binding.resultBottomBar.paddingRight,
                systemBars.bottom.coerceAtLeast(minBottom)
            )
            insets
        }
    }

    private fun bindData() {
        val outputUriStr = arguments?.getString("outputUri") ?: return
        val originalUriStr = arguments?.getString("originalUri")
        val width = arguments?.getInt("width", 0) ?: 0
        val height = arguments?.getInt("height", 0) ?: 0
        val durationMs = arguments?.getLong("durationMs", 0L) ?: 0L
        val backend = arguments?.getString("backend") ?: "vulkan"
        val deviceName = arguments?.getString("deviceName") ?: "GPU"
        val mediaType = arguments?.getString("mediaType") ?: "IMAGE"
        isVideo = mediaType.equals("VIDEO", ignoreCase = true)
        tempFilePath = arguments?.getString("tempFilePath")
        isSaved = arguments?.getBoolean("isSaved", false) ?: false
        isPreviewVideo = arguments?.getBoolean("isPreview", false) ?: false
        videoWidth = width
        videoHeight = height
        videoNativeWidth = width
        videoNativeHeight = height

        val initialUri = Uri.parse(outputUriStr)
        currentVideoUri = initialUri

        binding.tvResultDimensions.text = "$width × $height px"
        binding.tvResultDuration.text = getString(R.string.result_duration_format, durationMs / 1000.0)
        binding.tvResultBackend.text = "$backend ($deviceName)"

        if (isVideo) {
            binding.cardSlider.visibility = View.GONE
            binding.tvSliderHint.visibility = View.GONE
            binding.layoutImageControls.visibility = View.GONE
            binding.cardVideoPlayer.visibility = View.VISIBLE
            binding.cardVideoControls.visibility = View.VISIBLE

            updateSaveStateUi()
            setupVideoPlayback(initialUri)
            binding.videoTextureView.post { updateTextureTransform() }
        } else {
            binding.cardSlider.visibility = View.VISIBLE
            binding.tvSliderHint.visibility = View.VISIBLE
            binding.layoutImageControls.visibility = View.VISIBLE
            binding.cardVideoPlayer.visibility = View.GONE
            binding.cardVideoControls.visibility = View.GONE
            binding.btnSaveVideo.visibility = View.GONE
            binding.btnShare.visibility = View.VISIBLE
            binding.btnOpenGallery.visibility = View.VISIBLE
            binding.btnOpenGallery.isEnabled = true
            binding.tvResultLocation.text = outputUriStr

            val scale = arguments?.getInt("scale", 0) ?: 0
            val effectiveScale = if (scale > 0) scale else {
                val origW = arguments?.getInt("originalWidth", 0) ?: 0
                if (origW > 0 && width > 0) (width / origW) else 2
            }
            val scaleText = when (effectiveScale) {
                3 -> getString(R.string.scale_badge_3x)
                4 -> getString(R.string.scale_badge_4x)
                else -> getString(R.string.scale_badge_2x)
            }
            binding.tvScaleBadge.text = scaleText

            binding.btnToggleZoom.setOnClickListener {
                val isCurrentlyZoomed = binding.sliderView.isZoomed()
                binding.sliderView.toggleZoom(!isCurrentlyZoomed)
                binding.btnToggleZoom.text = if (!isCurrentlyZoomed) getString(R.string.btn_zoom_fit) else getString(R.string.btn_zoom_100)
            }

            // Load bitmaps safely in background for slider (capped at 4096px for full 4K sharpness)
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val afterBmp = decodeSampledBitmap(initialUri, maxDim = 4096)
                val beforeBmp = if (originalUriStr != null) decodeSampledBitmap(Uri.parse(originalUriStr), maxDim = 4096) else null

                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        binding.sliderView.setBitmaps(beforeBmp, afterBmp)
                    }
                }
            }
        }
    }

    private fun decodeSampledBitmap(uri: Uri, maxDim: Int = 4096): android.graphics.Bitmap? {
        return try {
            val cr = context?.contentResolver ?: return null
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
            var sample = 1
            val maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            while (maxSide / sample > maxDim) {
                sample *= 2
            }
            try {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            } catch (oom: OutOfMemoryError) {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample * 2
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }
        } catch (e: Throwable) {
            HdLogger.e(TAG, "Error decoding bitmap from $uri: ${e.message}", e)
            null
        }
    }

    private fun updateSaveStateUi() {
        if (!isVideo || _binding == null) return

        if (isSaved) {
            binding.btnSaveVideo.visibility = View.GONE
            binding.btnShare.visibility = View.VISIBLE
            binding.btnOpenGallery.visibility = View.VISIBLE
            binding.btnOpenGallery.isEnabled = true
            binding.tvResultLocation.text = currentVideoUri?.toString() ?: ""
        } else {
            binding.btnSaveVideo.visibility = View.VISIBLE
            binding.btnSaveVideo.isEnabled = !isSavingInProgress
            binding.btnSaveVideo.text = if (isSavingInProgress) getString(R.string.btn_saving_video) else getString(R.string.btn_save_video)

            binding.btnShare.visibility = View.GONE
            binding.btnOpenGallery.visibility = View.GONE
            binding.tvResultLocation.text = if (isSavingInProgress) getString(R.string.btn_saving_video) else getString(R.string.result_unsaved_draft)
        }
    }

    private fun setupVideoPlayback(uri: Uri) {
        binding.videoTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                textureSurface = Surface(surfaceTexture)
                startMediaPlayer(uri)
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                updateTextureTransform()
            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                releaseMediaPlayer()
                textureSurface?.release()
                textureSurface = null
                return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
            }
        }

        if (binding.videoTextureView.isAvailable) {
            val st = binding.videoTextureView.surfaceTexture
            if (st != null) {
                textureSurface = Surface(st)
                startMediaPlayer(uri)
            }
        }
    }

    private fun startMediaPlayer(uri: Uri) {
        val surface = textureSurface ?: return
        releaseMediaPlayer()

        try {
            mediaPlayer = MediaPlayer().apply {
                setSurface(surface)
                setDataSource(requireContext(), uri)
                isLooping = true
                setOnVideoSizeChangedListener { _, w, h ->
                    if (w > 0 && h > 0) {
                        videoNativeWidth = w
                        videoNativeHeight = h
                        updateTextureTransform()
                    }
                }
                setOnPreparedListener { mp ->
                    mp.start()
                    binding.fabPlayPause.setImageResource(R.drawable.ic_pause)
                    if (videoNativeWidth <= 0 || videoNativeHeight <= 0) {
                        videoNativeWidth = mp.videoWidth
                        videoNativeHeight = mp.videoHeight
                    }
                    updateTextureTransform()
                }
                setOnCompletionListener {
                    binding.fabPlayPause.setImageResource(R.drawable.ic_play)
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            HdLogger.e(TAG, "Gagal memulai MediaPlayer: ${e.message}", e)
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.stop()
        } catch (_: Throwable) {}
        try {
            mediaPlayer?.release()
        } catch (_: Throwable) {}
        mediaPlayer = null
    }

    private fun togglePlayPause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                binding.fabPlayPause.setImageResource(R.drawable.ic_play)
            } else {
                mp.start()
                binding.fabPlayPause.setImageResource(R.drawable.ic_pause)
            }
        }
    }

    private fun updateTextureTransform() {
        if (_binding == null || !isVideo) return

        val viewW = binding.videoTextureView.width.toFloat()
        val viewH = binding.videoTextureView.height.toFloat()
        if (viewW <= 0f || viewH <= 0f) {
            binding.videoTextureView.post { updateTextureTransform() }
            return
        }

        val vW = if (videoNativeWidth > 0) videoNativeWidth.toFloat() else (if (videoWidth > 0) videoWidth.toFloat() else viewW)
        val vH = if (videoNativeHeight > 0) videoNativeHeight.toFloat() else (if (videoHeight > 0) videoHeight.toFloat() else viewH)

        val isSwapped = currentRotation == 90 || currentRotation == 270
        val effectiveW = if (isSwapped) vH else vW
        val effectiveH = if (isSwapped) vW else vH

        val cx = viewW / 2f
        val cy = viewH / 2f

        // Compute aspect-fit scale within TextureView bounds
        val fitScale = minOf(viewW / effectiveW, viewH / effectiveH)
        val targetDisplayW = effectiveW * fitScale
        val targetDisplayH = effectiveH * fitScale

        val sx = if (isSwapped) targetDisplayH / viewW else targetDisplayW / viewW
        val sy = if (isSwapped) targetDisplayW / viewH else targetDisplayH / viewH

        val matrix = Matrix()
        matrix.setScale(sx, sy, cx, cy)

        if (currentRotation != 0) {
            matrix.postRotate(currentRotation.toFloat(), cx, cy)
        }

        if (isMirrored) {
            matrix.postScale(-1f, 1f, cx, cy)
        }

        binding.videoTextureView.setTransform(matrix)
        binding.videoTextureView.invalidate()

        binding.btnRotateVideo.text = getString(R.string.btn_rotate_format, currentRotation)
        val mirrorState = if (isMirrored) getString(R.string.mirror_on) else getString(R.string.mirror_off)
        binding.btnMirrorVideo.text = getString(R.string.btn_mirror_format, mirrorState)
        binding.btnResetTransform.isEnabled = currentRotation != 0 || isMirrored
        binding.tvResultDimensions.text = "${effectiveW.toInt()} × ${effectiveH.toInt()} px"
    }

    private fun setupListeners() {
        // Video orientation adjustment controls
        binding.btnRotateVideo.setOnSingleClickListener {
            currentRotation = (currentRotation + 90) % 360
            updateTextureTransform()
        }

        binding.btnMirrorVideo.setOnSingleClickListener {
            isMirrored = !isMirrored
            updateTextureTransform()
        }

        binding.btnResetTransform.setOnSingleClickListener {
            currentRotation = 0
            isMirrored = false
            updateTextureTransform()
        }

        binding.fabPlayPause.setOnClickListener { togglePlayPause() }
        binding.videoTextureView.setOnClickListener { togglePlayPause() }

        // Save Video Action (Single Primary CTA in bottom bar)
        binding.btnSaveVideo.setOnSingleClickListener { executeSaveVideo() }

        binding.btnShare.setOnSingleClickListener {
            val shareUri = currentVideoUri ?: return@setOnSingleClickListener
            val mimeType = if (isVideo) "video/mp4" else "image/*"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val title = if (isVideo) getString(R.string.share_video_title) else getString(R.string.action_share_image)
            startActivity(Intent.createChooser(shareIntent, title))
        }

        binding.btnOpenGallery.setOnSingleClickListener {
            if (isVideo && !isSaved) {
                Toast.makeText(requireContext(), getString(R.string.toast_save_first), Toast.LENGTH_SHORT).show()
                return@setOnSingleClickListener
            }
            val viewUri = currentVideoUri ?: return@setOnSingleClickListener
            val mimeType = if (isVideo) "video/mp4" else "image/*"
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(viewUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(viewIntent)
            } catch (e: Exception) {
                val msg = if (isVideo) getString(R.string.no_gallery_app) else getString(R.string.toast_no_gallery)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDone.setOnSingleClickListener {
            handleExit()
        }
    }

    private fun executeSaveVideo() {
        if (isSavingInProgress || isSaved) return

        val path = tempFilePath
        if (path == null) {
            Toast.makeText(requireContext(), getString(R.string.toast_draft_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(requireContext(), getString(R.string.toast_video_cache_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        isSavingInProgress = true
        updateSaveStateUi()
        binding.btnRotateVideo.isEnabled = false
        binding.btnMirrorVideo.isEnabled = false
        binding.btnResetTransform.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val inW = if (videoNativeWidth > 0) videoNativeWidth else videoWidth
                val inH = if (videoNativeHeight > 0) videoNativeHeight else videoHeight

                val savedUri = VideoPostProcessor.processAndSave(
                    context = requireContext(),
                    tempFile = file,
                    rotationDegrees = currentRotation,
                    isMirrored = isMirrored,
                    inputWidth = inW,
                    inputHeight = inH,
                    isPreview = isPreviewVideo
                )

                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        isSaved = true
                        isSavingInProgress = false
                        currentVideoUri = savedUri
                        tempFilePath = null
                        updateSaveStateUi()
                        Toast.makeText(requireContext(), getString(R.string.toast_video_save_success), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        isSavingInProgress = false
                        updateSaveStateUi()
                        binding.btnRotateVideo.isEnabled = true
                        binding.btnMirrorVideo.isEnabled = true
                        binding.btnResetTransform.isEnabled = true
                        Toast.makeText(requireContext(), getString(R.string.toast_video_save_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun handleExit() {
        if (isVideo && !isSaved) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_unsaved_video_title))
                .setMessage(getString(R.string.dialog_unsaved_video_desc))
                .setPositiveButton(getString(R.string.action_exit)) { _, _ ->
                    tempFilePath?.let { File(it).delete() }
                    findNavController().safePopBackStack()
                }
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show()
        } else {
            findNavController().safePopBackStack()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _binding?.fabPlayPause?.setImageResource(R.drawable.ic_play)
            }
        } catch (_: Throwable) {}
    }

    override fun onDestroyView() {
        _binding?.sliderView?.setBitmaps(null, null)
        releaseMediaPlayer()
        textureSurface?.release()
        textureSurface = null
        super.onDestroyView()
        _binding = null
    }
}
