# How to Contribute to AREA Backend

## Table of Contents
- [Welcome Contributors](#welcome-contributors)
- [Getting Started](#getting-started)
- [Development Environment Setup](#development-environment-setup)
- [Project Structure](#project-structure)
- [Contribution Workflow](#contribution-workflow)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Documentation Guidelines](#documentation-guidelines)
- [Pull Request Process](#pull-request-process)
- [Issue Guidelines](#issue-guidelines)
- [Security Guidelines](#security-guidelines)
- [Community Guidelines](#community-guidelines)

## Welcome Contributors

Thank you for your interest in contributing to the AREA (Action REAction) Backend project! AREA is an automation platform that allows users to create powerful workflows by connecting various services and applications. This document will guide you through the contribution process and help you become an effective contributor to our project.

### What is AREA?
AREA is a modern automation platform built with Spring Boot that enables users to:
- Create automated workflows between different services (GitHub, Gmail, Weather, etc.)
- Define custom actions and reactions
- Manage complex automation scenarios
- Integrate with OAuth2 providers for secure authentication

### Ways to Contribute
- **Bug Reports**: Help us identify and fix issues
- **Feature Requests**: Suggest new features and improvements
- **Code Contributions**: Implement new features, fix bugs, or improve performance
- **Documentation**: Improve or translate documentation
- **Testing**: Help with manual testing or write automated tests
- **Code Review**: Review pull requests from other contributors

## Getting Started

### Prerequisites
Before you start contributing, make sure you have:
- **Java 21 or higher** installed
- **Docker and Docker Compose** for local development
- **Git** for version control
- **An IDE** (IntelliJ IDEA, VS Code, or Eclipse recommended)
- **Basic knowledge** of Spring Boot, REST APIs, and Java

### First Time Setup

#### 1. Fork and Clone the Repository
```bash
# Fork the repository on GitHub
# Then clone your fork
git clone https://github.com/YOUR_USERNAME/AREA_Back.git
cd AREA_Back

# Add the original repository as upstream
git remote add upstream https://github.com/ORIGINAL_OWNER/AREA_Back.git
```

#### 2. Install Dependencies
```bash
# Make gradlew executable
chmod +x gradlew

# Download dependencies
./gradlew dependencies
```

#### 3. Set Up Environment
```bash
# Copy environment template
cp .env.template .env

# Edit .env with your configuration
# Minimum required variables:
DATABASE_URL=jdbc:postgresql://localhost:5432/area_dev
DATABASE_USERNAME=area_user
DATABASE_PASSWORD=dev_password
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_ACCESS_SECRET=your_jwt_access_secret_32_characters
JWT_REFRESH_SECRET=your_jwt_refresh_secret_32_characters
```

#### 4. Start Development Environment
```bash
# Start PostgreSQL and Redis with Docker
docker-compose -f Docker/docker-compose.dev.yml up -d postgres redis

# Or start all services including the application
docker-compose -f Docker/docker-compose.dev.yml up -d
```

#### 5. Verify Setup
```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Start the application (if not using Docker)
./gradlew bootRun
```

The application should be accessible at:
- **API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/actuator/health

## Development Environment Setup

### IDE Configuration

#### IntelliJ IDEA Setup
1. **Import Project**: Open IntelliJ and import the project as a Gradle project
2. **Java Version**: Set Project SDK to Java 21
3. **Code Style**: Import the code style configuration from `config/intellij-code-style.xml`
4. **Plugins**: Install recommended plugins:
   - Spring Boot
   - Docker
   - CheckStyle-IDEA
   - SonarLint

#### VS Code Setup
1. **Extensions**: Install recommended extensions:
   - Extension Pack for Java
   - Spring Boot Extension Pack
   - Docker
   - REST Client
2. **Settings**: Configure workspace settings in `.vscode/settings.json`

### Database Setup

#### Local PostgreSQL
```bash
# Create development database
createdb area_dev

# Run migrations
./gradlew flywayMigrate

# Check migration status
./gradlew flywayInfo
```

#### Test Database
```bash
# Create test database
createdb area_test

# Set test environment
export SPRING_PROFILES_ACTIVE=test

# Run tests with database
./gradlew test
```

### Redis Setup
```bash
# Start Redis locally
redis-server

# Or use Docker
docker run -d -p 6379:6379 redis:7-alpine
```

## Project Structure

Understanding the project structure will help you navigate and contribute effectively:

```
AREA_Back/
├── src/
│   ├── main/
│   │   ├── java/com/area/
│   │   │   ├── AreaBackApplication.java          # Main application class
│   │   │   ├── config/                           # Configuration classes
│   │   │   │   ├── SecurityConfig.java          # Security configuration
│   │   │   │   ├── RedisConfig.java             # Redis configuration
│   │   │   │   └── SwaggerConfig.java           # API documentation config
│   │   │   ├── controller/                      # REST API controllers
│   │   │   │   ├── AreaController.java          # Area management endpoints
│   │   │   │   ├── AuthController.java          # Authentication endpoints
│   │   │   │   └── ServiceController.java       # Service integration endpoints
│   │   │   ├── service/                         # Business logic layer
│   │   │   │   ├── AreaService.java             # Area business logic
│   │   │   │   ├── AuthService.java             # Authentication logic
│   │   │   │   └── JwtService.java              # JWT token management
│   │   │   ├── entity/                          # JPA entities
│   │   │   │   ├── User.java                    # User entity
│   │   │   │   ├── Area.java                    # Area entity
│   │   │   │   └── Service.java                 # Service entity
│   │   │   ├── repository/                      # Data access layer
│   │   │   │   ├── UserRepository.java          # User data access
│   │   │   │   ├── AreaRepository.java          # Area data access
│   │   │   │   └── ServiceRepository.java       # Service data access
│   │   │   ├── dto/                             # Data Transfer Objects
│   │   │   ├── exception/                       # Custom exceptions
│   │   │   ├── security/                        # Security components
│   │   │   └── worker/                          # Background workers
│   │   └── resources/
│   │       ├── application.properties           # Application configuration
│   │       ├── db/migration/                    # Flyway migrations
│   │       └── static/                          # Static resources
│   └── test/
│       └── java/com/area/                       # Test classes
├── docs/                                        # Documentation
│   ├── technical/                               # Technical documentation
│   └── api/                                     # API documentation
├── config/                                      # Configuration files
├── Docker/                                      # Docker configurations
└── gradle/                                      # Gradle wrapper
```

### Key Components

#### Controllers (`controller/`)
- Handle HTTP requests and responses
- Validate input parameters
- Delegate business logic to services
- Return appropriate HTTP status codes

#### Services (`service/`)
- Implement business logic
- Handle transactions
- Interact with repositories
- Coordinate between different components

#### Entities (`entity/`)
- Represent database tables
- Define relationships between entities
- Include validation annotations

#### Repositories (`repository/`)
- Provide data access abstraction
- Extend Spring Data JPA repositories
- Include custom query methods

## Contribution Workflow

### Branch Strategy
We use **Git Flow** for branch management:

- **main**: Production-ready code
- **develop**: Integration branch for features
- **feature/\***: New features or enhancements
- **bugfix/\***: Bug fixes
- **hotfix/\***: Critical production fixes
- **release/\***: Release preparation

### Workflow Steps

#### 1. Create a Feature Branch
```bash
# Sync with upstream
git fetch upstream
git checkout develop
git merge upstream/develop

# Create feature branch
git checkout -b feature/your-feature-name

# Or for bug fixes
git checkout -b bugfix/issue-number-description
```

#### 2. Make Your Changes
```bash
# Make your changes
# Add tests for new functionality
# Update documentation if needed

# Check your changes
./gradlew clean build
./gradlew test
```

#### 3. Commit Your Changes
```bash
# Stage your changes
git add .

# Commit with descriptive message
git commit -m "feat: add user profile management functionality

- Add ProfileController with CRUD operations
- Implement ProfileService with validation
- Add ProfileDto for data transfer
- Include comprehensive unit tests
- Update API documentation

Closes #123"
```

#### 4. Push and Create Pull Request
```bash
# Push to your fork
git push origin feature/your-feature-name

# Create pull request on GitHub
# Fill out the pull request template
# Request review from maintainers
```

### Commit Message Format
We follow the **Conventional Commits** specification:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Examples:**
```
feat(auth): add OAuth2 Google integration

Add Google OAuth2 provider support for user authentication.
Includes login/callback endpoints and token management.

Closes #45

fix(area): resolve null pointer exception in area execution

The area execution was failing when action parameters were null.
Added null checks and appropriate error handling.

Fixes #67

docs(api): update authentication endpoint documentation

Add examples for OAuth2 flows and improve error response descriptions.
```

## Coding Standards

### Java Code Style

#### General Principles
- Follow **Java naming conventions**
- Use **meaningful variable and method names**
- Keep methods **small and focused** (max 20-30 lines)
- Minimize **cyclomatic complexity**
- Follow **SOLID principles**

#### Code Formatting
```java
// Class naming: PascalCase
public class UserService {
    
    // Constants: UPPER_SNAKE_CASE
    private static final String DEFAULT_ROLE = "USER";
    
    // Fields: camelCase, private with getters/setters
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    // Constructor injection preferred
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    // Method naming: camelCase, descriptive
    public UserDto createNewUser(CreateUserRequest request) {
        // Validate input
        validateCreateUserRequest(request);
        
        // Business logic
        User user = User.builder()
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .role(DEFAULT_ROLE)
            .build();
        
        // Save and return
        User savedUser = userRepository.save(user);
        return UserMapper.toDto(savedUser);
    }
    
    // Private helper methods
    private void validateCreateUserRequest(CreateUserRequest request) {
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        // More validation...
    }
}
```

#### Documentation
```java
/**
 * Service class for managing user operations including creation, authentication,
 * and profile management.
 * 
 * @author Your Name
 * @since 1.0.0
 */
@Service
@Transactional
public class UserService {
    
    /**
     * Creates a new user account with the provided details.
     * 
     * @param request the user creation request containing email, password, and optional profile data
     * @return UserDto containing the created user information
     * @throws UserAlreadyExistsException if a user with the email already exists
     * @throws ValidationException if the request data is invalid
     */
    public UserDto createNewUser(CreateUserRequest request) {
        // Implementation...
    }
}
```

### Spring Boot Best Practices

#### Dependency Injection
```java
// Preferred: Constructor injection
@Service
public class AreaService {
    private final AreaRepository areaRepository;
    private final UserService userService;
    
    public AreaService(AreaRepository areaRepository, UserService userService) {
        this.areaRepository = areaRepository;
        this.userService = userService;
    }
}

// Avoid: Field injection
@Service
public class AreaService {
    @Autowired // Avoid this
    private AreaRepository areaRepository;
}
```

#### Exception Handling
```java
// Global exception handler
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.badRequest().body(error);
    }
}

// Custom exceptions
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String email) {
        super("User not found with email: " + email);
    }
}
```

#### Configuration
```java
// Configuration classes
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
    
    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}

// Configuration properties
@ConfigurationProperties(prefix = "app.jwt")
@Data
public class JwtProperties {
    private String accessSecret;
    private String refreshSecret;
    private Duration accessTokenExpiry = Duration.ofMinutes(15);
    private Duration refreshTokenExpiry = Duration.ofDays(30);
}
```

### Database Guidelines

#### Entity Design
```java
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID id;
    
    @Column(name = "email", nullable = false, unique = true)
    @Email
    private String email;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Relationships
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Area> areas = new ArrayList<>();
}
```

#### Repository Design
```java
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    Optional<User> findByEmail(String email);
    
    @Query("SELECT u FROM User u WHERE u.createdAt >= :since")
    List<User> findUsersCreatedSince(@Param("since") LocalDateTime since);
    
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime WHERE u.id = :userId")
    void updateLastLoginTime(@Param("userId") UUID userId, @Param("loginTime") LocalDateTime loginTime);
}
```

### API Design Guidelines

#### REST Controller Design
```java
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "Operations for managing user accounts")
@Validated
public class UserController {
    
    private final UserService userService;
    
    public UserController(UserService userService) {
        this.userService = userService;
    }
    
    @PostMapping
    @Operation(summary = "Create new user", description = "Creates a new user account")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "409", description = "User already exists")
    })
    public ResponseEntity<UserDto> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        UserDto user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
    
    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID", description = "Retrieves user information by user ID")
    public ResponseEntity<UserDto> getUser(
            @PathVariable @Parameter(description = "User ID") UUID userId) {
        UserDto user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }
}
```

#### DTO Design
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]",
             message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character")
    private String password;
    
    @Size(max = 50, message = "First name must not exceed 50 characters")
    private String firstName;
    
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String lastName;
}
```

## Testing Guidelines

### Testing Strategy
We follow the **Testing Pyramid** approach:
- **Unit Tests** (70%): Fast, isolated tests for individual components
- **Integration Tests** (20%): Test component interactions
- **End-to-End Tests** (10%): Full application workflow tests

### Unit Testing

#### Service Layer Tests
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    @DisplayName("Should create user successfully with valid data")
    void shouldCreateUserSuccessfully() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
            .email("test@example.com")
            .password("Password123!")
            .firstName("John")
            .lastName("Doe")
            .build();
        
        User savedUser = User.builder()
            .id(UUID.randomUUID())
            .email(request.getEmail())
            .passwordHash("hashed_password")
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .build();
        
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        
        // When
        UserDto result = userService.createUser(request);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(request.getEmail());
        assertThat(result.getFirstName()).isEqualTo(request.getFirstName());
        assertThat(result.getLastName()).isEqualTo(request.getLastName());
        
        verify(userRepository).save(any(User.class));
        verify(passwordEncoder).encode(request.getPassword());
    }
    
    @Test
    @DisplayName("Should throw exception when user already exists")
    void shouldThrowExceptionWhenUserAlreadyExists() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
            .email("existing@example.com")
            .password("Password123!")
            .build();
        
        when(userRepository.findByEmail(request.getEmail()))
            .thenReturn(Optional.of(new User()));
        
        // When & Then
        assertThatThrownBy(() -> userService.createUser(request))
            .isInstanceOf(UserAlreadyExistsException.class)
            .hasMessageContaining("existing@example.com");
        
        verify(userRepository, never()).save(any(User.class));
    }
}
```

#### Repository Tests
```java
@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:testdb"
})
class UserRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    @DisplayName("Should find user by email")
    void shouldFindUserByEmail() {
        // Given
        User user = User.builder()
            .email("test@example.com")
            .passwordHash("hashed_password")
            .build();
        entityManager.persistAndFlush(user);
        
        // When
        Optional<User> found = userRepository.findByEmail("test@example.com");
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }
}
```

### Integration Testing

#### Controller Integration Tests
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserControllerIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private UserRepository userRepository;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Test
    @DisplayName("Should create user via REST API")
    void shouldCreateUserViaRestApi() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
            .email("integration@example.com")
            .password("Password123!")
            .firstName("Integration")
            .lastName("Test")
            .build();
        
        // When
        ResponseEntity<UserDto> response = restTemplate.postForEntity(
            "/api/v1/users", request, UserDto.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmail()).isEqualTo(request.getEmail());
        
        // Verify in database
        Optional<User> savedUser = userRepository.findByEmail(request.getEmail());
        assertThat(savedUser).isPresent();
    }
}
```

### Test Utilities

#### Test Data Builders
```java
public class TestDataBuilder {
    
    public static User.UserBuilder defaultUser() {
        return User.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .passwordHash("hashed_password")
            .firstName("John")
            .lastName("Doe")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now());
    }
    
    public static Area.AreaBuilder defaultArea() {
        return Area.builder()
            .id(UUID.randomUUID())
            .name("Test Area")
            .description("Test area description")
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now());
    }
}
```

#### Custom Test Annotations
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
public @interface IntegrationTest {
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Test
@DisplayName
public @interface UnitTest {
    String value();
}
```

### Test Coverage
- Maintain **minimum 80% code coverage**
- Focus on **branch coverage**, not just line coverage
- Use **JaCoCo** for coverage reporting
- Exclude DTOs, entities, and configuration classes from coverage requirements

```bash
# Run tests with coverage
./gradlew test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

## Documentation Guidelines

### Code Documentation

#### JavaDoc Standards
```java
/**
 * Service responsible for managing user authentication and authorization.
 * <p>
 * This service handles user login, logout, token generation and validation,
 * and integration with OAuth2 providers.
 * </p>
 * 
 * @author Your Name
 * @version 1.0.0
 * @since 2024-01-01
 */
@Service
public class AuthService {
    
    /**
     * Authenticates a user with email and password.
     * <p>
     * This method validates the provided credentials against the database
     * and generates JWT tokens if authentication is successful.
     * </p>
     * 
     * @param email the user's email address (must not be null or empty)
     * @param password the user's plaintext password (must not be null or empty)
     * @return AuthResponse containing access and refresh tokens
     * @throws AuthenticationException if credentials are invalid
     * @throws UserNotFoundException if user doesn't exist
     * @throws AccountLockedException if user account is locked
     * 
     * @see #generateTokens(User)
     * @see #validateCredentials(String, String)
     */
    public AuthResponse authenticate(String email, String password) {
        // Implementation...
    }
}
```

### API Documentation

#### OpenAPI Annotations
```java
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User authentication and authorization endpoints")
public class AuthController {
    
    @PostMapping("/login")
    @Operation(
        summary = "Authenticate user",
        description = "Authenticates a user with email and password, returning JWT tokens",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Authentication successful",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AuthResponse.class),
                    examples = @ExampleObject(
                        name = "Successful authentication",
                        value = """
                        {
                          "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                          "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                          "user": {
                            "id": "123e4567-e89b-12d3-a456-426614174000",
                            "email": "user@example.com",
                            "firstName": "John",
                            "lastName": "Doe"
                          }
                        }
                        """
                    )
                )
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Invalid credentials",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)
                )
            )
        }
    )
    public ResponseEntity<AuthResponse> login(
            @Parameter(description = "User login credentials", required = true)
            @Valid @RequestBody LoginRequest request) {
        // Implementation...
    }
}
```

### README Updates
When adding new features, update relevant README sections:
- **Features**: Add new functionality descriptions
- **API Endpoints**: Document new endpoints
- **Configuration**: Add new environment variables
- **Dependencies**: Update if new dependencies are added

### Technical Documentation
For significant changes, create or update technical documentation:
- **Architecture decisions**: Document in `docs/technical/`
- **API changes**: Update API documentation
- **Database changes**: Document schema changes
- **Security changes**: Update security documentation

## Pull Request Process

### Pull Request Template
```markdown
## Description
Brief description of the changes and the problem they solve.

## Type of Change
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update

## How Has This Been Tested?
- [ ] Unit tests
- [ ] Integration tests
- [ ] Manual testing

## Checklist
- [ ] My code follows the project's coding standards
- [ ] I have performed a self-review of my own code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] I have made corresponding changes to the documentation
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes
- [ ] Any dependent changes have been merged and published

## Screenshots (if applicable)
Add screenshots for UI changes.

## Additional Notes
Any additional information that reviewers should know.
```

### Review Process

#### Before Submitting
1. **Self-review**: Review your own changes thoroughly
2. **Test locally**: Ensure all tests pass
3. **Check formatting**: Run code formatting and linting
4. **Update documentation**: Update relevant documentation
5. **Rebase**: Rebase your branch on the latest develop

```bash
# Pre-submission checklist
./gradlew clean build
./gradlew test
./gradlew checkstyleMain
./gradlew checkstyleTest

# Rebase on develop
git fetch upstream
git rebase upstream/develop
```

#### Review Criteria
Reviewers will check for:
- **Code quality**: Follows coding standards and best practices
- **Functionality**: Changes work as intended
- **Tests**: Adequate test coverage for new code
- **Documentation**: Proper documentation for new features
- **Security**: No security vulnerabilities introduced
- **Performance**: No significant performance degradation

#### Addressing Review Comments
1. **Read carefully**: Understand all review comments
2. **Ask questions**: If comments are unclear, ask for clarification
3. **Make changes**: Address all valid concerns
4. **Respond**: Reply to comments explaining your changes
5. **Re-request review**: Request review after making changes

### Merge Requirements
- ✅ At least **2 approving reviews** from maintainers
- ✅ All **CI checks passing**
- ✅ **Conflicts resolved**
- ✅ **Branch up to date** with target branch
- ✅ All **review comments addressed**

## Issue Guidelines

### Reporting Bugs

#### Bug Report Template
```markdown
## Bug Description
A clear and concise description of what the bug is.

## Steps to Reproduce
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

## Expected Behavior
A clear and concise description of what you expected to happen.

## Actual Behavior
A clear and concise description of what actually happened.

## Screenshots
If applicable, add screenshots to help explain your problem.

## Environment
- OS: [e.g. Ubuntu 20.04]
- Java Version: [e.g. 21.0.1]
- Application Version: [e.g. 1.2.3]
- Browser: [e.g. Chrome 120.0]

## Additional Context
Add any other context about the problem here.

## Logs
```
Include relevant log snippets here
```

### Feature Requests

#### Feature Request Template
```markdown
## Feature Description
A clear and concise description of the feature you'd like to see.

## Problem Statement
Describe the problem this feature would solve.

## Proposed Solution
Describe how you envision this feature working.

## Alternatives Considered
Describe any alternative solutions you've considered.

## Additional Context
Add any other context, mockups, or examples about the feature request.

## Implementation Notes
If you have ideas about implementation, share them here.
```

### Issue Labels
We use the following labels to categorize issues:
- **bug**: Something isn't working
- **enhancement**: New feature or request
- **documentation**: Improvements or additions to documentation
- **good first issue**: Good for newcomers
- **help wanted**: Extra attention is needed
- **question**: Further information is requested
- **wontfix**: This will not be worked on
- **duplicate**: This issue or pull request already exists
- **priority: high/medium/low**: Issue priority
- **size: S/M/L/XL**: Estimated effort required

## Security Guidelines

### Security Best Practices

#### Authentication & Authorization
- **Never log sensitive data** (passwords, tokens, personal data)
- **Use parameterized queries** to prevent SQL injection
- **Validate all inputs** on both client and server side
- **Implement proper CORS** configuration
- **Use HTTPS** for all communications
- **Follow JWT best practices** for token handling

#### Secure Coding
```java
// Good: Parameterized query
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);

// Bad: String concatenation (SQL injection risk)
// @Query("SELECT u FROM User u WHERE u.email = '" + email + "'")

// Good: Input validation
@Valid @RequestBody CreateUserRequest request

// Good: Password hashing
String hashedPassword = passwordEncoder.encode(rawPassword);

// Bad: Plaintext password storage
// user.setPassword(rawPassword);
```

#### Environment Variables
- **Never commit secrets** to version control
- **Use environment variables** for sensitive configuration
- **Rotate secrets regularly**
- **Use strong, random passwords**

### Reporting Security Issues
- **Do not** create public GitHub issues for security vulnerabilities
- **Email security issues** to security@yourproject.com
- **Include detailed steps** to reproduce the vulnerability
- **Wait for confirmation** before public disclosure

### Security Testing
- **Run security scans** with tools like OWASP ZAP
- **Keep dependencies updated** to avoid known vulnerabilities
- **Test authentication and authorization** thoroughly
- **Validate input sanitization**

## Community Guidelines

### Code of Conduct
We are committed to providing a welcoming and inspiring community for all. Please read our full [Code of Conduct](CODE_OF_CONDUCT.md).

#### Our Standards
- **Be respectful** and inclusive
- **Use welcoming language**
- **Be collaborative**
- **Focus on what's best** for the community
- **Show empathy** towards other community members

#### Unacceptable Behavior
- **Harassment** of any kind
- **Discriminatory language** or actions
- **Personal attacks**
- **Publishing private information** without permission
- **Other conduct** that could reasonably be considered inappropriate

### Communication Channels
- **GitHub Issues**: Bug reports and feature requests
- **GitHub Discussions**: General questions and discussions
- **Discord/Slack**: Real-time chat (if applicable)
- **Email**: Security issues and private matters

### Getting Help
- **Check existing issues**: Your question might already be answered
- **Read documentation**: Check our comprehensive docs
- **Ask in discussions**: For general questions
- **Be specific**: Provide context and details when asking for help

### Recognition
We recognize contributors in various ways:
- **Contributors list**: Listed in README.md
- **Release notes**: Major contributions mentioned
- **GitHub badges**: Achievement recognition
- **Community highlights**: Featured in newsletters/blogs

### Maintainer Responsibilities
Maintainers are responsible for:
- **Reviewing pull requests** in a timely manner
- **Maintaining code quality** standards
- **Helping contributors** with questions and issues
- **Keeping the project** moving forward
- **Enforcing the code of conduct**

## Conclusion

Thank you for contributing to the AREA Backend project! Your contributions help make this automation platform better for everyone. Whether you're fixing bugs, adding features, improving documentation, or helping other contributors, every contribution is valuable.

### Quick Start Checklist
- [ ] Fork and clone the repository
- [ ] Set up development environment
- [ ] Read and understand the codebase structure
- [ ] Find an issue to work on (check "good first issue" labels)
- [ ] Create a feature branch
- [ ] Make your changes with tests
- [ ] Submit a pull request
- [ ] Respond to review feedback

### Resources
- **Technical Documentation**: `/docs/technical/`
- **API Documentation**: http://localhost:8080/swagger-ui.html
- **Project Wiki**: GitHub Wiki
- **Community Chat**: Discord/Slack (if applicable)

### Questions?
If you have any questions about contributing, please:
1. Check the documentation first
2. Search existing GitHub issues and discussions
3. Create a new discussion for general questions
4. Create an issue for bugs or specific problems

Happy coding! 🚀