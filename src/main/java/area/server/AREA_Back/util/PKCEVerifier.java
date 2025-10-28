package area.server.AREA_Back.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * PKCE (Proof Key for Code Exchange) verification utilities
 * Implements RFC 7636: https://tools.ietf.org/html/rfc7636
 */
public class PKCEVerifier {
    
    /**
     * Verify that code_verifier matches the code_challenge
     * 
     * @param codeVerifier The code verifier from the client
     * @param codeChallenge The stored code challenge
     * @param method The challenge method (S256 or plain)
     * @return true if verification succeeds
     */
    public static boolean verify(String codeVerifier, String codeChallenge, String method) {
        if (codeVerifier == null || codeChallenge == null) {
            return false;
        }
        
        if ("plain".equals(method)) {
            // Plain method: code_challenge == code_verifier
            return codeChallenge.equals(codeVerifier);
        } else if ("S256".equals(method)) {
            // S256 method: code_challenge == BASE64URL(SHA256(code_verifier))
            String computedChallenge = generateCodeChallenge(codeVerifier);
            return codeChallenge.equals(computedChallenge);
        }
        
        return false;
    }
    
    /**
     * Generate code_challenge from code_verifier using SHA256
     */
    public static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
            return base64URLEncode(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Base64URL encode without padding (RFC 4648 § 5)
     */
    private static String base64URLEncode(byte[] data) {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(data);
    }
}
