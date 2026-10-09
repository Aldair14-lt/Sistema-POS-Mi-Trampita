package MiTrampita.SistemaPOS.security;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Huella privada de la credencial: nunca forma parte de la respuesta del usuario. */
public final class SessionCredentials {
    private static final String ATTRIBUTE = SessionCredentials.class.getName();
    private SessionCredentials() { }
    public static void remember(HttpSession session, String credential) { session.setAttribute(ATTRIBUTE, digest(credential)); }
    public static boolean matches(HttpSession session, String credential) {
        return session != null && session.getAttribute(ATTRIBUTE) instanceof byte[] saved
            && MessageDigest.isEqual(saved, digest(credential));
    }
    private static byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
