package dev.orchestrationlab.documentation;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;

/** Private server-to-server capability. Browser-origin MCP access is deliberately disallowed. */
@Component
public class McpOriginFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().equals("/mcp") && request.getHeader("Origin") != null) {
            response.sendError(403); return;
        }
        chain.doFilter(request, response);
    }
}
