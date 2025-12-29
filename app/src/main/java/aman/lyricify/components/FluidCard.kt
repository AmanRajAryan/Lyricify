package aman.lyricify.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FluidCard(
    title: String,
    artist: String,
    drawableId: Int,
    modifier: Modifier = Modifier
) {
    // 1. Create an InteractionSource to track touches
    val interactionSource = remember { MutableInteractionSource() }

    // 2. Configure the Ripple (Settings based on your feedback)
    val rippleIndication = remember {
        GlassyRippleIndication(
            amplitude = 10f,          // Strong wobble strength
            frequency = 6f,          // Frequency of rings
            decay = 0.8f,             // LOW number = Animation lasts longer (Slower fade)
            speed = 400f,             // LOW number = Wave travels slower
            highlightStrength = 0.0f, // 0.0f = Removes the white highlight completely
            enabled = true
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(8.dp)
            // 3. Apply the Ripple via clickable
            .clickable(
                interactionSource = interactionSource,
                indication = rippleIndication, 
                onClick = { 
                    // Add your click action here (e.g., open player)
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(70.dp)
            ) {
                Image(
                    painter = painterResource(id = drawableId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            
            // Text Details
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = artist,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------
// JAVA BRIDGE
// ---------------------------------------------------------
object FluidCardFactory {
    @JvmStatic
    fun setContent(view: ComposeView, title: String, artist: String, drawableId: Int) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            FluidCard(title, artist, drawableId)
        }
    }
}
