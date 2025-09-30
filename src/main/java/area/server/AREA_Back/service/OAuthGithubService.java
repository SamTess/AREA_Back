package area.server.AREA_Back.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.github", name = "client-id")
@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.github", name = "client-secret")
@Service
public class OAuthGithubService extends OAuthService {

    public OAuthGithubService(
        @Value("${spring.security.oauth2.client.registration.github.client-id}") String githubClientId,
        @Value("${spring.security.oauth2.client.registration.github.client-secret}") String githubClientSecret
    ) {
        super("github", githubClientId, githubClientSecret);
    }

    @Override
    public String exchangeToken(String authorizationCode) {
        return authorizationCode; // Placeholder implementation
    }
}
