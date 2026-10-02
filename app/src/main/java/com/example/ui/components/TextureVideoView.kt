package com.example.ui.components

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import java.io.File
import kotlin.math.abs

class TextureVideoView(context: Context) : FrameLayout(context), TextureView.SurfaceTextureListener {
    private val TAG = "TextureVideoView"
    private val textureView = TextureView(context)
    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var currentPath: String? = null
    private var isPrepared = false
    private var pendingPlay = false
    private var pendingSeekMs: Int? = null
    private var currentVolume = 1.0f

    init {
        textureView.surfaceTextureListener = this
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setVideoPath(path: String) {
        if (currentPath == path && mediaPlayer != null) return
        currentPath = path
        isPrepared = false
        releasePlayer()
        if (surface != null && surface!!.isValid) {
            initMediaPlayer()
        }
    }

    private fun initMediaPlayer() {
        val path = currentPath ?: return
        val surf = surface ?: return
        if (!surf.isValid) return
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return

        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                setSurface(surf)
                isLooping = true
                setOnPreparedListener { player ->
                    isPrepared = true
                    player.setVolume(currentVolume, currentVolume)
                    val seekToTarget = pendingSeekMs ?: 1
                    player.seekTo(seekToTarget)
                    if (pendingPlay) {
                        player.start()
                    }
                }
                setOnSeekCompleteListener {
                    // Frame rendered on surface
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error: what=$what extra=$extra")
                    true // Handled
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPlayer for $path", e)
        }
    }

    fun updatePlayback(isPlaying: Boolean, timeMs: Long, volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        pendingPlay = isPlaying

        val mp = mediaPlayer
        if (mp != null && isPrepared) {
            try {
                mp.setVolume(currentVolume, currentVolume)
                if (isPlaying) {
                    if (!mp.isPlaying) {
                        mp.seekTo(timeMs.toInt())
                        mp.start()
                    }
                } else {
                    if (mp.isPlaying) {
                        mp.pause()
                    }
                    val diff = abs(mp.currentPosition - timeMs)
                    if (diff > 800) {
                        mp.seekTo(timeMs.toInt())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Playback state update failed: ${e.message}")
            }
        } else {
            pendingSeekMs = timeMs.toInt()
        }
    }

    fun seekTo(timeMs: Long) {
        pendingSeekMs = timeMs.toInt()
        if (isPrepared) {
            try {
                mediaPlayer?.seekTo(timeMs.toInt())
            } catch (_: Exception) {}
        }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(surfaceTexture)
        if (mediaPlayer == null && currentPath != null) {
            initMediaPlayer()
        } else {
            try {
                mediaPlayer?.setSurface(surface)
            } catch (e: Exception) {
                Log.w(TAG, "Error setting surface: ${e.message}")
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        try {
            mediaPlayer?.setSurface(null)
        } catch (_: Exception) {}
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isPrepared = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releasePlayer()
        surface?.release()
        surface = null
    }
}
