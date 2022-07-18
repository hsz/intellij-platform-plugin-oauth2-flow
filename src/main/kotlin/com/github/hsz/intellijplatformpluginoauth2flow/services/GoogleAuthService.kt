package com.github.hsz.intellijplatformpluginoauth2flow.services

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.collaboration.auth.services.OAuthServiceBase
import com.intellij.openapi.components.Service

@Service
internal class GoogleAuthService : OAuthServiceBase<Credentials>() {

    // TODO: rename
    override val name = "jakub"

    override fun revokeToken(token: String) = Unit
}
