package eu.florianbecker.baureihensammler.ui

import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import eu.florianbecker.baureihensammler.data.fetchGithubRepoPreview

@Composable
fun FeedbackScreen(
    privacyModeEnabled: Boolean,
    onOpenMail: () -> Unit,
    onOpenGitHub: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var githubPreviewLoading by remember { mutableStateOf(false) }
    var githubPreview by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(privacyModeEnabled) {
        if (privacyModeEnabled) {
            githubPreview = null
            githubPreviewLoading = false
            return@LaunchedEffect
        }
        if (githubPreview != null) return@LaunchedEffect
        githubPreviewLoading = true
        val preview =
            fetchGithubRepoPreview("https://api.github.com/repos/FlorianB-DE/Baureihensammler")
        githubPreview =
            preview?.let {
                "${it.fullName}\n${it.description}\n⭐ ${it.stars} · Forks ${it.forks} · Open Issues ${it.openIssues}"
            }
        githubPreviewLoading = false
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Feedback",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onBackground,
            fontWeight = FontWeight.Bold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Ich suche gerne Leute, die beim Projekt helfen wollen.")
                Text("Ideen und Anregungen gerne per Mail an:")
                OutlinedButton(onClick = onOpenMail) {
                    Text("baureihensammler@florianbecker.eu")
                }
                Text("Für alle mit Sinn für Züge und bunten Kniesocken:")
                if (privacyModeEnabled) {
                    OutlinedButton(onClick = onOpenGitHub) {
                        Text("GitHub Repo")
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenGitHub),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("GitHub Vorschau", fontWeight = FontWeight.SemiBold)
                            if (githubPreviewLoading) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            } else {
                                Text(
                                    githubPreview ?: "Repository-Daten konnten nicht geladen werden.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Text("Wenn du mich unterstützen möchtest: Buy me a Coffee")
                if (privacyModeEnabled) {
                    OutlinedButton(onClick = onOpenSupport) {
                        Text("buymeacoffee.com/becker.software")
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(70.dp),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = false
                                isVerticalScrollBarEnabled = false
                                isHorizontalScrollBarEnabled = false
                                setBackgroundColor(0x00000000)
                                loadDataWithBaseURL(
                                    "https://www.buymeacoffee.com",
                                    """
                                    <a href="https://www.buymeacoffee.com/becker.software">
                                      <img src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&emoji=%E2%98%95&slug=becker.software&button_colour=FFDD00&font_colour=000000&font_family=Cookie&outline_colour=000000&coffee_colour=ffffff" />
                                    </a>
                                    """.trimIndent(),
                                    "text/html",
                                    "utf-8",
                                    null
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
