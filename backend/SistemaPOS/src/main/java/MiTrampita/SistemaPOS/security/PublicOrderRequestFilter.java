package MiTrampita.SistemaPOS.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Limita intentos y JSON antes de deserializar, incluyendo cuerpos sin Content-Length. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE + 50) @RequiredArgsConstructor
public class PublicOrderRequestFilter extends OncePerRequestFilter {
    private static final int MAX_BYTES = 32768;
    private final PublicOrderRateLimiter limite;
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || !path.matches("/api/public/tiendas/[^/]+/pedidos");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        try { limite.validar(request.getRemoteAddr()); }
        catch (ResponseStatusException ex) { reject(response, 429, "Demasiados intentos. Espera un minuto y vuelve a intentar"); return; }
        if (request.getContentLengthLong() > MAX_BYTES) { reject(response, 413, "El pedido excede el tamaño permitido"); return; }
        byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) { reject(response, 413, "El pedido excede el tamaño permitido"); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        try { if (!isFinished()) listener.onDataAvailable(); if (isFinished()) listener.onAllDataRead(); }
                        catch (IOException ex) { listener.onError(ex); }
                    }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
        }, response);
    }
    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setCharacterEncoding("UTF-8"); response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        if (status == 429) response.setHeader("Retry-After", "60");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
