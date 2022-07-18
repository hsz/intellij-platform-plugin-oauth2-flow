package com.github.hsz.intellijplatformpluginoauth2flow.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.github.hsz.intellijplatformpluginoauth2flow.services.GoogleAuthRequest
import com.github.hsz.intellijplatformpluginoauth2flow.services.GoogleAuthService

class AuthAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        service<GoogleAuthService>().authorize(GoogleAuthRequest())
    }
}
