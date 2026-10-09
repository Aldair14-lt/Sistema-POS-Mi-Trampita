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

/** Acota JSON antes de deserializar, incluso sin Content-Length, y limita envíos públicos. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE + 50) @RequiredArgsConstructor
public class ApiRequestBodyFilter extends OncePerRequestFilter {
    private final PublicOrderRateLimiter limite;
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("GET".equals(request.getMethod()) && path.startsWith("/api/public/tiendas/")) return false;
        return !path.startsWith("/api/") || !java.util.Set.of("POST", "PUT", "PATCH").contains(request.getMethod());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("GET".equals(request.getMethod())) {
            try { limite.validarLectura(request.getRemoteAddr()); }
            catch (ResponseStatusException ex) { reject(response, 429, "Demasiadas consultas. Espera un minuto"); return; }
            chain.doFilter(request, response); return;
        }
        boolean publicOrder = "POST".equals(request.getMethod()) && path.matches("/api/public/tiendas/[^/]+/pedidos");
        if (publicOrder) {
            try { limite.validar(request.getRemoteAddr()); }
            catch (ResponseStatusException ex) { reject(response, 429, "Demasiados intentos. Espera un minuto y vuelve a intentar"); return; }
        }
        int maxBytes = path.equals("/api/auth/login") ? 4096 : publicOrder ? 32768 : 262144;
        if (request.getContentLengthLong() > maxBytes) { reject(response, 413, "La solicitud excede el tamaño permitido"); return; }
        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) { reject(response, 413, "La solicitud excede el tamaño permitido"); return; }
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
