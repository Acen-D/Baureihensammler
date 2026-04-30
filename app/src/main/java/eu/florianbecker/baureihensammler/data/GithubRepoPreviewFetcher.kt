package eu.florianbecker.baureihensammler.data

import eu.florianbecker.baureihensammler.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GithubRepoPreview(
    val fullName: String,
    val description: String,
    val stars: Int,
    val forks: Int,
    val openIssues: Int,
)

suspend fun fetchGithubRepoPreview(repoApiUrl: String): GithubRepoPreview? =
    withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(repoApiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty(
                    "User-Agent",
                    "Baureihensammler/1.0 (Android; ${BuildConfig.APPLICATION_ID})"
                )
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(body)
            GithubRepoPreview(
                fullName = j.optString("full_name").ifBlank { "FlorianB-DE/Baureihensammler" },
                description = j.optString("description").ifBlank { "Baureihensammler repository" },
                stars = j.optInt("stargazers_count", 0),
                forks = j.optInt("forks_count", 0),
                openIssues = j.optInt("open_issues_count", 0),
            )
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
