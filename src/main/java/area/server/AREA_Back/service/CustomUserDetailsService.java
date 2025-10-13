package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;

    private Counter loadUserAttempts;
    private Counter loadUserSuccess;
    private Counter loadUserFailures;
    private Counter inactiveUserFailures;
    private Counter invalidUuidFailures;

    @PostConstruct
    public void initMetrics() {
        loadUserAttempts = Counter.builder("auth.user_details.load.attempts")
                .description("Total number of user details load attempts")
                .register(meterRegistry);

        loadUserSuccess = Counter.builder("auth.user_details.load.success")
                .description("Total number of successful user details loads")
                .register(meterRegistry);

        loadUserFailures = Counter.builder("auth.user_details.load.failures")
                .description("Total number of failed user details loads")
                .register(meterRegistry);

        inactiveUserFailures = Counter.builder("auth.user_details.load.inactive_user")
                .description("Total number of user details load failures due to inactive users")
                .register(meterRegistry);

        invalidUuidFailures = Counter.builder("auth.user_details.load.invalid_uuid")
                .description("Total number of user details load failures due to invalid UUID format")
                .register(meterRegistry);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        loadUserAttempts.increment();
        try {
            UUID userId = UUID.fromString(username);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));

            if (!user.getIsActive()) {
                inactiveUserFailures.increment();
                throw new UsernameNotFoundException("User account is inactive: " + userId);
            }

            loadUserSuccess.increment();
            return new CustomUserPrincipal(user);

        } catch (IllegalArgumentException e) {
            invalidUuidFailures.increment();
            throw new UsernameNotFoundException("Invalid user ID format: " + username);
        } catch (UsernameNotFoundException e) {
            loadUserFailures.increment();
            throw e;
        } catch (Exception e) {
            loadUserFailures.increment();
            log.error("Error loading user details for: { }", username, e);
            throw new UsernameNotFoundException("Error loading user details", e);
        }
    }

    public static class CustomUserPrincipal implements UserDetails {

        private final User user;

        public CustomUserPrincipal(User user) {
            this.user = user;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            if (user.getIsAdmin()) {
                return List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_USER")
                );
            } else {
                return List.of(new SimpleGrantedAuthority("ROLE_USER"));
            }
        }

        @Override
        public String getPassword() {
            return "";
        }

        @Override
        public String getUsername() {
            return user.getId().toString();
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return user.getIsActive();
        }

        public User getUser() {
            return user;
        }

        public UUID getUserId() {
            return user.getId();
        }

        public String getEmail() {
            return user.getEmail();
        }

        public boolean isAdmin() {
            return user.getIsAdmin();
        }
    }
}