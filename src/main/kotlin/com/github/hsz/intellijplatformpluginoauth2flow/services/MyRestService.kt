package com.github.hsz.intellijplatformpluginoauth2flow.services

import com.intellij.collaboration.auth.OAuthCallbackHandlerBase
import com.intellij.collaboration.auth.services.OAuthService
import com.intellij.openapi.components.service

class MyRestService : OAuthCallbackHandlerBase() {

    override fun oauthService(): OAuthService<*> = service<GoogleAuthService>()

    // TODO: provide a better HTML response
    override fun handleAcceptCode(isAccepted: Boolean) = AcceptCodeHandleResult.Page("<strong>OK</strong>")
}
