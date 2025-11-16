package ro.snapify.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LoadingBar(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading_bar_transition")

    val position by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loading_bar_position"
    )

    // We still use BoxWithConstraints to make it responsive.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .padding(horizontal = 16.dp)
    ) {
        val barWidth = 60.dp

        // Calculate the maximum offset in Dp.
        // The bar can move from the start until its left edge reaches
        // the point where its right edge touches the container's end.
        val maxOffset = maxWidth - barWidth

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(barWidth)
                .offset(x = maxOffset * position) // Use the offset modifier here!
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (position > 0.5f) Color.Red else Color.Blue
                )
        )
    }
}
