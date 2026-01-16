package org.jetbrains.plugins.oauth2

import com.intellij.openapi.components.service
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.util.CharsetUtil
import org.jetbrains.ide.RestService

/**
 * Handles the OAuth2 callback by providing a local REST endpoint.
 * The endpoint is available at: http://localhost:63342/api/myplugin/callback
 */
class AuthRestService : RestService() {

    override fun getServiceName(): String = "myplugin"

    override fun execute(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext,
    ): String? {
        if (!urlDecoder.path().contains("/callback")) {
            return "Unknown path"
        }

        urlDecoder.parameters()["code"]
            ?.firstOrNull()
            ?.let { service<AuthService>().handleCallback(it) }
            ?: return "No authorization code found"

        val html = "<p><strong>Authentication Successful!</strong> You can now close this tab and return to the IDE.</p>"

        sendResponse(request, context, DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.copiedBuffer(html, CharsetUtil.UTF_8)
        ).apply {
            headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8")
            HttpUtil.setContentLength(this, content().readableBytes().toLong())
        })

        return null
    }
}
