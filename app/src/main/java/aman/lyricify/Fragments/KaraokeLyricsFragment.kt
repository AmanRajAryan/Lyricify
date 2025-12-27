package aman.lyricify

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView

/**
 * Fragment wrapper for the Karaoke Lyrics library using Jetpack Compose. This provides a third
 * lyrics display option alongside Native and Web engines.
 */
class KaraokeLyricsFragment : Fragment() {

  private var composeView: ComposeView? = null
  private var syncedLyrics: SyncedLyrics? = null

  // Playback state tracking
  private var currentPosition by mutableLongStateOf(0L)
  private var playingState by mutableStateOf(false)
  private var lastUpdateTime by mutableLongStateOf(0L)
  private var currentFontIndex by mutableIntStateOf(0)

  // Callback interface for seeking
  fun interface SeekListener {
    fun onSeek(timeMs: Long)
  }

  private var seekListener: SeekListener? = null

  override fun onCreateView(
          inflater: LayoutInflater,
          container: ViewGroup?,
          savedInstanceState: Bundle?
  ): View {
    composeView = ComposeView(requireContext())
    return composeView!!
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    // Content will be set when lyrics are loaded
    syncedLyrics?.let { setupComposeContent() }
  }

  /** Set the seek listener for handling line taps */
  fun setSeekListener(listener: SeekListener) {
    this.seekListener = listener
  }

  /**
   * Load and parse lyrics from raw text (LRC, TTML, etc.)
   * @param rawLyrics The raw lyrics text to parse
   */
  fun setLyrics(rawLyrics: String?) {
    if (rawLyrics.isNullOrEmpty()) return

    try {
      // Parse the lyrics using AutoParser
      val autoParser = AutoParser.Builder().build()
      syncedLyrics = autoParser.parse(rawLyrics)

      // If view is already created, update the compose content
      composeView?.let { setupComposeContent() }
    } catch (e: Exception) {
      e.printStackTrace()
      // Handle parsing error - could show error UI or fallback
    }
  }

  /**
   * Update the current playback position
   * @param timeMs Current time in milliseconds
   */
  fun updateTime(timeMs: Long) {
    currentPosition = timeMs
    lastUpdateTime = System.currentTimeMillis()
  }

  /**
   * Update the playing state
   * @param playing Whether the music is currently playing
   */
  fun setPlaying(playing: Boolean) {
    playingState = playing
    lastUpdateTime = System.currentTimeMillis()
  }

  /**
   * Cycle through available fonts. Updated to support 5 system fonts.
   * @return The name of the newly selected font
   */
  fun cycleFont(): String {
    currentFontIndex = (currentFontIndex + 1) % 5
    return when (currentFontIndex) {
      0 -> "Default"
      1 -> "Serif"
      2 -> "Monospace"
      3 -> "SansSerif"
      4 -> "Cursive"
      else -> "Default"
    }
  }

  /** Setup the Jetpack Compose content with the lyrics view */
  private fun setupComposeContent() {
    val lyrics = syncedLyrics ?: return

    composeView?.setContent { KaraokeLyricsContent(lyrics = lyrics) }
  }

  /** The actual Compose function that renders the lyrics */
  @Composable
  private fun KaraokeLyricsContent(lyrics: SyncedLyrics) {
    // 1. State for scrolling
    val listState = rememberLazyListState()

    // 2. Animated position for smooth playback
    var animatedPosition by remember { mutableLongStateOf(0L) }

    // 3. Remember the latest playback state to avoid recreating LaunchedEffect
    val latestPlayingState by rememberUpdatedState(playingState)
    val latestPosition by rememberUpdatedState(currentPosition)
    val latestUpdateTime by rememberUpdatedState(lastUpdateTime)
    val latestFontIndex by rememberUpdatedState(currentFontIndex)

    // 4. Get the current font family based on the cycle index
    val fontFamily =
            when (latestFontIndex) {
              0 -> FontFamily.Default
              1 -> FontFamily.Serif
              2 -> FontFamily.Monospace
              3 -> FontFamily.SansSerif
              4 -> FontFamily.Cursive
              else -> FontFamily.Default
            }

    // 5. Smooth animation loop - matches library sample exactly
    LaunchedEffect(latestPlayingState) {
      if (latestPlayingState) {
        // Playing: animate smoothly using frame updates
        while (true) {
          val elapsed = System.currentTimeMillis() - latestUpdateTime
          animatedPosition = latestPosition + elapsed

          // Wait for next frame (equivalent to awaitFrame())
          withFrameMillis {}
        }
      } else {
        // Paused: just use the current position
        animatedPosition = latestPosition
      }
    }

    // 6. Sync position when externally updated (seek operations)
    LaunchedEffect(latestPosition, latestUpdateTime) {
      if (!latestPlayingState) {
        animatedPosition = latestPosition
      }
    }

    // 7. The Lyrics Component with custom text styles
    // We use `key(latestFontIndex)` to force the Compose runtime to destroy
    // and recreate the KaraokeLyricsView whenever the font index changes.
    // This bypasses the library's internal caching bug.
    key(latestFontIndex) {
      KaraokeLyricsView(
              listState = listState,
              lyrics = lyrics,
              currentPosition = animatedPosition,
              onLineClicked = { line ->
                // Handle seek operation
                seekListener?.onSeek(line.start.toLong())
              },
              onLinePressed = { line ->
                // Optional: Handle long press
              },

              // Apply the font family to the main lyrics
              normalLineTextStyle =
                      TextStyle(
                              fontFamily = fontFamily,
                              fontSize = 35.sp,
                              fontWeight = FontWeight.Black,
                              color = Color.White
                      ),

              // Apply the SAME font family to accompaniment (backing vocals)
              // ensuring the style remains consistent across all line types
              accompanimentLineTextStyle =
                      TextStyle(
                              fontFamily = fontFamily,
                              fontSize = 20.sp,
                              fontWeight = FontWeight.Bold,
                              color = Color.White.copy(alpha = 0.6f)
                      ),
              modifier =
                      Modifier.graphicsLayer {
                        // These settings help with performance and rendering
                        compositingStrategy = CompositingStrategy.Offscreen
                      }
      )
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    composeView = null
  }
}
