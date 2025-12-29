package aman.lyricify.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView

class FluidLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var contentSet = false

    // This ensures that when you call setVisibility() in Java, 
    // the FluidLayout wrapper also updates its visibility.
    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        // If we have a child, keep them in sync
        if (childCount > 0) {
            getChildAt(0).visibility = visibility
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!contentSet && childCount > 0) {
            setupRippleWrapper()
        }
    }

    private fun setupRippleWrapper() {
        val child = getChildAt(0) ?: return
        
        // SYNC VISIBILITY: If card is hidden, wrapper should be too
        this.visibility = child.visibility
        
        val originalParams = child.layoutParams
        removeView(child)

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val interactionSource = remember { MutableInteractionSource() }
                val rippleIndication = remember {
                    GlassyRippleIndication(
                        amplitude = 10f,
                        frequency = 5f,
                        decay = 0.8f,
                        speed = 400f,
                        highlightStrength = 0.0f,
                        enabled = true
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = rippleIndication,
                            onClick = {
                                if (child.hasOnClickListeners()) {
                                    child.performClick()
                                }
                            }
                        )
                ) {
                    AndroidView(
                        factory = { child },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }
            }
        }

        addView(composeView, LayoutParams(originalParams.width, originalParams.height))
        contentSet = true
    }
}
