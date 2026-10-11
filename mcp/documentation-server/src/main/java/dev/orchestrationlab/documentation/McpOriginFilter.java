package dev.orchestrationlab.documentation;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;

/**
 * Özel sunucular arası MCP sınırına tarayıcı Origin erişimini kapatır. Bu filtre kimlik doğrulama veya
 * TLS sağlamaz; dağıtım güvenilir özel ağ varsayımına dayanır.
 */
@Component
public class McpOriginFilter extends OncePerRequestFilter {
    /**
     * Tam /mcp yolunda Origin başlığı varsa 403 verir ve zinciri durdurur; diğer istekleri sıradaki
     * filtreye aktarır.
     */
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().equals("/mcp") && request.getHeader("Origin") != null) {
            response.sendError(403); return;
        }
        chain.doFilter(request, response);
    }
}
