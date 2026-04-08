package org.jetbrains.plugins.oauth2

import com.google.gson.Gson
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.BrowserUtil
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.DigestUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService
import org.jetbrains.io.response
import org.kohsuke.github.GitHubBuilder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private const val OAUTH_CLIENT_ID = "Iv23ctuaqovvSqutt2KT"
private const val OAUTH_CLIENT_SECRET = "09938f222c92793fc691defc64e03e2643011ecc"
private const val SERVICE_NAME = "myplugin"
private const val HTML_RESPONSE = "<p><b>Authentication Successful!</b> Close this tab and return to the IDE.</p>"

sealed interface AuthState {
    data object Disconnected : AuthState
    data class Connected(val username: String? = null) : AuthState
}

@Service(Service.Level.APP)
class AuthService(val coroutineScope: CoroutineScope) {

    private val requests = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val redirectUri get() = "http://localhost:${BuiltInServerManager.getInstance().port}/api/$SERVICE_NAME"
    private val credentials = CredentialAttributes(generateServiceName("MyPluginAuth", "OAuthToken"))
    private val httpClient = HttpClient.newHttpClient()
    private val gson = Gson()
    private val _state = MutableStateFlow<AuthState>(AuthState.Disconnected)

    val state = _state.asStateFlow()

    @Volatile
    private var cachedToken: String? = null
    private var storedToken: String?
        get() = cachedToken
        set(value) {
            cachedToken = value
            PasswordSafe.instance.setPassword(credentials, value)
        }

    @Volatile
    private var loginJob: Job? = null

    init {
        coroutineScope.launch {
            val token = PasswordSafe.instance.getPassword(credentials) ?: return@launch

            cachedToken = token
            _state.value = AuthState.Connected(fetchUserProfile(token))
        }
    }

    fun login() {
        if (cachedToken != null || loginJob?.isActive == true) return
        loginJob = coroutineScope.launch {
            try {
                val token = requestToken()
                storedToken = token
                _state.value = AuthState.Connected(fetchUserProfile(token))
            } catch (e: CancellationException) {
                _state.value = AuthState.Disconnected
                throw e
            } catch (t: Throwable) {
                storedToken = null
                _state.value = AuthState.Disconnected
                thisLogger().warn("OAuth login failed", t)
            } finally {
                loginJob = null
            }
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        loginJob = null

        if (cachedToken == null) {
            _state.value = AuthState.Disconnected
        }
    }

    fun logout() = coroutineScope.launch {
        cancelLogin()
        storedToken = null
        _state.value = AuthState.Disconnected
    }

    private suspend fun requestToken(): String {
        val requestId = DigestUtil.digestToHash(DigestUtil.sha512())
        val codeVerifier = DigestUtil.digestToHash(DigestUtil.sha512())

        val request = CompletableDeferred<String>()
        requests[requestId] = request
        try {
            BrowserUtil.browse(buildAuthorizationUrl(requestId, codeVerifier))
            return exchangeCodeForToken(request.await(), codeVerifier)
        } finally {
            request.cancel()
            requests.remove(requestId)
        }
    }

    private fun buildAuthorizationUrl(requestId: String, codeVerifier: String): String {
        val codeChallenge = DigestUtil.sha256().digest(codeVerifier.toByteArray())
        val codeChallengeEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(codeChallenge)

        return "https://github.com/login/oauth/authorize" +
                "?client_id=$OAUTH_CLIENT_ID" +
                "&scope=read:user%20user:email" +
                "&state=$requestId" +
                "&redirect_uri=$redirectUri" +
                "&code_challenge=$codeChallengeEncoded" +
                "&code_challenge_method=S256"
    }

    private suspend fun exchangeCodeForToken(code: String, codeVerifier: String): String = withContext(Dispatchers.IO) {
        val uri = "https://github.com/login/oauth/access_token" +
                "?client_id=$OAUTH_CLIENT_ID" +
                "&client_secret=$OAUTH_CLIENT_SECRET" +
                "&code=$code" +
                "&redirect_uri=$redirectUri" +
                "&code_verifier=$codeVerifier"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        gson.fromJson(response.body(), Map::class.java)["access_token"] as? String
            ?: throw IllegalStateException("Failed to exchange code for token")
    }

    /**
     * Handles the OAuth2 callback by providing a local REST endpoint.
     * The endpoint is available at: http://localhost:63342/api/myplugin
     */
    internal class AuthRestService : RestService() {

        override fun getServiceName() = SERVICE_NAME

        override fun execute(
            urlDecoder: QueryStringDecoder,
            request: FullHttpRequest,
            context: ChannelHandlerContext,
        ): String? {
            val parameters = urlDecoder.parameters()
            val state = parameters["state"]?.firstOrNull() ?: return "No authorization state found"
            val code = parameters["code"]?.firstOrNull() ?: return "No authorization code found"
            val currentRequest = service<AuthService>().requests[state] ?: return "No active OAuth request found"

            currentRequest.complete(code)
            sendResponse(request, context, response("text/html", Unpooled.wrappedBuffer(HTML_RESPONSE.toByteArray())))
            return null
        }
    }

    private suspend fun fetchUserProfile(token: String): String? = withContext(Dispatchers.IO) {
        runCatching { GitHubBuilder().withOAuthToken(token).build().myself.login }
            .onFailure { thisLogger().warn("Failed to fetch user profile", it) }
            .getOrNull()
    }
}
