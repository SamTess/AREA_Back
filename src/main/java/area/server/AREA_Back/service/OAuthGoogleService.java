package area.server.AREA_Back.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;

@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.google.client-id")
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.google.client-secret")
@Service
public class OAuthGoogleService extends OAuthService {

    public OAuthGoogleService(
        @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId,
        @Value("${spring.security.oauth2.client.registration.google.client-secret}") String googleClientSecret,
        PasswordEncoder passwordEncoder,
        UserOAuthIdentityRepository userOAuthIdentityRepository,
        UserRepository userRepository,
        RedisTokenService redisTokenService,
        JwtService jwtService
    ) {
        super(
            "google",
            "Google",
            "https://cdn1.iconfinder.com/data/icons/google-s-logo/150/Google_Icons-09-512.png",
            "https://accounts.google.com/o/oauth2/v2/auth?client_id=" + googleClientId + "&redirect_uri=http://localhost:3000&response_type=code&scope=openid%20email%20profile",
            googleClientId,
            googleClientSecret,
            jwtService
        );
        this.passwordEncoder = passwordEncoder;
        this.userOAuthIdentityRepository = userOAuthIdentityRepository;
        this.userRepository = userRepository;
        this.redisTokenService = redisTokenService;
    }

    // TODO : implement
    @Override
    public AuthResponse authenticate(OAuthLoginRequest request, HttpServletResponse response) {
        return new AuthResponse();
    }
}
