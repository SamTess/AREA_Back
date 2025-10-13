package area.server.AREA_Back.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;

@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.google.client-id")
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.google.client-secret")
@Service
public class OAuthGoogleService extends OAuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final UserRepository userRepository;
    private final RedisTokenService redisTokenService;

    @Autowired
    private MeterRegistry meterRegistry;

    private Counter oauthLoginSuccessCounter;
    private Counter oauthLoginFailureCounter;
    private Counter authenticateCalls;
    private Counter tokenExchangeCalls;
    private Counter tokenExchangeFailures;

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
            "/oauth-icons/google.svg",
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

    @PostConstruct
    public void initMetrics() {
        this.oauthLoginSuccessCounter = Counter.builder("oauth.google.login.success")
            .description("Number of successful Google OAuth logins")
            .register(meterRegistry);

        this.oauthLoginFailureCounter = Counter.builder("oauth.google.login.failure")
            .description("Number of failed Google OAuth logins")
            .register(meterRegistry);

        this.authenticateCalls = Counter.builder("oauth.google.authenticate.calls")
            .description("Number of Google OAuth authentication calls")
            .register(meterRegistry);

        this.tokenExchangeCalls = Counter.builder("oauth.google.token_exchange.calls")
            .description("Number of Google OAuth token exchange calls")
            .register(meterRegistry);

        this.tokenExchangeFailures = Counter.builder("oauth.google.token_exchange.failures")
            .description("Number of failed Google OAuth token exchanges")
            .register(meterRegistry);
    }

    // TODO : implement
    @Override
    public AuthResponse authenticate(OAuthLoginRequest request, HttpServletResponse response) {
        authenticateCalls.increment();
        // FIXME: Implement Google OAuth authentication logic
        // For now, this is a placeholder that will be marked as failure
        oauthLoginFailureCounter.increment();
        return new AuthResponse();
    }
}
