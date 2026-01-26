package org.jetbrains.plugins.oauth2

import com.google.gson.Gson
import com.intellij.collaboration.auth.services.PkceUtils
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.BrowserUtil
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.ide.BuiltInServerManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*

fun interface AuthListener {
    fun authChanged()
}

val AUTH_TOPIC = Topic.create("Auth changes", AuthListener::class.java)

private val gson = Gson()

@Service(Service.Level.APP)
@State(name = "AuthSettings", storages = [Storage("authSettings.xml")])
class AuthService : PersistentStateComponent<AuthService.State>, Disposable {

    data class State(var username: String? = null)

    companion object {
        private const val OAUTH_CLIENT_ID = "Iv23ctuaqovvSqutt2KT"
        private const val OAUTH_CLIENT_SECRET = "09938f222c92793fc691defc64e03e2643011ecc"
    }

    @Volatile
    private var myState = State()

    @Volatile
    private var cachedToken: String? = null

    private val httpClient = HttpClient.newHttpClient()
    private val credentialAttributes = CredentialAttributes(generateServiceName("MyPluginAuth", "OAuthToken"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val redirectUri get() = "http://localhost:${BuiltInServerManager.getInstance().port}/api/myplugin/callback"

    init {
        scope.launch {
            cachedToken = PasswordSafe.instance.getPassword(credentialAttributes)?.also { notifyAuthChanged() }
        }
    }

    override fun getState() = myState
    override fun loadState(state: State) { myState = state }

    fun getToken() = when {
        ApplicationManager.getApplication().isDispatchThread -> cachedToken
        else -> PasswordSafe.instance.getPassword(credentialAttributes)
    }

    private fun saveToken(token: String?) {
        cachedToken = token
        PasswordSafe.instance.setPassword(credentialAttributes, token)
    }

    private fun notifyAuthChanged() =
        ApplicationManager.getApplication().messageBus.syncPublisher(AUTH_TOPIC).authChanged()

    fun logout() = scope.launch {
        saveToken(null)
        myState = State()
        notifyAuthChanged()
    }

    fun isLoggedIn() = cachedToken != null

    private var currentCodeVerifier: String? = null

    /**
     * Step 1: Start OAuth authorization flow with PKCE.
     */
    fun startLogin() {
        currentCodeVerifier = PkceUtils.generateCodeVerifier() + PkceUtils.generateCodeVerifier()

        val challenge = PkceUtils.generateShaCodeChallenge(
            requireNotNull(currentCodeVerifier),
            Base64.getUrlEncoder().withoutPadding()
        )
        val authUrl = buildAuthorizationUrl(redirectUri, challenge)
        BrowserUtil.browse(authUrl)
    }

    /**
     * Build GitHub OAuth authorization URL with PKCE.
     */
    private fun buildAuthorizationUrl(redirectUri: String, codeChallenge: String): String {
        return "https://github.com/login/oauth/authorize" +
                "?client_id=$OAUTH_CLIENT_ID" +
                "&scope=read:user%20user:email" +
                "&redirect_uri=$redirectUri" +
                "&code_challenge=$codeChallenge" +
                "&code_challenge_method=S256"
    }

    /**
     * Step 2: Handle OAuth callback with authorization code.
     */
    fun handleCallback(code: String) {
        val verifier = currentCodeVerifier ?: return
        currentCodeVerifier = null

        scope.launch {
            runCatching {
                exchangeCodeForToken(code, verifier)
            }.onSuccess { token ->
                saveToken(token)
                fetchUserProfile()
                notifyAuthChanged()
            }.onFailure { e ->
                println("OAuth error: ${e.message}")
            }
        }
    }

    /**
     * Exchange authorization code for access token.
     */
    private fun exchangeCodeForToken(code: String, codeVerifier: String): String {
        val body = "client_id=$OAUTH_CLIENT_ID" +
                "&client_secret=$OAUTH_CLIENT_SECRET" +
                "&code=$code" +
                "&redirect_uri=$redirectUri" +
                "&code_verifier=$codeVerifier"

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://github.com/login/oauth/access_token"))
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return gson.fromJson(response.body(), Map::class.java)["access_token"] as? String
            ?: throw IllegalStateException("Failed to exchange code for token")
    }

    /**
     * Step 3: Fetch user profile from GitHub API.
     */
    private fun fetchUserProfile() {
        runCatching {
            val github = service<GitHubService>().github
            val user = github.myself
            myState = State(user.login)
        }.onFailure { e ->
            println("Failed to fetch user profile: ${e.message}")
        }
    }

    override fun dispose() {
        scope.cancel()
    }
}
