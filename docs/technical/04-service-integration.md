# Service Integration Architecture

## Table of Contents
- [Overview](#overview)
- [Service Framework](#service-framework)
- [Service Discovery](#service-discovery)
- [Authentication Management](#authentication-management)
- [GitHub Integration](#github-integration)
- [Webhook System](#webhook-system)
- [Service Cache](#service-cache)
- [API Reference](#api-reference)

## Overview

The Service Integration Architecture provides a unified framework for connecting with external services (GitHub, Slack, Google, etc.). It handles authentication, API interactions, webhooks, and event processing in a scalable and extensible manner.

## Service Framework

### Service Entity Model
```java
@Entity
@Table(name = "a_services", schema = "area")
public class Service {
    @Id
    private UUID id;
    
    @NotBlank
    @Column(unique = true)
    private String key;                   // Unique identifier (github, slack, google)
    
    @NotBlank
    private String name;                  // Display name
    
    @Enumerated(EnumType.STRING)
    private AuthType auth;                // OAUTH2, APIKEY, NONE
    
    private String docsUrl;               // Service documentation
    private String iconLightUrl;          // Light theme icon
    private String iconDarkUrl;           // Dark theme icon
    
    private Boolean isActive;             // Enabled/disabled state
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### Authentication Types
```java
public enum AuthType {
    OAUTH2,        // OAuth2 flow (GitHub, Google, Slack)
    APIKEY,        // API key authentication  
    NONE           // No authentication required
}
```

### Service Account Management
```java
@Entity
@Table(name = "a_service_accounts", schema = "area")
public class ServiceAccount {
    @Id
    private UUID id;
    
    @ManyToOne
    private User user;                    // Account owner
    
    @ManyToOne  
    private Service service;              // Associated service
    
    private String accountName;           // User-friendly name
    
    // Encrypted tokens
    private String accessTokenEnc;        // Encrypted access token
    private String refreshTokenEnc;       // Encrypted refresh token
    
    private LocalDateTime expiresAt;      // Token expiration
    private String scopes;                // OAuth scopes
    
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata; // Service-specific data
    
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

## Service Discovery

### Service Registry
The system maintains a dynamic registry of available services and their capabilities:

```java
@RestController
@RequestMapping("/api/services")
public class ServiceController {
    
    @GetMapping
    public ResponseEntity<Page<ServiceResponse>> getAllServices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir.equals("desc") 
            ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy));
        
        Page<Service> services = serviceRepository.findAll(pageable);
        Page<ServiceResponse> serviceResponses = services.map(this::convertToResponse);
        
        return ResponseEntity.ok(serviceResponses);
    }
    
    @GetMapping("/catalog/enabled")
    public ResponseEntity<List<ServiceResponse>> getEnabledServices() {
        List<Service> enabledServices = serviceRepository.findAllEnabledServices();
        List<ServiceResponse> responses = enabledServices.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
}
```

### About Endpoint
Provides comprehensive service information for client applications:

```java
@RestController
@RequestMapping("/")
public class AboutController {
    
    @GetMapping("/about.json")
    public ResponseEntity<Map<String, Object>> getAbout() {
        Map<String, Object> about = new HashMap<>();
        about.put("client", Map.of("host", "localhost:8080"));
        about.put("server", Map.of("current_time", LocalDateTime.now()));
        about.put("services", buildServicesInfo());
        
        return ResponseEntity.ok(about);
    }
    
    private List<Map<String, Object>> buildServicesInfo() {
        List<Service> services = serviceRepository.findAllEnabledServices();
        
        return services.stream().map(service -> {
            Map<String, Object> serviceInfo = new HashMap<>();
            serviceInfo.put("name", service.getName());
            serviceInfo.put("key", service.getKey());
            
            // Get available actions (triggers)
            List<ActionDefinition> actions = actionDefinitionRepository
                .findByServiceKey(service.getKey())
                .stream()
                .filter(ActionDefinition::getIsEventCapable)
                .collect(Collectors.toList());
            
            serviceInfo.put("actions", actions.stream()
                .map(this::mapActionDefinition)
                .collect(Collectors.toList()));
            
            // Get available reactions
            List<ActionDefinition> reactions = actionDefinitionRepository
                .findByServiceKey(service.getKey())
                .stream()
                .filter(ActionDefinition::getIsExecutable)
                .collect(Collectors.toList());
            
            serviceInfo.put("reactions", reactions.stream()
                .map(this::mapActionDefinition)
                .collect(Collectors.toList()));
            
            return serviceInfo;
        }).collect(Collectors.toList());
    }
}
```

## Authentication Management

### Service Account Service
```java
@Service
@Transactional
public class ServiceAccountService {
    
    private final ServiceAccountRepository serviceAccountRepository;
    private final TokenEncryptionService tokenEncryptionService;
    
    /**
     * Creates a new service account with OAuth2 tokens
     */
    public ServiceAccount createServiceAccount(User user, 
                                             Service service,
                                             String accountName,
                                             String accessToken,
                                             String refreshToken,
                                             LocalDateTime expiresAt,
                                             String scopes) {
        
        ServiceAccount account = new ServiceAccount();
        account.setUser(user);
        account.setService(service);
        account.setAccountName(accountName);
        account.setAccessTokenEnc(tokenEncryptionService.encrypt(accessToken));
        account.setRefreshTokenEnc(tokenEncryptionService.encrypt(refreshToken));
        account.setExpiresAt(expiresAt);
        account.setScopes(scopes);
        account.setIsActive(true);
        
        return serviceAccountRepository.save(account);
    }
    
    /**
     * Retrieves decrypted access token
     */
    public String getAccessToken(ServiceAccount account) {
        return tokenEncryptionService.decrypt(account.getAccessTokenEnc());
    }
    
    /**
     * Updates tokens after refresh
     */
    public void updateTokens(ServiceAccount account, 
                           String newAccessToken,
                           String newRefreshToken,
                           LocalDateTime newExpiresAt) {
        
        account.setAccessTokenEnc(tokenEncryptionService.encrypt(newAccessToken));
        account.setRefreshTokenEnc(tokenEncryptionService.encrypt(newRefreshToken));
        account.setExpiresAt(newExpiresAt);
        account.setUpdatedAt(LocalDateTime.now());
        
        serviceAccountRepository.save(account);
    }
}
```

### Token Encryption Service
```java
@Service
public class TokenEncryptionService {
    
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    
    @Value("${app.encryption.secret}")
    private String encryptionSecret;
    
    /**
     * Encrypts sensitive token data
     */
    public String encrypt(String plaintext) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(
                encryptionSecret.getBytes(), "AES");
            
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(
                GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);
            
            byte[] cipherText = cipher.doFinal(plaintext.getBytes());
            
            // Combine IV and ciphertext
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, encryptedWithIv, GCM_IV_LENGTH, cipherText.length);
            
            return Base64.getEncoder().encodeToString(encryptedWithIv);
            
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    /**
     * Decrypts sensitive token data
     */
    public String decrypt(String encryptedText) {
        try {
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedText);
            
            // Extract IV and ciphertext
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            
            System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, cipherText, 0, cipherText.length);
            
            SecretKeySpec secretKey = new SecretKeySpec(
                encryptionSecret.getBytes(), "AES");
            
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(
                GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);
            
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText);
            
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
```

## GitHub Integration

### GitHub Service Implementation
```java
@Service
@ConditionalOnProperty(name = "github.enabled", havingValue = "true", matchIfMissing = true)
public class GitHubActionService {
    
    private final ServiceAccountService serviceAccountService;
    private final WebClient webClient;
    
    /**
     * Creates a GitHub issue
     */
    public Map<String, Object> createIssue(ServiceAccount serviceAccount,
                                          String repository,
                                          String title,
                                          String body) {
        
        String accessToken = serviceAccountService.getAccessToken(serviceAccount);
        
        Map<String, Object> issueRequest = Map.of(
            "title", title,
            "body", body
        );
        
        try {
            WebClient.ResponseSpec response = webClient
                .post()
                .uri("/repos/{repo}/issues", repository)
                .header("Authorization", "token " + accessToken)
                .header("Accept", "application/vnd.github.v3+json")
                .bodyValue(issueRequest)
                .retrieve();
            
            return response.bodyToMono(Map.class).block();
            
        } catch (WebClientResponseException e) {
            throw new ServiceIntegrationException(
                "Failed to create GitHub issue: " + e.getResponseBodyAsString(), e);
        }
    }
    
    /**
     * Lists repository issues
     */
    public List<Map<String, Object>> listIssues(ServiceAccount serviceAccount,
                                               String repository,
                                               String state) {
        
        String accessToken = serviceAccountService.getAccessToken(serviceAccount);
        
        try {
            WebClient.ResponseSpec response = webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/repos/{repo}/issues")
                    .queryParam("state", state)
                    .build(repository))
                .header("Authorization", "token " + accessToken)
                .header("Accept", "application/vnd.github.v3+json")
                .retrieve();
            
            return response.bodyToMono(List.class).block();
            
        } catch (WebClientResponseException e) {
            throw new ServiceIntegrationException(
                "Failed to list GitHub issues: " + e.getResponseBodyAsString(), e);
        }
    }
}
```

### GitHub Webhook Processing
```java
@RestController
@RequestMapping("/api/webhooks/github")
@Slf4j
public class GitHubWebhookController {
    
    private final WebhookEventProcessingService processingService;
    private final WebhookSignatureValidator signatureValidator;
    
    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {
        
        log.info("Received GitHub webhook: event={}, delivery={}", eventType, deliveryId);
        
        try {
            // Validate webhook signature
            if (!signatureValidator.validateGitHubSignature(payload, signature)) {
                log.warn("Invalid GitHub webhook signature for delivery: {}", deliveryId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid signature");
            }
            
            // Process webhook event
            processingService.processGitHubWebhook(eventType, deliveryId, payload);
            
            return ResponseEntity.ok("Webhook processed successfully");
            
        } catch (Exception e) {
            log.error("Error processing GitHub webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Webhook processing failed");
        }
    }
}
```

### GitHub Event Processing
```java
@Service
public class WebhookEventProcessingService {
    
    private final RedisEventService redisEventService;
    private final WebhookDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;
    
    /**
     * Processes GitHub webhook events
     */
    public void processGitHubWebhook(String eventType, String deliveryId, String payload) {
        try {
            Map<String, Object> eventData = objectMapper.readValue(payload, Map.class);
            
            // Check for duplicate events
            if (deduplicationService.isDuplicateEvent("github", deliveryId)) {
                log.debug("Duplicate GitHub webhook ignored: {}", deliveryId);
                return;
            }
            
            // Create standardized event
            Map<String, Object> standardizedEvent = Map.of(
                "service", "github",
                "event_type", eventType,
                "delivery_id", deliveryId,
                "timestamp", Instant.now().toString(),
                "data", eventData
            );
            
            // Send to Redis stream for processing
            redisEventService.publishEvent("github." + eventType, standardizedEvent);
            
            // Mark as processed
            deduplicationService.markEventProcessed("github", deliveryId);
            
        } catch (Exception e) {
            log.error("Failed to process GitHub webhook: {}", e.getMessage(), e);
            throw new WebhookProcessingException("Webhook processing failed", e);
        }
    }
}
```

## Webhook System

### Webhook Signature Validation
```java
@Service
public class WebhookSignatureValidator {
    
    private static final String GITHUB_SIGNATURE_PREFIX = "sha256=";
    
    @Value("${github.webhook.secret}")
    private String githubWebhookSecret;
    
    /**
     * Validates GitHub webhook signature
     */
    public boolean validateGitHubSignature(String payload, String signature) {
        if (signature == null || !signature.startsWith(GITHUB_SIGNATURE_PREFIX)) {
            return false;
        }
        
        try {
            String expectedSignature = GITHUB_SIGNATURE_PREFIX + 
                calculateHmacSha256(payload, githubWebhookSecret);
            
            return MessageDigest.isEqual(
                signature.getBytes(),
                expectedSignature.getBytes()
            );
            
        } catch (Exception e) {
            log.error("Error validating GitHub signature: {}", e.getMessage());
            return false;
        }
    }
    
    private String calculateHmacSha256(String data, String secret) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        mac.init(secretKeySpec);
        
        byte[] hash = mac.doFinal(data.getBytes());
        return bytesToHex(hash);
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
```

### Webhook Deduplication
```java
@Service
public class WebhookDeduplicationService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String DEDUP_KEY_PREFIX = "webhook:dedup:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);
    
    /**
     * Checks if webhook event is duplicate
     */
    public boolean isDuplicateEvent(String service, String deliveryId) {
        String key = DEDUP_KEY_PREFIX + service + ":" + deliveryId;
        return redisTemplate.hasKey(key);
    }
    
    /**
     * Marks webhook event as processed
     */
    public void markEventProcessed(String service, String deliveryId) {
        String key = DEDUP_KEY_PREFIX + service + ":" + deliveryId;
        redisTemplate.opsForValue().set(key, "processed", DEDUP_TTL);
    }
}
```

## Service Cache

### Service Cache Implementation
```java
@Service
public class ServiceCacheService {
    
    private final ServiceRepository serviceRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String SERVICE_CACHE_KEY = "services:enabled";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    
    /**
     * Gets enabled services with caching
     */
    @Cacheable(value = "services", key = "'enabled'")
    public List<Service> getEnabledServices() {
        return serviceRepository.findAllEnabledServices();
    }
    
    /**
     * Invalidates service cache
     */
    @CacheEvict(value = "services", allEntries = true)
    public void invalidateServiceCache() {
        log.info("Service cache invalidated");
    }
    
    /**
     * Refreshes service cache
     */
    @Scheduled(fixedDelay = 1800000) // 30 minutes
    public void refreshServiceCache() {
        invalidateServiceCache();
        getEnabledServices();
        log.info("Service cache refreshed");
    }
}
```

### Redis Cache Configuration
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
}
```

## Event Stream Processing

### Redis Event Service
```java
@Service
public class RedisEventService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisConfig redisConfig;
    
    /**
     * Publishes event to Redis stream
     */
    public void publishEvent(String eventKey, Map<String, Object> eventData) {
        try {
            Map<String, Object> streamData = Map.of(
                "event_key", eventKey,
                "timestamp", Instant.now().toString(),
                "data", eventData
            );
            
            redisTemplate.opsForStream().add(
                redisConfig.getAreasEventsStream(),
                streamData
            );
            
            log.debug("Published event to stream: {}", eventKey);
            
        } catch (Exception e) {
            log.error("Failed to publish event to Redis stream: {}", e.getMessage(), e);
            throw new EventPublishingException("Event publishing failed", e);
        }
    }
    
    /**
     * Initializes Redis stream and consumer group
     */
    public void initializeStream() {
        try {
            // Create stream if it doesn't exist
            if (!redisTemplate.hasKey(redisConfig.getAreasEventsStream())) {
                redisTemplate.opsForStream().add(
                    redisConfig.getAreasEventsStream(),
                    Collections.singletonMap("init", "stream")
                );
            }
            
            // Create consumer group if it doesn't exist
            try {
                redisTemplate.opsForStream().createGroup(
                    redisConfig.getAreasEventsStream(),
                    redisConfig.getAreasConsumerGroup()
                );
            } catch (Exception e) {
                // Consumer group already exists
                log.debug("Consumer group already exists: {}", 
                    redisConfig.getAreasConsumerGroup());
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize Redis stream: {}", e.getMessage(), e);
        }
    }
}
```

## API Reference

### Service Management Endpoints

#### Get All Services
```http
GET /api/services?page=0&size=20&sortBy=name&sortDir=asc
```

#### Get Enabled Services
```http
GET /api/services/catalog/enabled
```

#### Get Service Details
```http
GET /api/services/{serviceId}
```

#### About Information
```http
GET /about.json
```

### Service Account Endpoints

#### Create Service Account
```http
POST /api/service-accounts
Content-Type: application/json

{
    "serviceId": "service-uuid",
    "accountName": "My GitHub Account",
    "accessToken": "encrypted-token",
    "refreshToken": "encrypted-refresh-token",
    "expiresAt": "2024-12-31T23:59:59",
    "scopes": "repo,issues"
}
```

#### Get User Service Accounts
```http
GET /api/service-accounts/user/{userId}
```

#### Update Service Account
```http
PUT /api/service-accounts/{accountId}
Content-Type: application/json

{
    "accountName": "Updated Account Name",
    "isActive": true
}
```

#### Delete Service Account
```http
DELETE /api/service-accounts/{accountId}
```

### Webhook Endpoints

#### GitHub Webhook
```http
POST /api/webhooks/github
X-GitHub-Event: issues
X-GitHub-Delivery: 12345678-1234-1234-1234-123456789abc
X-Hub-Signature-256: sha256=hash
Content-Type: application/json

{
    "action": "opened",
    "issue": {
        "title": "Bug report",
        "body": "Description of the bug"
    },
    "repository": {
        "full_name": "owner/repo"
    }
}
```

## Error Handling

### Service Integration Errors
```json
{
    "error": "SERVICE_INTEGRATION_ERROR",
    "message": "Failed to connect to GitHub API",
    "details": {
        "service": "github",
        "operation": "create_issue",
        "httpStatus": 401,
        "errorCode": "UNAUTHORIZED"
    }
}
```

### Webhook Processing Errors
```json
{
    "error": "WEBHOOK_PROCESSING_ERROR",
    "message": "Invalid webhook signature",
    "details": {
        "service": "github",
        "deliveryId": "12345678-1234-1234-1234-123456789abc",
        "eventType": "issues"
    }
}
```

## Monitoring & Observability

### Metrics
- Service API response times
- Webhook processing rates
- Authentication success/failure rates
- Cache hit/miss ratios
- Stream processing latency

### Health Checks
```java
@Component
public class ServiceHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        // Check GitHub API connectivity
        // Check Redis connectivity
        // Check database connectivity
        // Return aggregated health status
    }
}
```

### Alerting
- Service API failures
- Webhook processing failures
- Token expiration warnings
- Stream processing delays
- Cache invalidation events