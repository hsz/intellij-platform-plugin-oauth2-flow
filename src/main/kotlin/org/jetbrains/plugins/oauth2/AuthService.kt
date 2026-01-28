package org.jetbrains.plugins.oauth2

import com.google.gson.Gson
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
import com.intellij.util.io.DigestUtil
import com.intellij.util.messages.Topic
import kotlinx.coroutines.*
import org.jetbrains.ide.BuiltInServerManager
import org.kohsuke.github.GitHubBuilder
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

    companion object {
        private const val OAUTH_CLIENT_ID = "Iv23ctuaqovvSqutt2KT"
        private const val OAUTH_CLIENT_SECRET = "09938f222c92793fc691defc64e03e2643011ecc"
    }

    data class State(var username: String? = null)

    @Volatile
    private var myState = State()

    @Volatile
    private var cachedToken: String? = null

    private val httpClient = HttpClient.newHttpClient()
    private val credentialAttributes = CredentialAttributes(generateServiceName("MyPluginAuth", "OAuthToken"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val redirectUri get() = "http://localhost:${BuiltInServerManager.getInstance().port}/api/${AuthRestService.SERVICE_NAME}"

    init {
        scope.launch {
            cachedToken = PasswordSafe.instance.getPassword(credentialAttributes)?.also { notifyAuthChanged() }
        }
    }

    override fun getState() = myState
    override fun loadState(state: State) {
        myState = state
    }

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

    private lateinit var codeVerifier: String

    /**
     * Step 1: Start OAuth authorization flow with PKCE.
     */
    fun startLogin() {
        codeVerifier = DigestUtil.digestToHash(DigestUtil.sha256())
        val challenge = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(DigestUtil.sha256().digest(codeVerifier.toByteArray()))
        BrowserUtil.browse(buildAuthorizationUrl(challenge))
    }

    /**
     * Build GitHub OAuth authorization URL with PKCE.
     */
    private fun buildAuthorizationUrl(codeChallenge: String) =
        "https://github.com/login/oauth/authorize" +
                "?client_id=$OAUTH_CLIENT_ID" +
                "&scope=read:user%20user:email" +
                "&redirect_uri=$redirectUri" +
                "&code_challenge=$codeChallenge" +
                "&code_challenge_method=S256"

    /**
     * Step 2: Handle OAuth callback with authorization code.
     */
    fun handleCallback(code: String) {
//        val verifier = codeVerifier ?: return
//        codeVerifier = null

        scope.launch {
            runCatching { exchangeCodeForToken(code, codeVerifier) }
                .onSuccess { token ->
                    saveToken(token)
                    fetchUserProfile(token)
                    notifyAuthChanged()
                }
                .onFailure { println("OAuth error: ${it.message}") }
        }
    }

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

    private fun fetchUserProfile(token: String) = runCatching {
        val github = GitHubBuilder().withOAuthToken(token).build()
        myState = State(github.myself.login)
    }.onFailure { println("Failed to fetch user profile: ${it.message}") }

    override fun dispose() = scope.cancel()
}
