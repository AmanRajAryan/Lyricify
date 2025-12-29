package aman.lyricify.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.unit.toIntSize
import kotlinx.coroutines.launch

// ---------------------------------------------------------
// 1. THE SHADER (Now with configurable Highlight)
// ---------------------------------------------------------
private const val GlassyRippleShader = """
uniform shader content;
uniform float2 iResolution;
uniform float rippleData[40];
uniform int rippleCount;
uniform float amplitude;
uniform float frequency;
uniform float decay;
uniform float speed;
uniform float highlightStrength; // NEW: Controls the white fade/transparency

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
    
    // Use the custom highlightStrength instead of hardcoded 0.3
    return highlightStrength * (rippleAmount / amplitude) * exp(-decay * adjustedTime);
}

half4 main(float2 fragCoord) {
    float2 position = fragCoord;
    float2 totalOffset = float2(0.0, 0.0);
    float totalBrightness = 0.0;

    // Unroll loop for up to 5 ripples (Optimization)
    if (rippleCount > 0) {
        float2 origin = float2(rippleData[0], rippleData[1]);
        totalOffset += calculateRippleOffset(position, origin, rippleData[2]);
        totalBrightness += calculateBrightness(position, origin, rippleData[2]);
    }
    if (rippleCount > 1) {
        float2 origin = float2(rippleData[4], rippleData[5]);
        totalOffset += calculateRippleOffset(position, origin, rippleData[6]);
        totalBrightness += calculateBrightness(position, origin, rippleData[6]);
    }
    if (rippleCount > 2) {
        float2 origin = float2(rippleData[8], rippleData[9]);
        totalOffset += calculateRippleOffset(position, origin, rippleData[10]);
        totalBrightness += calculateBrightness(position, origin, rippleData[10]);
    }
    if (rippleCount > 3) {
        float2 origin = float2(rippleData[12], rippleData[13]);
        totalOffset += calculateRippleOffset(position, origin, rippleData[14]);
        totalBrightness += calculateBrightness(position, origin, rippleData[14]);
    }
    if (rippleCount > 4) {
        float2 origin = float2(rippleData[16], rippleData[17]);
        totalOffset += calculateRippleOffset(position, origin, rippleData[18]);
        totalBrightness += calculateBrightness(position, origin, rippleData[18]);
    }
    
    float2 newPosition = position + totalOffset;
    half4 color = content.eval(newPosition);
    
    // Add brightness (The "White Thing")
    color.rgb += totalBrightness * color.a;

    return color;
}
"""

private data class GlassyRippleState(
    val position: Offset,
    val animatable: Animatable<Float, *>
)

// ---------------------------------------------------------
// 2. THE NODE IMPLEMENTATION
// ---------------------------------------------------------
private class GlassyRippleIndicationNode(
    private val interactionSource: InteractionSource,
    private val amplitude: Float,
    private val frequency: Float,
    private val decay: Float,
    private val speed: Float,
    private val highlightStrength: Float, // NEW
    private val enabled: Boolean          // NEW
) : Modifier.Node(), DrawModifierNode {

    override val shouldAutoInvalidate: Boolean = false
    private val activeRipples = mutableListOf<GlassyRippleState>()
    private val maxRipples: Int = 5
    private var contentLayer: GraphicsLayer? = null
    
    private val isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    
    private var runtimeShader: RuntimeShader? = if (isSupported) {
        try { RuntimeShader(GlassyRippleShader) } catch (e: Exception) { null }
    } else { null }

    private var currentSize: androidx.compose.ui.geometry.Size? = null

    private fun calculateDuration(componentSize: androidx.compose.ui.geometry.Size, pressPosition: Offset): Int {
        val corners = listOf(
            Offset(0f, 0f), Offset(componentSize.width, 0f),
            Offset(0f, componentSize.height), Offset(componentSize.width, componentSize.height)
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
        val ripple = GlassyRippleState(pressPosition, animatable)

        if (activeRipples.size >= maxRipples) activeRipples.removeAt(0)
        activeRipples.add(ripple)

        animatable.animateTo(
            targetValue = duration / 1000f,
            animationSpec = tween(durationMillis = duration, easing = LinearEasing)
        ) { invalidateDraw() }

        activeRipples.remove(ripple)
        invalidateDraw()
    }

    override fun onAttach() {
        if (!enabled) return // Skip if disabled

        val graphicsContext = requireGraphicsContext()
        contentLayer = graphicsContext.createGraphicsLayer()

        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        currentSize?.let { size ->
                            launch { animateRipple(interaction.pressPosition, size) }
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
        val shader = runtimeShader

        if (layer == null || shader == null || !isSupported || !enabled) {
            drawContent()
            return
        }

        layer.record(size = size.toIntSize()) { this@draw.drawContent() }

        if (activeRipples.isNotEmpty()) {
            val rippleData = FloatArray(40)
            activeRipples.take(maxRipples).forEachIndexed { index, ripple ->
                val baseIndex = index * 4
                rippleData[baseIndex] = ripple.position.x
                rippleData[baseIndex + 1] = ripple.position.y
                rippleData[baseIndex + 2] = ripple.animatable.value
            }

            shader.setFloatUniform("iResolution", size.width, size.height)
            shader.setIntUniform("rippleCount", activeRipples.size)
            shader.setFloatUniform("amplitude", amplitude)
            shader.setFloatUniform("frequency", frequency)
            shader.setFloatUniform("decay", decay)
            shader.setFloatUniform("speed", speed)
            shader.setFloatUniform("highlightStrength", highlightStrength) // Pass strength
            shader.setFloatUniform("rippleData", rippleData)

            val effect = RenderEffect.createRuntimeShaderEffect(shader, "content")
            layer.renderEffect = effect.asComposeRenderEffect()
        } else {
            layer.renderEffect = null
        }

        drawLayer(layer)
    }
}

// ---------------------------------------------------------
// 3. THE INDICATION CLASS (Configurable)
// ---------------------------------------------------------
data class GlassyRippleIndication(
    val amplitude: Float = 35f,
    val frequency: Float = 12f,
    val decay: Float = 0.8f,
    val speed: Float = 400f,
    val highlightStrength: Float = 0.5f, // Default 0.5 brightness
    val enabled: Boolean = true
) : IndicationNodeFactory {
    
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return GlassyRippleIndicationNode(
            interactionSource, 
            amplitude, 
            frequency, 
            decay, 
            speed, 
            highlightStrength, 
            enabled
        )
    }

    override fun hashCode(): Int = super.hashCode()
    override fun equals(other: Any?) = other === this
}
