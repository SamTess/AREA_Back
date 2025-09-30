package area.server.AREA_Back.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.google", name = "client-id")
@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.google", name = "client-secret")
@Service
public class OAuthGoogleService extends OAuthService {

    public OAuthGoogleService(
        @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId,
        @Value("${spring.security.oauth2.client.registration.google.client-secret}") String googleClientSecret
    ) {
        super("google", googleClientId, googleClientSecret);
    }

    @Override
    public String exchangeToken(String authorizationCode) {
        return authorizationCode;
    }
}
