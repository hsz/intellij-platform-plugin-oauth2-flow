package com.github.hsz.intellijplatformpluginoauth2flow.services

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.collaboration.auth.credentials.SimpleCredentials
import com.intellij.collaboration.auth.services.OAuthCredentialsAcquirer
import com.intellij.collaboration.auth.services.OAuthCredentialsAcquirerHttp
import com.intellij.util.Url

class GoogleAuthCredentialsAcquirer(
    private val authorizationCodeUrl: Url,
    private val tokenUrl: Url,
    private val clientId: String,
    private val clientSecret: String,
) : OAuthCredentialsAcquirer<Credentials> {

    override fun acquireCredentials(code: String): OAuthCredentialsAcquirer.AcquireCredentialsResult<Credentials> =
        OAuthCredentialsAcquirerHttp.requestToken(getTokenUrlWithParameters(code)) { body, headers ->
            // TODO: extract token from body
            SimpleCredentials("foo")
        }

    private fun getTokenUrlWithParameters(code: String) = tokenUrl.addParameters(
        mapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "code" to code,
            "grant_type" to "authorization_code",
            "redirect_uri" to authorizationCodeUrl.toExternalForm(),
        )
    )
}
