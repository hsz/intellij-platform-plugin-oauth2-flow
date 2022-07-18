package com.github.hsz.intellijplatformpluginoauth2flow.services

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.collaboration.auth.services.OAuthRequest
import com.intellij.openapi.components.service
import com.intellij.util.Urls
import com.intellij.util.io.DigestUtil
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService

class GoogleAuthRequest : OAuthRequest<Credentials> {

    private val clientId = "153933018950-8i2ku09c4uume07v48d51jf3u12au15r.apps.googleusercontent.com"
    private val clientSecret = "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"
    // it is fine to store `clientSecret` due to: https://developers.google.com/identity/protocols/oauth2#installed

    private val authUrl = Urls.newFromEncoded("https://accounts.google.com/o/oauth2/v2/auth")
    private val tokenUrl = Urls.newFromEncoded("https://oauth2.googleapis.com/token")
    private val state get() = DigestUtil.randomToken() // state token to prevent request forgery
    private val port by lazy { BuiltInServerManager.getInstance().port }

    override val authorizationCodeUrl by lazy {
        Urls.newFromEncoded("http://localhost:$port/${RestService.PREFIX}/${service<GoogleAuthService>().name}")
    }

    override val credentialsAcquirer = GoogleAuthCredentialsAcquirer(authorizationCodeUrl, tokenUrl, clientId, clientSecret)

    override val authUrlWithParameters = authUrl.addParameters(
        mapOf(
            "scope" to "profile",
            "include_granted_scopes" to "true",
            "response_type" to "code",
            "state" to state,
            "redirect_uri" to authorizationCodeUrl.toString(),
            "client_id" to clientId,
        )
    )
}
