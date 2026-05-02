package aman.lyricify

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.padding // Added Import
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toIntSize
import androidx.fragment.app.Fragment
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import kotlinx.coroutines.launch

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
            // Handle parsing error
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
     * Cycle through available fonts.
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

        // 3. Remember state
        val latestPlayingState by rememberUpdatedState(playingState)
        val latestPosition by rememberUpdatedState(currentPosition)
        val latestUpdateTime by rememberUpdatedState(lastUpdateTime)
        val latestFontIndex by rememberUpdatedState(currentFontIndex)

        // 4. Get the current font family based on the cycle index
        val fontFamily = when (latestFontIndex) {
            0 -> FontFamily.Default
            1 -> FontFamily.Serif
            2 -> FontFamily.Monospace
            3 -> FontFamily.SansSerif
            4 -> FontFamily.Cursive
            else -> FontFamily.Default
        }

        // 5. Smooth animation loop
        LaunchedEffect(latestPlayingState) {
            if (latestPlayingState) {
                while (true) {
                    val elapsed = System.currentTimeMillis() - latestUpdateTime
                    animatedPosition = latestPosition + elapsed
                    withFrameMillis {}
                }
            } else {
                animatedPosition = latestPosition
            }
        }

        // 6. Sync position when externally updated
        LaunchedEffect(latestPosition, latestUpdateTime) {
            if (!latestPlayingState) {
                animatedPosition = latestPosition
            }
        }

        // 7. Render with Custom Ripple Indication
        key(latestFontIndex) {
            // Apply the custom Ripple Indication
            CompositionLocalProvider(LocalIndication provides RippleIndication) {
                KaraokeLyricsView(
                    listState = listState,
                    lyrics = lyrics,
                    currentPosition = animatedPosition,
                    onLineClicked = { line ->
                        seekListener?.onSeek(line.start.toLong())
                    },
                    onLinePressed = { line ->
                        // Optional: Handle long press
                    },
                    normalLineTextStyle = TextStyle(
                        fontFamily = fontFamily,
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    ),
                    accompanimentLineTextStyle = TextStyle(
                        fontFamily = fontFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f)
                    ),
                    // Applied padding to modifier since contentPadding parameter is unavailable
                    modifier = Modifier
                        .padding(vertical = 100.dp) 
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        composeView = null
    }
}

// =========================================================================
// RIPPLE ANIMATION & SHADER LOGIC (Standard Android API Version)
// =========================================================================

private const val RippleShaderString = """
uniform shader content;
uniform float2 iResolution;
uniform float rippleData[40];
uniform int rippleCount;
uniform float amplitude;
uniform float frequency;
uniform float decay;
uniform float speed;

float2 calculateRippleOffset(float2 position, float2 origin, float time) {
    float distance = length(position - origin);
    float delay = distance / speed;
    float adjustedTime = max(0.0, time - delay);
    float rippleAmount = amplitude * sin(frequency * adjustedTime) * exp(-decay * adjustedTime);
    return rippleAmount * normalize(position - origin);
}

float calculateBrightness(float2 position, float2 origin, float time) {
    float distance = length(position - origin);
    float delay = distance / speed;
    float adjustedTime = max(0.0, time - delay);
    float rippleAmount = amplitude * sin(frequency * adjustedTime) * exp(-decay * adjustedTime);
    return 0.3 * (rippleAmount / amplitude) * exp(-decay * adjustedTime);
}

half4 main(float2 fragCoord) {
    float2 position = fragCoord;
    float2 totalOffset = float2(0.0, 0.0);
    float totalBrightness = 0.0;

    if (rippleCount > 0) {
        float2 origin0 = float2(rippleData[0], rippleData[1]);
        totalOffset += calculateRippleOffset(position, origin0, rippleData[2]);
        totalBrightness += calculateBrightness(position, origin0, rippleData[2]);
    }
    if (rippleCount > 1) {
        float2 origin1 = float2(rippleData[4], rippleData[5]);
        totalOffset += calculateRippleOffset(position, origin1, rippleData[6]);
        totalBrightness += calculateBrightness(position, origin1, rippleData[6]);
    }
    if (rippleCount > 2) {
        float2 origin2 = float2(rippleData[8], rippleData[9]);
        totalOffset += calculateRippleOffset(position, origin2, rippleData[10]);
        totalBrightness += calculateBrightness(position, origin2, rippleData[10]);
    }
    if (rippleCount > 3) {
        float2 origin3 = float2(rippleData[12], rippleData[13]);
        totalOffset += calculateRippleOffset(position, origin3, rippleData[14]);
        totalBrightness += calculateBrightness(position, origin3, rippleData[14]);
    }
    if (rippleCount > 4) {
        float2 origin4 = float2(rippleData[16], rippleData[17]);
        totalOffset += calculateRippleOffset(position, origin4, rippleData[18]);
        totalBrightness += calculateBrightness(position, origin4, rippleData[18]);
    }
    if (rippleCount > 5) {
        float2 origin5 = float2(rippleData[20], rippleData[21]);
        totalOffset += calculateRippleOffset(position, origin5, rippleData[22]);
        totalBrightness += calculateBrightness(position, origin5, rippleData[22]);
    }
    if (rippleCount > 6) {
        float2 origin6 = float2(rippleData[24], rippleData[25]);
        totalOffset += calculateRippleOffset(position, origin6, rippleData[26]);
        totalBrightness += calculateBrightness(position, origin6, rippleData[26]);
    }
    if (rippleCount > 7) {
        float2 origin7 = float2(rippleData[28], rippleData[29]);
        totalOffset += calculateRippleOffset(position, origin7, rippleData[30]);
        totalBrightness += calculateBrightness(position, origin7, rippleData[30]);
    }
    if (rippleCount > 8) {
        float2 origin8 = float2(rippleData[32], rippleData[33]);
        totalOffset += calculateRippleOffset(position, origin8, rippleData[34]);
        totalBrightness += calculateBrightness(position, origin8, rippleData[34]);
    }
    if (rippleCount > 9) {
        float2 origin9 = float2(rippleData[36], rippleData[37]);
        totalOffset += calculateRippleOffset(position, origin9, rippleData[38]);
        totalBrightness += calculateBrightness(position, origin9, rippleData[38]);
    }

    float2 newPosition = position + totalOffset;
    half4 color = content.eval(newPosition);
    color.rgb += totalBrightness * color.a;

    return color;
}
"""

private data class RippleState(
    val position: Offset,
    val animatable: Animatable<Float, *>
)

private class RippleIndicationNode(
    private val interactionSource: InteractionSource
) : Modifier.Node(), DrawModifierNode {

    override val shouldAutoInvalidate: Boolean = false

    private val activeRipples = mutableListOf<RippleState>()

    private val amplitude: Float = 10f
    private val frequency: Float = 8f
    private val decay: Float = 1.5f
    private val speed: Float = 800f
    private val maxRipples: Int = 10

    private var contentLayer: GraphicsLayer? = null
    
    // We store the shader as 'Any' to avoid class verification errors on older Android versions,
    // but in practice this is a android.graphics.RuntimeShader
    private var runtimeShader: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            RuntimeShader(RippleShaderString)
        } catch (e: Exception) {
            null
        }
    } else {
        null
    }

    private var currentSize: androidx.compose.ui.geometry.Size? = null

    private fun calculateDuration(componentSize: androidx.compose.ui.geometry.Size, pressPosition: Offset): Int {
        val corners = listOf(
            Offset(0f, 0f),
            Offset(componentSize.width, 0f),
            Offset(0f, componentSize.height),
            Offset(componentSize.width, componentSize.height)
        )

        val maxDistance = corners.maxOf { corner ->
            kotlin.math.sqrt(
                (corner.x - pressPosition.x) * (corner.x - pressPosition.x) +
                        (corner.y - pressPosition.y) * (corner.y - pressPosition.y)
            )
        }

        val propagationTime = (maxDistance / speed) * 1000
        val decayTime = (3 / decay) * 1000
        return (propagationTime + decayTime).toInt().coerceAtLeast(800)
    }

    private suspend fun animateRipple(pressPosition: Offset, componentSize: androidx.compose.ui.geometry.Size) {
        val duration = calculateDuration(componentSize, pressPosition)
        val animatable = Animatable(0f)
        val ripple = RippleState(pressPosition, animatable)

        if (activeRipples.size >= maxRipples) {
            activeRipples.removeAt(0)
        }
        activeRipples.add(ripple)

        animatable.animateTo(
            targetValue = duration / 1000f,
            animationSpec = tween(durationMillis = duration, easing = LinearEasing)
        ) {
            invalidateDraw()
        }

        activeRipples.remove(ripple)
        invalidateDraw()
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        contentLayer = graphicsContext.createGraphicsLayer()

        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        currentSize?.let { size ->
                            launch {
                                animateRipple(interaction.pressPosition, size)
                            }
                        }
                    }
                    is PressInteraction.Release -> {}
                    is PressInteraction.Cancel -> {}
                }
            }
        }
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        contentLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            contentLayer = null
        }
    }

    override fun ContentDrawScope.draw() {
        currentSize = size

        val layer = contentLayer
        
        // Check for Android 13+ (Tiramisu) support
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || layer == null || runtimeShader == null) {
            drawContent()
            return
        }

        layer.record(size = size.toIntSize()) {
            this@draw.drawContent()
        }

        if (activeRipples.isNotEmpty()) {
            // Cast safely because we checked SDK version above
            val shader = runtimeShader as RuntimeShader
            
            val rippleData = FloatArray(40)
            activeRipples.take(10).forEachIndexed { index, ripple ->
                val baseIndex = index * 4
                rippleData[baseIndex] = ripple.position.x
                rippleData[baseIndex + 1] = ripple.position.y
                rippleData[baseIndex + 2] = ripple.animatable.value
                // 4th value is padding/unused in shader logic
            }

            try {
                shader.setFloatUniform("iResolution", size.width, size.height)
                shader.setIntUniform("rippleCount", activeRipples.size.coerceAtMost(10))
                shader.setFloatUniform("amplitude", amplitude)
                shader.setFloatUniform("frequency", frequency)
                shader.setFloatUniform("decay", decay)
                shader.setFloatUniform("speed", speed)
                shader.setFloatUniform("rippleData", rippleData)

                val renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
                layer.renderEffect = renderEffect.asComposeRenderEffect()
            } catch (e: Exception) {
                // Fallback if shader fails
                layer.renderEffect = null
            }
        } else {
            layer.renderEffect = null
        }

        drawLayer(layer)
    }
}

object RippleIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return RippleIndicationNode(interactionSource)
    }

    override fun hashCode(): Int = -1
    override fun equals(other: Any?) = other === this
}
