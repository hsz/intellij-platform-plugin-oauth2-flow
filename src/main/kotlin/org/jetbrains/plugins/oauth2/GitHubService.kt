package org.jetbrains.plugins.oauth2

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.kohsuke.github.GitHubBuilder

/**
 * Service providing access to GitHub API client.
 * Automatically uses the OAuth token from AuthService.
 */
@Service(Service.Level.APP)
class GitHubService {

    private val authService = service<AuthService>()

    /**
     * Get GitHub API client instance.
     * Returns null if user is not authenticated.
     */
    val github
        get() = runCatching {
            val token = authService.getToken()
            GitHubBuilder().withOAuthToken(token).build()
        }
            .onFailure { throw IllegalStateException("GitHub API not available", it) }
            .getOrThrow()

    /**
     * Check if GitHub API is available (user is authenticated).
     */
    fun isAvailable(): Boolean = authService.isLoggedIn()

    companion object {
        fun getInstance(): GitHubService = service()
    }
}
