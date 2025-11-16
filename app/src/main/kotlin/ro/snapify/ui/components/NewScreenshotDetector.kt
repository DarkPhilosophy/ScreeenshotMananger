package ro.snapify.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun NewScreenshotDetector(
    newScreenshotFlow: Flow<Unit>,
    onLoadingChange: (Boolean) -> Unit,
    onNewScreenshot: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val loadingJob = remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        newScreenshotFlow.collect {
            loadingJob.value?.cancel()
            loadingJob.value = scope.launch {
                onLoadingChange(true)
                delay(1000) // Show for 1 second
                onLoadingChange(false)
            }
            // Delay scroll to allow animation to start
            scope.launch {
                delay(200)
                onNewScreenshot()
            }
        }
    }
}
