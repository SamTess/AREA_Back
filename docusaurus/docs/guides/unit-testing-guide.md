# Unit Testing Guide

This guide explains how to implement unit tests in the AREA Backend project using the existing testing setup.

## Table of Contents

- [Overview](#overview)
- [Testing Framework](#testing-framework)
- [Project Test Structure](#project-test-structure)
- [Types of Tests](#types-of-tests)
- [Setting Up Tests](#setting-up-tests)
- [Writing Unit Tests](#writing-unit-tests)
- [Integration Tests](#integration-tests)
- [Test Configuration](#test-configuration)
- [Best Practices](#best-practices)
- [Running Tests](#running-tests)
- [Coverage Reports](#coverage-reports)

## Overview

The AREA Backend project uses a comprehensive testing setup with JUnit 5, Mockito, Spring Boot Test, and Testcontainers. The project maintains a test coverage target of 80%.

## Testing Framework

### Core Testing Libraries

- **JUnit 5**: Main testing framework
- **Mockito**: Mocking framework for isolating units under test
- **Spring Boot Test**: Integration testing with Spring context
- **Testcontainers**: Real database testing with Docker containers
- **H2**: In-memory database for unit tests
- **Hamcrest**: Assertion matchers

### Additional Testing Dependencies

```gradle
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.springframework.restdocs:spring-restdocs-mockmvc'
testImplementation 'org.springframework.security:spring-security-test'
testImplementation 'org.testcontainers:junit-jupiter'
testImplementation 'org.testcontainers:postgresql'
testImplementation 'com.h2database:h2'
testImplementation 'org.mockito:mockito-core'
testImplementation 'org.mockito:mockito-junit-jupiter'
testImplementation 'org.hamcrest:hamcrest'
```

## Project Test Structure

```
src/test/java/area/server/AREA_Back/
├── config/
│   └── TestSecurityConfig.java          # Test security configuration
├── controller/                          # Controller layer tests
│   ├── AboutControllerTest.java
│   ├── AreaControllerTest.java
│   ├── ServiceControllerTest.java
│   └── UserControllerTest.java
├── dto/                                # DTO tests
│   ├── AreaResponseTest.java
│   ├── CreateAreaRequestTest.java
│   └── UserResponseTest.java
├── entity/                             # Entity tests
│   ├── AreaTest.java
│   ├── ServiceTest.java
│   └── UserTest.java
├── repository/                         # Repository layer tests
│   ├── AreaRepositoryTest.java
│   └── UserRepositoryTest.java
├── service/                           # Service layer tests
├── AllTestsSuite.java                 # Test suite runner
├── AreaBackApplicationTests.java      # Application context tests
├── TestcontainersConfiguration.java   # Testcontainers setup
└── TestAreaBackApplication.java       # Test application entry point
```

## Types of Tests

### 1. Unit Tests

Test individual components in isolation using mocks.

**Example: Entity Unit Test**

```java
@ExtendWith(MockitoExtension.class)
class UserTest {

    @Test
    void testUserCreation() {
        // Given
        String email = "test@example.com";
        String username = "testuser";
        
        // When
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        
        // Then
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getUsername()).isEqualTo(username);
        assertThat(user.getCreatedAt()).isNotNull();
    }
}
```

### 2. Web Layer Tests

Test controllers with mocked dependencies using `@WebMvcTest`.

**Example: Controller Test**

```java
@WebMvcTest(UserController.class)
@Import(TestSecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    void shouldCreateUser() throws Exception {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@example.com");
        request.setUsername("testuser");
        request.setPassword("password");

        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail(request.getEmail());
        savedUser.setUsername(request.getUsername());

        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // When & Then
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpected(jsonPath("$.email").value("test@example.com"))
                .andExpected(jsonPath("$.username").value("testuser"));
    }
}
```

### 3. Repository Tests

Test data access layer with `@DataJpaTest`.

**Example: Repository Test**

```java
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldFindByEmail() {
        // Given
        User user = new User();
        user.setEmail("test@example.com");
        user.setUsername("testuser");
        entityManager.persistAndFlush(user);

        // When
        Optional<User> found = userRepository.findByEmail("test@example.com");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
    }
}
```

### 4. Integration Tests

Test complete application flow with `@SpringBootTest` and Testcontainers.

**Example: Integration Test**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_area_db")
            .withUsername("test_user")
            .withPassword("test_password");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldCreateAndRetrieveUser() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("integration@example.com");
        request.setUsername("integrationuser");
        request.setPassword("password");

        // When
        ResponseEntity<UserResponse> createResponse = restTemplate
                .postForEntity("/api/users", request, UserResponse.class);

        // Then
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        UUID userId = createResponse.getBody().getId();
        ResponseEntity<UserResponse> getResponse = restTemplate
                .getForEntity("/api/users/" + userId, UserResponse.class);
                
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getEmail()).isEqualTo("integration@example.com");
    }
}
```

## Setting Up Tests

### 1. Test Class Structure

```java
// For unit tests
@ExtendWith(MockitoExtension.class)
class ServiceClassTest {
    // Test implementation
}

// For web layer tests
@WebMvcTest(ControllerClass.class)
@Import(TestSecurityConfig.class)
class ControllerClassTest {
    // Test implementation
}

// For repository tests
@DataJpaTest
class RepositoryClassTest {
    // Test implementation
}

// For integration tests
@SpringBootTest
@Testcontainers
class IntegrationTest {
    // Test implementation
}
```

### 2. Common Annotations

- `@Test`: Marks a test method
- `@BeforeEach`: Setup before each test
- `@AfterEach`: Cleanup after each test
- `@MockitoBean`: Create mock beans in Spring context
- `@Mock`: Create mock objects
- `@InjectMocks`: Inject mocks into tested object
- `@WithMockUser`: Security context for tests

## Writing Unit Tests

### 1. Test Method Naming

Use descriptive names following the pattern:
```java
@Test
void should_ReturnExpectedResult_When_GivenSpecificCondition() {
    // Test implementation
}
```

### 2. Test Structure (Given-When-Then)

```java
@Test
void shouldCalculateUserAge() {
    // Given - Set up test data
    LocalDate birthDate = LocalDate.of(1990, 1, 1);
    User user = new User();
    user.setBirthDate(birthDate);
    
    // When - Execute the method under test
    int age = user.calculateAge();
    
    // Then - Verify the results
    assertThat(age).isEqualTo(34);
}
```

### 3. Mocking Dependencies

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
    void shouldCreateUser() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@example.com");
        
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(createMockUser());

        // When
        UserResponse response = userService.createUser(request);

        // Then
        assertThat(response).isNotNull();
        verify(userRepository).save(any(User.class));
    }
}
```

## Integration Tests

### Testcontainers Configuration

The project includes `TestcontainersConfiguration.java` for database integration tests:

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:15")
                .withDatabaseName("test_area_db")
                .withUsername("test_user")
                .withPassword("test_password");
    }
}
```

## Test Configuration

### Security Configuration for Tests

```java
@TestConfiguration
@EnableWebSecurity
public class TestSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
```

## Best Practices

### 1. Test Independence

- Each test should be independent and not rely on other tests
- Use `@DirtiesContext` when tests modify shared state
- Clean up resources in `@AfterEach` methods

### 2. Test Data Management

```java
@BeforeEach
void setUp() {
    // Create fresh test data for each test
    testUser = createTestUser();
}

@AfterEach
void tearDown() {
    // Clean up if necessary
    userRepository.deleteAll();
}
```

### 3. Assertion Guidelines

Use AssertJ for fluent assertions:

```java
// Good
assertThat(users)
    .hasSize(3)
    .extracting(User::getEmail)
    .containsExactly("user1@test.com", "user2@test.com", "user3@test.com");

// Avoid
assertEquals(3, users.size());
assertEquals("user1@test.com", users.get(0).getEmail());
```

### 4. Exception Testing

```java
@Test
void shouldThrowExceptionWhenUserNotFound() {
    // Given
    UUID nonExistentId = UUID.randomUUID();
    when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> userService.getUserById(nonExistentId))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessage("User not found with id: " + nonExistentId);
}
```

### 5. Parameterized Tests

```java
@ParameterizedTest
@ValueSource(strings = {"", " ", "invalid-email", "@test.com"})
void shouldRejectInvalidEmails(String email) {
    // Given
    CreateUserRequest request = new CreateUserRequest();
    request.setEmail(email);

    // When & Then
    assertThatThrownBy(() -> userService.createUser(request))
            .isInstanceOf(ValidationException.class);
}
```

## Running Tests

### Command Line

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests UserControllerTest

# Run tests with specific tags
./gradlew test --tests "*Integration*"

# Run tests with coverage
./gradlew jacocoTestReport
```

### IDE Integration

Most IDEs (IntelliJ IDEA, Eclipse, VS Code) provide built-in support for running JUnit tests with visual feedback.

## Coverage Reports

### Generating Coverage Reports

```bash
./gradlew jacocoTestReport
```

### Viewing Reports

- **HTML Report**: `build/jacocoHtml/index.html`
- **XML Report**: `build/reports/jacoco/test/jacocoTestReport.xml`

### Coverage Requirements

The project enforces a minimum coverage of 80%:

```gradle
jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.80
            }
        }
    }
}
```

## Troubleshooting

### Common Issues

1. **Tests fail due to security context**: Add `@WithMockUser` or configure test security
2. **Database connection issues**: Ensure Testcontainers is properly configured
3. **Mock not working**: Verify `@MockitoBean` vs `@Mock` usage
4. **Tests pass individually but fail in suite**: Check for test interdependencies

### Debug Tips

- Use `@Sql` to load test data from SQL files
- Add logging to understand test execution flow
- Use breakpoints and debug mode in your IDE
- Check test execution order with `@TestMethodOrder`

This guide should help you implement comprehensive unit tests following the project's established patterns and best practices.