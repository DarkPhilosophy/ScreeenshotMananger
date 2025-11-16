package ro.snapify.ui.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Tutorial overlay that dims the background and highlights specific UI elements
 */
@Composable
fun TutorialOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    highlightedElements: List<TutorialHighlight>,
    currentStep: Int = 0,
    totalSteps: Int = highlightedElements.size,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onFinish: (() -> Unit)? = null
) {
    if (!isVisible) return

    val currentHighlight = highlightedElements.getOrNull(currentStep)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .zIndex(1000f)
    ) {
        // Highlight cutout
        currentHighlight?.let { highlight ->
            HighlightCutout(
                shape = highlight.shape,
                padding = highlight.padding,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        // You would need to pass coordinates from the actual UI elements
                        // This is a simplified version
                    }
            )
        }

        // Tutorial tooltip
        currentHighlight?.let { highlight ->
            TutorialTooltip(
                title = highlight.title,
                description = highlight.description,
                position = highlight.tooltipPosition,
                onNext = if (currentStep < totalSteps - 1) onNext else null,
                onPrevious = if (currentStep > 0) onPrevious else null,
                onFinish = if (currentStep == totalSteps - 1) onFinish else null,
                currentStep = currentStep + 1,
                totalSteps = totalSteps,
                modifier = Modifier.align(highlight.tooltipAlignment)
            )
        }

        // Close button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close tutorial",
                tint = Color.White
            )
        }
    }
}

/**
 * Data class for tutorial highlight information
 */
data class TutorialHighlight(
    val title: String,
    val description: String,
    val shape: HighlightShape = HighlightShape.Rectangle,
    val padding: Dp = 8.dp,
    val tooltipPosition: TooltipPosition = TooltipPosition.Bottom,
    val tooltipAlignment: Alignment = Alignment.BottomCenter
)

enum class HighlightShape {
    Rectangle,
    Circle,
    RoundedRectangle
}

enum class TooltipPosition {
    Top,
    Bottom,
    Left,
    Right,
    Center
}

/**
 * Creates a cutout effect for highlighting UI elements
 */
@Composable
fun HighlightCutout(
    shape: HighlightShape = HighlightShape.Rectangle,
    padding: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    // This is a simplified version. In a real implementation,
    // you'd use Canvas or custom drawing to create actual cutouts
    Box(modifier = modifier)
}

/**
 * Tutorial tooltip that shows instructions
 */
@Composable
fun TutorialTooltip(
    title: String,
    description: String,
    position: TooltipPosition = TooltipPosition.Bottom,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onFinish: (() -> Unit)? = null,
    currentStep: Int = 1,
    totalSteps: Int = 1,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(16.dp)
            .wrapContentWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step indicator
            if (totalSteps > 1) {
                Text(
                    text = "$currentStep / $totalSteps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Description
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Navigation buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onPrevious != null) {
                    OutlinedButton(
                        onClick = onPrevious,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Previous")
                    }
                }

                if (onPrevious == null) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (onNext != null) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Next")
                    }
                } else if (onFinish != null) {
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Finish")
                    }
                }
            }
        }
    }
}

/**
 * Tutorial step content for demonstrating app features
 */
@Composable
fun TutorialStepContent(
    step: TutorialStep,
    modifier: Modifier = Modifier
) {
    when (step) {
        TutorialStep.Welcome -> TutorialWelcomeStep(modifier)
        TutorialStep.StatusBar -> TutorialStatusBarStep(modifier)
        TutorialStep.Tabs -> TutorialTabsStep(modifier)
        TutorialStep.ScreenshotCard -> TutorialScreenshotCardStep(modifier)
        TutorialStep.FABs -> TutorialFABsStep(modifier)
    }
}

enum class TutorialStep {
    Welcome,
    StatusBar,
    Tabs,
    ScreenshotCard,
    FABs
}

@Composable
fun TutorialWelcomeStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Welcome to Screenshot Manager!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = "This tutorial will show you how to use all the features of the app. Let's get started!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TutorialStatusBarStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Simulate the status bar
        Surface(
            color = Color(0xFFFF9800), // Warning orange
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Monitoring Stopped",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Status Bar",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "This bar shows if screenshot monitoring is active. Tap it to start/stop monitoring or grant permissions.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TutorialTabsStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Filter Tabs",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Simulate tab row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TutorialTabItem("All", true)
            TutorialTabItem("Marked", false)
            TutorialTabItem("Kept", false)
            TutorialTabItem("Unmarked", false)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Use these tabs to filter screenshots by their status. Each tab shows screenshots in different states.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TutorialTabItem(text: String, isSelected: Boolean) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun TutorialScreenshotCardStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Simulate a screenshot card
        Surface(
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail placeholder
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📸", style = MaterialTheme.typography.headlineMedium)
                }

                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Screenshot_001.png",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "2.3 MB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Action buttons
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF4CAF50), // Success green
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⭐", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color(0xFFF44336)
                        ), // Error red
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "🗑️",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFF44336)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Screenshot Cards",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Each screenshot appears as a card. Tap the card to view it. Use the star button to keep it permanently, or the delete button to remove it.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TutorialFABsStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Action Buttons",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Simulate FABs
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomEnd
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Back to top FAB
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("⬆️", style = MaterialTheme.typography.headlineSmall)
                    }
                }

                // Settings FAB
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("⚙️", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))

        Text(
            text = "The top button scrolls back to the newest screenshots. The bottom button opens app settings.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
