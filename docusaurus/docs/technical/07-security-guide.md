# Security Architecture Guide

## Table of Contents
- [Security Overview](#security-overview)
- [Authentication Security](#authentication-security)
- [Authorization Framework](#authorization-framework)
- [Data Protection](#data-protection)
- [API Security](#api-security)
- [Infrastructure Security](#infrastructure-security)
- [Security Configuration](#security-configuration)
- [Threat Model](#threat-model)
- [Security Best Practices](#security-best-practices)

## Security Overview

The AREA backend implements a comprehensive security framework designed to protect user data, authenticate users securely, and provide robust protection against common web application vulnerabilities.

### Security Principles
- **Defense in Depth**: Multiple layers of security controls
- **Principle of Least Privilege**: Minimal access rights
- **Secure by Default**: Security-first configuration
- **Zero Trust**: Verify every request and user
- **Data Minimization**: Collect and store only necessary data

## Authentication Security

### JWT Token Security

#### Token Structure and Claims
```java
// Access Token Claims
{
    "sub": "user-uuid",           // Subject (user ID)
    "iat": 1640995200,           // Issued at
    "exp": 1640995800,           // Expiration (15 minutes)
    "type": "access_token",      // Token type
    "jti": "token-uuid"          // JWT ID for revocation
}

// Refresh Token Claims
{
    "sub": "user-uuid",
    "iat": 1640995200,
    "exp": 1641600000,           // Expiration (7 days)
    "type": "refresh_token",
    "jti": "refresh-token-uuid"
}
```

#### Token Security Features
```java
@Service
public class JwtService {
    
    // Secure key generation
    public static String generateSecureKey() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] key = new byte[32]; // 256 bits
        secureRandom.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
    
    // Token generation with secure signing
    private String generateToken(Map<String, Object> extraClaims, 
                                String subject,
                                Date expiration, 
                                SecretKey signingKey) {
        return Jwts.builder()
            .setClaims(extraClaims)
            .setSubject(subject)
            .setIssuedAt(new Date(System.currentTimeMillis()))
            .setExpiration(expiration)
            .setId(UUID.randomUUID().toString()) // JWT ID for revocation
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }
}
```

#### Token Storage Security
```java
// HTTP-Only Cookie Configuration
@Component
@ConfigurationProperties(prefix = "app.jwt.cookie")
public class JwtCookieProperties {
    
    private boolean secure = false;      // HTTPS only in production
    private String sameSite = "Strict";  // CSRF protection
    private String domain;               // Cookie domain restriction
    private boolean httpOnly = true;     // Prevent XSS access
    
    // Cookie creation with security flags
    private Cookie createSecureCookie(String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);              // Prevent JavaScript access
        cookie.setSecure(secure);              // HTTPS only
        cookie.setPath("/");                   // Application-wide
        cookie.setMaxAge(maxAge);              // Expiration time
        cookie.setAttribute("SameSite", sameSite); // CSRF protection
        
        if (domain != null && !domain.isEmpty()) {
            cookie.setDomain(domain);          // Domain restriction
        }
        
        return cookie;
    }
}
```

### Password Security

#### Password Hashing
```java
@Configuration
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with strength 12 (2^12 rounds)
        return new BCryptPasswordEncoder(12);
    }
}

@Service
public class AuthService {
    
    private final PasswordEncoder passwordEncoder;
    
    // Secure password validation
    private boolean isValidPassword(String rawPassword, String encodedPassword) {
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (Exception e) {
            log.warn("Password validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    // Account lockout mechanism
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int ACCOUNT_LOCK_DURATION_MINUTES = 30;
    
    private void handleFailedLogin(UserLocalIdentity identity) {
        identity.setFailedLoginAttempts(identity.getFailedLoginAttempts() + 1);
        
        if (identity.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
            identity.setLockedUntil(LocalDateTime.now()
                .plusMinutes(ACCOUNT_LOCK_DURATION_MINUTES));
            log.warn("Account locked due to too many failed attempts: {}", 
                identity.getEmail());
        }
        
        userLocalIdentityRepository.save(identity);
    }
}
```

### OAuth2 Security

#### Secure OAuth Flow
```java
@Service
public class OAuthGithubService extends OAuthService {
    
    // Secure state parameter generation
    private String generateSecureState() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] stateBytes = new byte[32];
        secureRandom.nextBytes(stateBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
    }
    
    // Authorization URL with security parameters
    public String getUserAuthUrl() {
        return "https://github.com/login/oauth/authorize"
            + "?client_id=" + clientId
            + "&scope=" + URLEncoder.encode("user:email,repo", StandardCharsets.UTF_8)
            + "&state=" + generateSecureState()
            + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
    }
    
    // Secure token exchange
    @Override
    public AuthResponse authenticate(OAuthLoginRequest request, HttpServletResponse response) {
        // Validate state parameter
        if (!validateState(request.getState())) {
            throw new SecurityException("Invalid OAuth state parameter");
        }
        
        // Exchange authorization code for access token
        // Implement with proper error handling and validation
    }
}
```

## Authorization Framework

### Role-Based Access Control (RBAC)
```java
@Entity
public class User {
    
    @Column(name = "is_admin", nullable = false)
    private Boolean isAdmin = false;
    
    // Future: Role hierarchy
    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.USER;
}

public enum UserRole {
    USER,           // Regular user permissions
    MODERATOR,      // Extended permissions (future)
    ADMIN           // Full system access
}
```

### Resource-Level Authorization
```java
@Service
public class AreaAuthorizationService {
    
    /**
     * Checks if user can access area
     */
    public boolean canAccessArea(UUID userId, UUID areaId) {
        Area area = areaRepository.findById(areaId)
            .orElseThrow(() -> new AreaNotFoundException(areaId));
        
        // Users can only access their own areas
        // Admins can access all areas
        return area.getUser().getId().equals(userId) || 
               isUserAdmin(userId);
    }
    
    /**
     * Checks if user can modify area
     */
    public boolean canModifyArea(UUID userId, UUID areaId) {
        // Same as access for now, could be different in future
        return canAccessArea(userId, areaId);
    }
    
    private boolean isUserAdmin(UUID userId) {
        return userRepository.findById(userId)
            .map(User::getIsAdmin)
            .orElse(false);
    }
}
```

### Method-Level Security
```java
@RestController
@RequestMapping("/api/areas")
public class AreaController {
    
    @GetMapping("/{id}")
    @PreAuthorize("@areaAuthorizationService.canAccessArea(authentication.principal.userId, #id)")
    public ResponseEntity<AreaResponse> getAreaById(@PathVariable UUID id) {
        // Implementation
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("@areaAuthorizationService.canModifyArea(authentication.principal.userId, #id)")
    public ResponseEntity<AreaResponse> updateArea(@PathVariable UUID id, 
                                                  @RequestBody UpdateAreaRequest request) {
        // Implementation
    }
}
```

## Data Protection

### Encryption at Rest

#### Sensitive Data Encryption
```java
@Service
public class TokenEncryptionService {
    
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    
    @Value("${app.encryption.secret}")
    private String encryptionSecret;
    
    /**
     * Encrypts sensitive data using AES-GCM
     */
    public String encrypt(String plaintext) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(
                deriveKey(encryptionSecret), "AES");
            
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(
                GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);
            
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and ciphertext
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, encryptedWithIv, GCM_IV_LENGTH, cipherText.length);
            
            return Base64.getEncoder().encodeToString(encryptedWithIv);
            
        } catch (Exception e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }
    
    /**
     * Derives encryption key using PBKDF2
     */
    private byte[] deriveKey(String password) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] salt = "AREA-SALT-2024".getBytes(StandardCharsets.UTF_8);
        
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 100000, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        
        return factory.generateSecret(spec).getEncoded();
    }
}
```

#### Database Column Encryption
```java
@Entity
@Table(name = "a_service_accounts", schema = "area")
public class ServiceAccount {
    
    // Encrypted token storage
    @Column(name = "access_token_enc")
    private String accessTokenEnc;      // Encrypted access token
    
    @Column(name = "refresh_token_enc")
    private String refreshTokenEnc;     // Encrypted refresh token
    
    // Encryption helper methods
    @Transient
    public void setAccessToken(String accessToken) {
        this.accessTokenEnc = tokenEncryptionService.encrypt(accessToken);
    }
    
    @Transient
    public String getAccessToken() {
        return tokenEncryptionService.decrypt(this.accessTokenEnc);
    }
}
```

### Data Validation and Sanitization

#### Input Validation
```java
// Request DTO validation
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAreaRequest {
    
    @NotBlank(message = "Area name is required")
    @Size(min = 1, max = 255, message = "Area name must be between 1 and 255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-_]+$", message = "Area name contains invalid characters")
    private String name;
    
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;
    
    @NotNull(message = "User ID is required")
    private UUID userId;
}

// Custom validation annotations
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = JsonSchemaValidator.class)
public @interface ValidJsonSchema {
    String message() default "Invalid JSON schema";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

#### SQL Injection Prevention
```java
// Safe JPA queries
@Repository
public interface AreaRepository extends JpaRepository<Area, UUID> {
    
    // Safe parameterized query
    @Query("SELECT a FROM Area a WHERE a.user.id = :userId AND a.enabled = :enabled")
    Page<Area> findByUserIdAndEnabled(@Param("userId") UUID userId, 
                                     @Param("enabled") Boolean enabled, 
                                     Pageable pageable);
    
    // Safe native query with parameters
    @Query(value = "SELECT * FROM area.a_areas WHERE user_id = :userId " +
                   "AND actions @> :actionFilter", nativeQuery = true)
    List<Area> findByUserAndActionType(@Param("userId") UUID userId,
                                      @Param("actionFilter") String actionFilter);
}
```

## API Security

### CORS Configuration
```java
@Configuration
public class SecurityConfig {
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allowed origins (environment-specific)
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:3000",      // Development
            "https://*.yourdomain.com"    // Production
        ));
        
        // Allowed methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        
        // Allowed headers
        configuration.setAllowedHeaders(Arrays.asList("*"));
        
        // Allow credentials (required for cookies)
        configuration.setAllowCredentials(true);
        
        // Exposed headers
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "Content-Length"
        ));
        
        // Preflight cache duration
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

### Request Rate Limiting (Future Enhancement)
```java
@Component
public class RateLimitingFilter implements Filter {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    // Rate limiting configuration
    private static final int REQUESTS_PER_MINUTE = 60;
    private static final int REQUESTS_PER_HOUR = 1000;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientIP = getClientIP(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        // Check rate limits
        if (isRateLimited(clientIP, userAgent)) {
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setHeader("Retry-After", "60");
            return;
        }
        
        chain.doFilter(request, response);
    }
    
    private boolean isRateLimited(String clientIP, String userAgent) {
        // Implement sliding window rate limiting with Redis
        return false; // Placeholder
    }
}
```

### Content Security Policy
```java
@Component
public class SecurityHeadersFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Content Security Policy
        httpResponse.setHeader("Content-Security-Policy", 
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: https:; " +
            "font-src 'self'; " +
            "connect-src 'self'; " +
            "frame-ancestors 'none'");
        
        // Other security headers
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        httpResponse.setHeader("Permissions-Policy", 
            "camera=(), microphone=(), geolocation=()");
        
        chain.doFilter(request, response);
    }
}
```

## Infrastructure Security

### Database Security

#### Connection Security
```properties
# SSL/TLS database connection
spring.datasource.url=jdbc:postgresql://localhost:5432/area?sslmode=require&sslcert=client-cert.pem&sslkey=client-key.pem&sslrootcert=ca-cert.pem

# Connection pool security
spring.datasource.hikari.connection-test-query=SELECT 1
spring.datasource.hikari.validation-timeout=3000
spring.datasource.hikari.leak-detection-threshold=60000
```

#### Database User Privileges
```sql
-- Application database user with minimal privileges
CREATE USER area_app WITH PASSWORD 'secure_generated_password';

-- Grant only necessary permissions
GRANT CONNECT ON DATABASE area TO area_app;
GRANT USAGE ON SCHEMA area TO area_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA area TO area_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA area TO area_app;

-- Revoke unnecessary permissions
REVOKE CREATE ON SCHEMA area FROM area_app;
REVOKE ALL ON DATABASE area FROM PUBLIC;
```

### Redis Security

#### Redis Authentication and SSL
```properties
# Redis security configuration
spring.data.redis.host=${REDIS_HOST}
spring.data.redis.port=${REDIS_PORT}
spring.data.redis.password=${REDIS_PASSWORD}
spring.data.redis.ssl=true
spring.data.redis.timeout=2000ms

# Redis connection pool security
spring.data.redis.lettuce.pool.max-active=8
spring.data.redis.lettuce.pool.max-idle=8
spring.data.redis.lettuce.pool.min-idle=0
spring.data.redis.lettuce.pool.max-wait=-1ms
```

#### Redis Command Restrictions
```redis
# Redis configuration (redis.conf)
# Disable dangerous commands
rename-command FLUSHDB ""
rename-command FLUSHALL ""
rename-command EVAL ""
rename-command DEBUG ""
rename-command CONFIG ""

# Enable protected mode
protected-mode yes

# Bind to specific interfaces only
bind 127.0.0.1 ::1
```

## Security Configuration

### Environment-Specific Configuration

#### Development Environment
```properties
# Development security settings
app.jwt.cookie.secure=false
app.jwt.cookie.same-site=Lax
spring.profiles.active=development

# Relaxed CORS for development
cors.allowed-origins=http://localhost:3000,http://localhost:3001

# Debug logging
logging.level.org.springframework.security=DEBUG
logging.level.area.server.AREA_Back.filter=DEBUG
```

#### Production Environment
```properties
# Production security settings
app.jwt.cookie.secure=true
app.jwt.cookie.same-site=Strict
app.jwt.cookie.domain=yourdomain.com
spring.profiles.active=production

# Strict CORS for production
cors.allowed-origins=https://yourdomain.com,https://app.yourdomain.com

# Minimal logging
logging.level.org.springframework.security=WARN
logging.level.area.server.AREA_Back=INFO
```

### Secrets Management

#### Environment Variables
```bash
# Authentication secrets
export JWT_ACCESS_SECRET=$(openssl rand -base64 32)
export JWT_REFRESH_SECRET=$(openssl rand -base64 32)
export ENCRYPTION_SECRET=$(openssl rand -base64 32)

# Database credentials
export DATABASE_URL="jdbc:postgresql://localhost:5432/area"
export DATABASE_USERNAME="area_app"
export DATABASE_PASSWORD=$(openssl rand -base64 24)

# Redis credentials
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export REDIS_PASSWORD=$(openssl rand -base64 16)

# OAuth credentials
export GITHUB_CLIENT_ID="your-github-client-id"
export GITHUB_CLIENT_SECRET="your-github-client-secret"
export GOOGLE_CLIENT_ID="your-google-client-id"
export GOOGLE_CLIENT_SECRET="your-google-client-secret"

# Webhook secrets
export GITHUB_WEBHOOK_SECRET=$(openssl rand -base64 32)
```

#### Docker Secrets (Production)
```yaml
# docker-compose.yml
version: '3.8'
services:
  app:
    image: area-backend:latest
    secrets:
      - jwt_access_secret
      - jwt_refresh_secret
      - database_password
    environment:
      JWT_ACCESS_SECRET_FILE: /run/secrets/jwt_access_secret
      JWT_REFRESH_SECRET_FILE: /run/secrets/jwt_refresh_secret
      DATABASE_PASSWORD_FILE: /run/secrets/database_password

secrets:
  jwt_access_secret:
    external: true
  jwt_refresh_secret:
    external: true
  database_password:
    external: true
```

## Threat Model

### Identified Threats and Mitigations

#### 1. Authentication Bypass
**Threat**: Attackers attempting to bypass authentication mechanisms.

**Mitigations**:
- JWT token validation on every request
- Token blacklisting in Redis
- Secure token storage in HTTP-only cookies
- Token rotation and short expiration times

#### 2. Cross-Site Scripting (XSS)
**Threat**: Injection of malicious scripts into web pages.

**Mitigations**:
- HTTP-only cookies prevent JavaScript access to tokens
- Content Security Policy headers
- Input validation and output encoding
- JSONB parameter validation

#### 3. Cross-Site Request Forgery (CSRF)
**Threat**: Unauthorized commands transmitted from a user's browser.

**Mitigations**:
- SameSite cookie attribute (Strict)
- CORS configuration restricting origins
- Double-submit cookie pattern (future enhancement)

#### 4. SQL Injection
**Threat**: Injection of malicious SQL code.

**Mitigations**:
- Parameterized queries with JPA
- Input validation annotations
- Prepared statements
- Database user privilege restrictions

#### 5. Sensitive Data Exposure
**Threat**: Unauthorized access to sensitive user data.

**Mitigations**:
- AES-GCM encryption for sensitive fields
- TLS encryption in transit
- Secure key derivation (PBKDF2)
- Data minimization principles

#### 6. Broken Access Control
**Threat**: Users accessing resources beyond their privileges.

**Mitigations**:
- Resource-level authorization checks
- Method-level security annotations
- User ownership validation
- Admin privilege separation

#### 7. Security Misconfiguration
**Threat**: Insecure default configurations.

**Mitigations**:
- Secure-by-default configuration
- Environment-specific settings
- Regular security configuration reviews
- Automated security testing

#### 8. Vulnerable Dependencies
**Threat**: Known vulnerabilities in third-party libraries.

**Mitigations**:
- Regular dependency updates
- Vulnerability scanning in CI/CD
- Security advisory monitoring
- Dependency license compliance

#### 9. Email Verification Attacks
**Threat**: Attacks targeting the email verification and password reset system.

**Mitigations**:
- Time-limited verification tokens (24 hours)
- Single-use tokens that expire after use
- Rate limiting on password reset requests
- Generic responses to prevent email enumeration
- Secure token generation with cryptographically strong random values
- HTTPS-only token transmission
- Account lockout after failed verification attempts

#### 10. Password Reset Attacks
**Threat**: Unauthorized password resets through token interception or prediction.

**Mitigations**:
- Very short token expiry (15 minutes for reset tokens)
- Single-use tokens invalidated immediately after use
- Secure token storage and transmission
- Rate limiting on reset requests per email/IP
- Session invalidation after password change
- Email confirmation before allowing reset
- Strong password requirements enforcement

## Security Best Practices

### Development Security Practices

#### Secure Coding Guidelines
1. **Input Validation**: Validate all input at API boundaries
2. **Output Encoding**: Encode all output to prevent injection
3. **Parameterized Queries**: Never concatenate SQL strings
4. **Error Handling**: Don't expose sensitive information in errors
5. **Logging Security**: Don't log sensitive data

#### Code Review Security Checklist
- [ ] Authentication and authorization properly implemented
- [ ] Input validation covers all parameters
- [ ] Sensitive data encrypted before storage
- [ ] Error messages don't leak information
- [ ] SQL queries use parameterization
- [ ] Dependencies are up-to-date
- [ ] Security headers configured correctly

### Deployment Security Practices

#### Container Security
```dockerfile
# Secure Dockerfile practices
FROM openjdk:21-jre-slim

# Create non-root user
RUN groupadd -r area && useradd -r -g area area

# Set working directory
WORKDIR /app

# Copy application
COPY --chown=area:area target/area-backend.jar app.jar

# Switch to non-root user
USER area

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### Network Security
- TLS 1.3 for all external communications
- Network segmentation between services
- Firewall rules restricting unnecessary ports
- VPN access for administrative functions

### Monitoring and Incident Response

#### Security Monitoring
```java
@Component
public class SecurityEventLogger {
    
    private static final Logger securityLogger = 
        LoggerFactory.getLogger("SECURITY");
    
    public void logAuthenticationSuccess(String userId, String source) {
        securityLogger.info("AUTH_SUCCESS user={} source={}", userId, source);
    }
    
    public void logAuthenticationFailure(String email, String source, String reason) {
        securityLogger.warn("AUTH_FAILURE email={} source={} reason={}", 
            email, source, reason);
    }
    
    public void logPrivilegeEscalation(String userId, String resource, String action) {
        securityLogger.error("PRIVILEGE_ESCALATION user={} resource={} action={}", 
            userId, resource, action);
    }
    
    public void logSuspiciousActivity(String userId, String activity, String details) {
        securityLogger.warn("SUSPICIOUS_ACTIVITY user={} activity={} details={}", 
            userId, activity, details);
    }
}
```

#### Incident Response Plan
1. **Detection**: Automated monitoring and alerting
2. **Analysis**: Security team investigation
3. **Containment**: Immediate threat mitigation
4. **Eradication**: Root cause elimination
5. **Recovery**: Service restoration
6. **Lessons Learned**: Process improvement

### Security Testing

#### Automated Security Testing
```yaml
# GitHub Actions security workflow
name: Security Scan
on: [push, pull_request]

jobs:
  security:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Run OWASP Dependency Check
        run: |
          ./gradlew dependencyCheckAnalyze
          
      - name: Run Static Analysis
        run: |
          ./gradlew sonarqube
          
      - name: Run Security Unit Tests
        run: |
          ./gradlew test -Dspring.profiles.active=security-test
```

#### Penetration Testing Checklist
- [ ] Authentication bypass attempts
- [ ] Authorization control testing  
- [ ] Input validation testing
- [ ] Session management testing
- [ ] Business logic flaw testing
- [ ] Infrastructure security testing