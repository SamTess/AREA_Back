package area.server.AREA_Back.filter;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserLocalIdentity;
import area.server.AREA_Back.repository.UserLocalIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationFilterTest {

    @Mock
    private UserLocalIdentityRepository userLocalIdentityRepository;

    @InjectMocks
    private EmailVerificationFilter emailVerificationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private User testUser;
    private UserLocalIdentity testIdentity;
    private UUID userId;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        userId = UUID.randomUUID();
        testUser = new User();
        testUser.setId(userId);

        testIdentity = new UserLocalIdentity();
        testIdentity.setUser(testUser);
        testIdentity.setIsEmailVerified(false);
    }

    @Test
    void shouldAllowAccessWhenUserIsNotAuthenticated() throws Exception {
        // Given
        SecurityContextHolder.getContext().setAuthentication(null);
        request.setRequestURI("/api/users");

        // When
        emailVerificationFilter.doFilterInternal(request, response, (req, res) -> {
            // Filter chain should continue
        });

        // Then
        assertEquals(200, response.getStatus()); // No error status set
    }

    @Test
    void shouldAllowAccessWhenUserIsVerified() throws Exception {
        // Given
        testIdentity.setIsEmailVerified(true);
        setupAuthenticatedUser();

        when(userLocalIdentityRepository.findByUserId(userId)).thenReturn(Optional.of(testIdentity));

        request.setRequestURI("/api/users");

        // When
        emailVerificationFilter.doFilterInternal(request, response, (req, res) -> {
            // Filter chain should continue
        });

        // Then
        assertEquals(200, response.getStatus()); // No error status set
    }

    @Test
    void shouldAllowAccessWhenNonVerifiedUserAccessesAllowedEndpoint() throws Exception {
        // Given
        setupAuthenticatedUser();

        when(userLocalIdentityRepository.findByUserId(userId)).thenReturn(Optional.of(testIdentity));

        request.setRequestURI("/api/auth/me");

        // When
        emailVerificationFilter.doFilterInternal(request, response, (req, res) -> {
            // Filter chain should continue
        });

        // Then
        assertEquals(200, response.getStatus()); // No error status set
    }

    @Test
    void shouldDenyAccessWhenNonVerifiedUserAccessesRestrictedEndpoint() throws Exception {
        // Given
        setupAuthenticatedUser();

        when(userLocalIdentityRepository.findByUserId(userId)).thenReturn(Optional.of(testIdentity));

        request.setRequestURI("/api/users");

        // When
        emailVerificationFilter.doFilterInternal(request, response, (req, res) -> {
            // This should not be called
            fail("Filter chain should not continue for restricted endpoint");
        });

        // Then
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Email verification required"));
    }

    @Test
    void shouldDenyAccessWhenLocalIdentityNotFound() throws Exception {
        // Given - User authenticated but no local identity (data inconsistency)
        setupAuthenticatedUser();

        when(userLocalIdentityRepository.findByUserId(userId)).thenReturn(Optional.empty());

        request.setRequestURI("/api/users");

        // When
        emailVerificationFilter.doFilterInternal(request, response, (req, res) -> {
            // This should not be called
            fail("Filter chain should not continue when local identity not found");
        });

        // Then
        // User with no local identity should be denied (data inconsistency)
        assertEquals(401, response.getStatus());
    }

    private void setupAuthenticatedUser() {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
            .withUsername(userId.toString())
            .password("")
            .authorities("ROLE_USER")
            .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            userDetails.getAuthorities()
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}