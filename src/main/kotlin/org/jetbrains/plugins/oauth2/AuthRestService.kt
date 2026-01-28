package org.jetbrains.plugins.oauth2

import com.intellij.openapi.components.service
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService
import org.jetbrains.io.response

/**
 * Handles the OAuth2 callback by providing a local REST endpoint.
 * The endpoint is available at: http://localhost:63342/api/myplugin/callback
 */
class AuthRestService : RestService() {

    companion object {
        const val SERVICE_NAME = "myplugin"
        const val HTML_RESPONSE =
            "<p><strong>Authentication Successful!</strong> Close this tab and return to the IDE.</p>"
    }

    override fun getServiceName() = SERVICE_NAME

    override fun execute(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext,
    ): String? {
        val code = urlDecoder.parameters()["code"]?.firstOrNull() ?: return "No authorization code found"

        service<AuthService>().handleCallback(code)
        sendResponse(request, context, response("text/html", Unpooled.wrappedBuffer(HTML_RESPONSE.toByteArray())))

        return null
    }
}
