package org.jetbrains.plugins.oauth2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Provides the Settings page for the plugin.
 */
class AuthConfigurable : Configurable, Disposable {

    private val authService by lazy { service<AuthService>() }
    private lateinit var panel: JPanel

    override fun getDisplayName() = "My Plugin Auth"

    override fun createComponent() = JPanel(BorderLayout()).also { panel = it }.apply {
        updatePanel()
        ApplicationManager.getApplication().messageBus.connect(this@AuthConfigurable)
            .subscribe(AUTH_TOPIC, AuthListener { invokeLater(ModalityState.any()) { updatePanel() } })
    }

    private fun updatePanel() = panel.apply {
        removeAll()
        add(panel {
            if (authService.isLoggedIn()) {
                row("Username") { label(authService.state.username ?: "Unknown") }
                row { button("Logout") { authService.logout() } }
            } else {
                row { button("Login with GitHub") { authService.startLogin() } }
            }
        })
        revalidate()
    }

    override fun isModified() = false
    override fun apply() {}
    override fun dispose() {}
    override fun disposeUIResources() {
        panel.removeAll()
        Disposer.dispose(this)
    }
}
