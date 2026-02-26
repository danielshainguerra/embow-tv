package com.embow.tv.ui.player

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.embow.tv.BuildConfig
import com.embow.tv.data.models.Video
import com.embow.tv.data.network.ApiClient
import com.embow.tv.data.repository.EmbowRepository
import com.embow.tv.data.storage.SecureStorage
import com.embow.tv.databinding.ActivityMainBinding
import com.embow.tv.service.HeartbeatService
import com.embow.tv.ui.pairing.PairingActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: EmbowRepository
    private var player: ExoPlayer? = null
    private var currentVideos: List<Video> = emptyList()
    private var currentVideoIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val secureStorage = SecureStorage(this)
        val apiClient = ApiClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY
        )
        repository = EmbowRepository(apiClient, secureStorage)

        if (!repository.isDeviceActivated()) {
            navigateToPairing()
            return
        }

        validateTokenAndStart()
    }

    private fun validateTokenAndStart() {
        lifecycleScope.launch {
            val result = repository.validateDeviceToken()
            result.fold(
                onSuccess = { isValid ->
                    if (isValid) {
                        initializePlayer()
                        loadPlaylist()
                        startHeartbeatService()
                    } else {
                        navigateToPairing()
                    }
                },
                onFailure = {
                    Toast.makeText(
                        this@MainActivity,
                        "Token validation failed, returning to pairing",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToPairing()
                }
            )
        }
    }

    private fun navigateToPairing() {
        val intent = Intent(this, PairingActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            binding.playerView.player = exoPlayer

            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            playNextVideo()
                        }
                    }
                }
            })

            exoPlayer.prepare()
        }
    }

    private fun loadPlaylist() {
        lifecycleScope.launch {
            val result = repository.getPlaylist()
            result.fold(
                onSuccess = { playlist ->
                    currentVideos = playlist.videos
                    if (currentVideos.isNotEmpty()) {
                        playVideo(0)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "No videos in playlist",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onFailure = { exception ->
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to load playlist: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    private fun playVideo(index: Int) {
        if (index < 0 || index >= currentVideos.size) return

        currentVideoIndex = index
        val video = currentVideos[index]

        val mediaItem = MediaItem.fromUri(video.url)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()

        binding.tvVideoTitle.text = video.title
    }

    private fun playNextVideo() {
        val nextIndex = (currentVideoIndex + 1) % currentVideos.size
        playVideo(nextIndex)
    }

    private fun playPreviousVideo() {
        val prevIndex = if (currentVideoIndex > 0) currentVideoIndex - 1 else currentVideos.size - 1
        playVideo(prevIndex)
    }

    fun pausePlayback() {
        player?.pause()
    }

    fun resumePlayback() {
        player?.play()
    }

    fun refreshPlaylist() {
        loadPlaylist()
    }

    fun getCurrentVideoId(): String? {
        return if (currentVideoIndex < currentVideos.size) {
            currentVideos[currentVideoIndex].id
        } else null
    }

    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0L
    }

    fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }

    private fun startHeartbeatService() {
        val intent = Intent(this, HeartbeatService::class.java)
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }
}
