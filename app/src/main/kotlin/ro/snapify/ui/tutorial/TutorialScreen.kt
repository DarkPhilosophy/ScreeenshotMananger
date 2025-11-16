package ro.snapify.ui.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Main tutorial screen that guides users through app features
 */
@Composable
fun TutorialScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var showOverlayTutorial by remember { mutableStateOf(false) }

    val tutorialSteps = listOf(
        TutorialStep.Welcome,
        TutorialStep.StatusBar,
        TutorialStep.Tabs,
        TutorialStep.ScreenshotCard,
        TutorialStep.FABs
    )

    val highlights = listOf(
        TutorialHighlight(
            title = "Welcome!",
            description = "Let's learn how to use Screenshot Manager. This tutorial will show you all the features.",
            tooltipAlignment = Alignment.Center
        ),
        TutorialHighlight(
            title = "Status Bar",
            description = "This colored bar shows if screenshot monitoring is active. Green means active, orange means stopped.",
            tooltipAlignment = Alignment.TopCenter
        ),
        TutorialHighlight(
            title = "Filter Tabs",
            description = "Use these tabs to filter screenshots: All, Marked for deletion, Kept permanently, or Unmarked.",
            tooltipAlignment = Alignment.TopCenter
        ),
        TutorialHighlight(
            title = "Screenshot Cards",
            description = "Each screenshot appears as a card with thumbnail, name, and size. Tap to view, use buttons to keep or delete.",
            tooltipAlignment = Alignment.Center
        ),
        TutorialHighlight(
            title = "Action Buttons",
            description = "The top button scrolls to newest screenshots. The bottom button opens settings.",
            tooltipAlignment = Alignment.BottomCenter
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main tutorial content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "How to Use Screenshot Manager",
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    )
                }

                tutorialSteps.forEachIndexed { index, step ->
                    item {
                        TutorialStepContent(
                            step = step,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { showOverlayTutorial = true },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Text("Start Interactive Tutorial")
                        }

                        OutlinedButton(
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Text("Skip Tutorial")
                        }
                    }
                }
            }

            // Interactive overlay tutorial
            TutorialOverlay(
                isVisible = showOverlayTutorial,
                onDismiss = { showOverlayTutorial = false },
                highlightedElements = highlights,
                currentStep = currentStep,
                totalSteps = highlights.size,
                onNext = {
                    if (currentStep < highlights.size - 1) {
                        currentStep++
                    }
                },
                onPrevious = {
                    if (currentStep > 0) {
                        currentStep--
                    }
                },
                onFinish = {
                    showOverlayTutorial = false
                    onComplete()
                }
            )
        }
    }
}

/**
 * Tutorial preview for development
 */
@Composable
fun TutorialScreenPreview() {
    MaterialTheme {
        TutorialScreen(
            onComplete = {},
            onSkip = {}
        )
    }
}
