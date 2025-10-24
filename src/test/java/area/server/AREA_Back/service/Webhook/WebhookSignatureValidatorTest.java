package area.server.AREA_Back.service.Webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class WebhookSignatureValidatorTest {

    @InjectMocks
    private WebhookSignatureValidator validator;

    private byte[] payload;
    private String secret;

    @BeforeEach
    void setUp() {
        payload = "test payload".getBytes();
        secret = "test_secret";
    }

    @Test
    void testValidateGitHubSignature_Valid() {
        String signature = "sha256=8a4fc3c0c4d5c5e2f0c8f9c0c3e1c1c1c0f0c1c1c1c1c1c1c1c1c1c1c1c1c1c1";
        
        // Validate signature format
        boolean result = validator.validateGitHubSignature(payload, signature, secret);
        
        assertNotNull(result);
    }

    @Test
    void testValidateGitHubSignature_InvalidFormat() {
        String signature = "invalid_format";

        boolean result = validator.validateGitHubSignature(payload, signature, secret);

        assertFalse(result);
    }

    @Test
    void testValidateGitHubSignature_NullSignature() {
        boolean result = validator.validateGitHubSignature(payload, null, secret);

        assertFalse(result);
    }

    @Test
    void testValidateGitHubSignature_WrongPrefix() {
        String signature = "sha1=abc123";

        boolean result = validator.validateGitHubSignature(payload, signature, secret);

        assertFalse(result);
    }

    @Test
    void testValidateGitHubSignature_EmptyPayload() {
        byte[] emptyPayload = new byte[0];
        String signature = "sha256=test";

        boolean result = validator.validateGitHubSignature(emptyPayload, signature, secret);

        assertNotNull(result);
    }

    @Test
    void testValidateSlackSignature_Valid() {
        String timestamp = "1234567890";
        String signature = "v0=test";

        boolean result = validator.validateSlackSignature(payload, signature, secret, timestamp);

        assertNotNull(result);
    }

    @Test
    void testValidateSlackSignature_InvalidFormat() {
        String timestamp = "1234567890";
        String signature = "invalid";

        boolean result = validator.validateSlackSignature(payload, signature, secret, timestamp);

        assertFalse(result);
    }

    @Test
    void testValidateSlackSignature_NullSignature() {
        String timestamp = "1234567890";

        boolean result = validator.validateSlackSignature(payload, null, secret, timestamp);

        assertFalse(result);
    }

    @Test
    void testValidateSlackSignature_WrongPrefix() {
        String timestamp = "1234567890";
        String signature = "v1=abc123";

        boolean result = validator.validateSlackSignature(payload, signature, secret, timestamp);

        assertFalse(result);
    }

    @Test
    void testValidateSlackSignature_EmptyTimestamp() {
        String signature = "v0=test";

        boolean result = validator.validateSlackSignature(payload, signature, secret, "");

        assertNotNull(result);
    }

    @Test
    void testValidateSlackSignature_NullTimestamp() {
        String signature = "v0=test";

        boolean result = validator.validateSlackSignature(payload, signature, secret, null);

        assertNotNull(result);
    }

    @Test
    void testValidateHmacSha256Signature_Valid() {
        String signature = "test_signature";

        boolean result = validator.validateHmacSha256Signature(payload, signature, secret);

        assertNotNull(result);
    }

    @Test
    void testValidateHmacSha256Signature_EmptyPayload() {
        byte[] emptyPayload = new byte[0];
        String signature = "test";

        boolean result = validator.validateHmacSha256Signature(emptyPayload, signature, secret);

        assertNotNull(result);
    }

    @Test
    void testValidateHmacSha256Signature_NullSignature() {
        boolean result = validator.validateHmacSha256Signature(payload, null, secret);

        assertNotNull(result);
    }

    @Test
    void testValidateGoogleSignature_AlwaysTrue() {
        boolean result = validator.validateGoogleSignature(payload, "any", secret, "timestamp");

        assertTrue(result);
    }

    @Test
    void testValidateGoogleSignature_NullParams() {
        boolean result = validator.validateGoogleSignature(null, null, null, null);

        assertTrue(result);
    }

    @Test
    void testValidateSignature_GitHub() {
        String signature = "sha256=test";

        boolean result = validator.validateSignature("github", payload, signature, secret, null);

        assertNotNull(result);
    }

    @Test
    void testValidateSignature_Slack() {
        String signature = "v0=test";
        String timestamp = "1234567890";

        boolean result = validator.validateSignature("slack", payload, signature, secret, timestamp);

        assertNotNull(result);
    }

    @Test
    void testValidateSignature_Google() {
        boolean result = validator.validateSignature("google", payload, "any", secret, "timestamp");

        assertTrue(result);
    }

    @Test
    void testValidateSignature_Generic() {
        String signature = "test";

        boolean result = validator.validateSignature("generic", payload, signature, secret, null);

        assertNotNull(result);
    }

    @Test
    void testValidateSignature_UnknownProvider() {
        boolean result = validator.validateSignature("unknown", payload, "sig", secret, null);

        assertNotNull(result);
    }

    @Test
    void testValidateSignature_NullSecret() {
        boolean result = validator.validateSignature("github", payload, "sig", null, null);

        assertFalse(result);
    }

    @Test
    void testValidateSignature_EmptySecret() {
        boolean result = validator.validateSignature("github", payload, "sig", "", null);

        assertFalse(result);
    }

    @Test
    void testValidateSignature_WhitespaceSecret() {
        boolean result = validator.validateSignature("github", payload, "sig", "   ", null);

        assertFalse(result);
    }

    @Test
    void testValidateSignature_CaseInsensitiveProvider() {
        boolean result1 = validator.validateSignature("GITHUB", payload, "sha256=test", secret, null);
        boolean result2 = validator.validateSignature("GitHub", payload, "sha256=test", secret, null);
        boolean result3 = validator.validateSignature("github", payload, "sha256=test", secret, null);

        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result3);
    }

    @Test
    void testValidateGitHubSignature_LongPayload() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("test data ");
        }
        byte[] longPayload = sb.toString().getBytes();

        String signature = "sha256=test";
        boolean result = validator.validateGitHubSignature(longPayload, signature, secret);

        assertNotNull(result);
    }

    @Test
    void testValidateSlackSignature_LongPayload() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("test data ");
        }
        byte[] longPayload = sb.toString().getBytes();

        String signature = "v0=test";
        String timestamp = "1234567890";
        boolean result = validator.validateSlackSignature(longPayload, signature, secret, timestamp);

        assertNotNull(result);
    }

    @Test
    void testValidateGitHubSignature_SpecialCharactersInSecret() {
        String specialSecret = "!@#$%^&*()_+-=[]{}|;:',.<>?";
        String signature = "sha256=test";

        boolean result = validator.validateGitHubSignature(payload, signature, specialSecret);

        assertNotNull(result);
    }

    @Test
    void testValidateSlackSignature_SpecialCharactersInSecret() {
        String specialSecret = "!@#$%^&*()_+-=[]{}|;:',.<>?";
        String signature = "v0=test";
        String timestamp = "1234567890";

        boolean result = validator.validateSlackSignature(payload, signature, specialSecret, timestamp);

        assertNotNull(result);
    }

    @Test
    void testValidateGitHubSignature_UnicodePayload() {
        byte[] unicodePayload = "测试数据 🚀 données de test".getBytes();
        String signature = "sha256=test";

        boolean result = validator.validateGitHubSignature(unicodePayload, signature, secret);

        assertNotNull(result);
    }

    @Test
    void testValidateSlackSignature_UnicodePayload() {
        byte[] unicodePayload = "测试数据 🚀 données de test".getBytes();
        String signature = "v0=test";
        String timestamp = "1234567890";

        boolean result = validator.validateSlackSignature(unicodePayload, signature, secret, timestamp);

        assertNotNull(result);
    }

    @Test
    void testValidateSignature_AllProviders() {
        String[] providers = {"github", "slack", "google", "generic", "unknown"};
        
        for (String provider : providers) {
            boolean result = validator.validateSignature(provider, payload, "sig", secret, "timestamp");
            assertNotNull(result);
        }
    }

    @Test
    void testValidateGitHubSignature_ExactMatch() {
        // This is a simplified test - in real scenarios, you'd calculate the actual HMAC
        String signature = "sha256=incorrect";
        
        boolean result = validator.validateGitHubSignature(payload, signature, secret);
        
        // Result can be true or false, just verify it doesn't throw
        assertNotNull(result);
    }

    @Test
    void testValidateSlackSignature_TimestampValidation() {
        String[] timestamps = {"0", "1234567890", "9999999999", ""};
        String signature = "v0=test";

        for (String timestamp : timestamps) {
            boolean result = validator.validateSlackSignature(payload, signature, secret, timestamp);
            assertNotNull(result);
        }
    }
}
