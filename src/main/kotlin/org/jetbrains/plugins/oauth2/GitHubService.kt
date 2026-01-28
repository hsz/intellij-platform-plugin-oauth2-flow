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

    private val authService by lazy { service<AuthService>() }

    private val token
        get() = checkNotNull(authService.getToken()) { "User is not authenticated" }

    /**
     * Get a GitHub API client instance.
     * Returns null if the user is not authenticated.
     */
    val github
        get() = GitHubBuilder().withOAuthToken(token).build()
}
