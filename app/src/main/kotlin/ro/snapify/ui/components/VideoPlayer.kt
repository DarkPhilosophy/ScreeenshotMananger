package ro.snapify.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import ro.snapify.data.entity.MediaItem
import androidx.media3.common.MediaItem as ExoMediaItem

data class VideoPlayerState(
    var isPlaying: Boolean = true,
    var position: Float = 0f,
    var duration: Long = 0L,
    var exoPlayer: ExoPlayer? = null
)

@Composable
fun VideoPlayer(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    onStateChanged: (VideoPlayerState) -> Unit = {},
    onEndReached: (ExoPlayer) -> Unit = {}
): VideoPlayerState {
    val context = LocalContext.current
    var videoState by remember { mutableStateOf(VideoPlayerState()) }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                val exoPlayer = ExoPlayer.Builder(ctx).build()

                // Set URI
                val uri = Uri.parse("file://${mediaItem.filePath}")
                val exoMediaItem = ExoMediaItem.fromUri(uri)
                exoPlayer.setMediaItem(exoMediaItem)

                // Add listener for end reached
                exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                            onEndReached(exoPlayer)
                        }
                    }
                })

                // Prepare and play
                exoPlayer.prepare()
                exoPlayer.play()

                // Set resize mode to fit aspect ratio
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT

                // Disable built-in controls
                useController = false

                player = exoPlayer

                videoState = videoState.copy(exoPlayer = exoPlayer)
            }
        },
        update = { view ->
            // Update if needed
        },
        onRelease = { view ->
            // Clean up ExoPlayer
            (view as? PlayerView)?.player?.release()
        },
        modifier = modifier
    )

    // Poll position and duration
    LaunchedEffect(videoState.exoPlayer) {
        videoState.exoPlayer?.let { player ->
            while (true) {
                delay(1000)
                videoState = videoState.copy(
                    position = player.currentPosition.toFloat() / player.duration.toFloat(),
                    duration = player.duration
                )
                onStateChanged(videoState)
            }
        }
    }



    return videoState
}

@Composable
fun MiniVideoPlayer(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
): VideoPlayerState {
    val context = LocalContext.current
    var videoState by remember { mutableStateOf(VideoPlayerState()) }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                val exoPlayer = ExoPlayer.Builder(ctx).build()

                // Set URI
                val uri = Uri.parse("file://${mediaItem.filePath}")
                val exoMediaItem = ExoMediaItem.fromUri(uri)
                exoPlayer.setMediaItem(exoMediaItem)

                // Prepare and play (muted)
                exoPlayer.prepare()
                exoPlayer.play()
                exoPlayer.volume = 0f // Mute

                // Set resize mode to fit aspect ratio
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT

                // Disable controls
                useController = false

                player = exoPlayer

                videoState = videoState.copy(exoPlayer = exoPlayer)
            }
        },
        update = { /* No updates needed */ },
        onRelease = { view ->
            (view as? PlayerView)?.player?.release()
        },
        modifier = modifier
    )

    // Poll position (no duration needed for mini)
    LaunchedEffect(videoState.exoPlayer) {
        videoState.exoPlayer?.let { player ->
            while (true) {
                delay(1000)
                videoState = videoState.copy(
                    position = player.currentPosition.toFloat() / player.duration.toFloat()
                )
            }
        }
    }

    return videoState
}
