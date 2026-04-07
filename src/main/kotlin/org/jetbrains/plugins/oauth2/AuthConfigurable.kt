package org.jetbrains.plugins.oauth2

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.*

class AuthConfigurable : BoundConfigurable("My Plugin Auth") {

    private val authService by lazy { service<AuthService>() }

    override fun createPanel() = panel {
        val content = Wrapper()
        val scope = CoroutineScope(SupervisorJob())

        row {
            cell(content).align(AlignX.FILL)
        }

        scope.launch(Dispatchers.IO) {
            authService.state.collect { content.setContent(createView(it)) }
        }

        disposable?.let { Disposer.register(it) { scope.cancel() } }
    }

    private fun createView(state: AuthState) = panel {
        when (state) {
            is AuthState.Connected -> row("Username") {
                label(state.username ?: "Unknown")
                button("Logout") { authService.logout() }
            }

            is AuthState.Disconnected -> row {
                button("Login with GitHub") { authService.login() }
            }
        }
    }
}
