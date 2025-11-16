package ro.snapify.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ro.snapify.ui.theme.AppTheme

data class TutorialStep(
    val title: String,
    val description: String,
    val imageDescription: String? = null // Placeholder for images
)

val tutorialSteps = listOf(
    TutorialStep(
        title = "Welcome to Screenshot Manager!",
        description = "Screenshot Manager is your intelligent screenshot manager for Android. It automatically detects, organizes, and cleans up screenshots from your device's Pictures/Screenshots folder. With customizable timers and manual controls, Screenshot Manager helps keep your gallery clutter-free."
    ),
    TutorialStep(
        title = "Main Activity Overview",
        description = "On the main screen, you'll see your screenshots organized in tabs. The status bar shows monitoring state. Tap screenshots to keep or delete them manually. Grant permissions if prompted to enable automatic detection."
    ),
    TutorialStep(
        title = "Understanding Operation Modes",
        description = "Choose between Manual mode (decide for each screenshot) or Automatic mode (set deletion timers). In Automatic mode, configure how long to wait before deleting screenshots."
    ),
    TutorialStep(
        title = "Settings & Customization",
        description = "Access Settings to change themes, languages, operation modes, and custom folders. The final step will take you there to highlight key options."
    )
)

@Composable
fun TutorialScreen(
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { tutorialSteps.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val step = tutorialSteps[page]
                TutorialStepContent(step = step)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = onSkip) {
                    Text("Skip")
                }

                AnimatedVisibility(
                    visible = pagerState.currentPage < tutorialSteps.size - 1,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                if (pagerState.currentPage < tutorialSteps.size - 1) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        }
                    ) {
                        Text("Next")
                    }
                }

                AnimatedVisibility(
                    visible = pagerState.currentPage == tutorialSteps.size - 1,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Button(onClick = onFinish) {
                        Text("Get Started")
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialStepContent(step: TutorialStep) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = step.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = step.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Placeholder for image
        if (step.imageDescription != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(200.dp)
                    .background(Color.Gray.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = step.imageDescription,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TutorialScreenPreview() {
    AppTheme {
        TutorialScreen(onSkip = {}, onFinish = {})
    }
}
