package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserLocalIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserLocalIdentityRepository extends JpaRepository<UserLocalIdentity, UUID> {

    /**
     * Find user local identity by email
     */
    Optional<UserLocalIdentity> findByEmail(String email);

    /**
     * Find user local identity by user
     */
    Optional<UserLocalIdentity> findByUser(User user);

    /**
     * Find user local identity by user ID
     */
    Optional<UserLocalIdentity> findByUserId(UUID userId);

    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Find by email verification token
     */
    Optional<UserLocalIdentity> findByEmailVerificationToken(String token);

    /**
     * Find by password reset token
     */
    Optional<UserLocalIdentity> findByPasswordResetToken(String token);

    /**
     * Increment failed login attempts
     */
    @Modifying
    @Query("UPDATE UserLocalIdentity u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.email = :email")
    void incrementFailedLoginAttempts(@Param("email") String email);

    /**
     * Reset failed login attempts
     */
    @Modifying
    @Query("UPDATE UserLocalIdentity u SET u.failedLoginAttempts = 0, u.lockedUntil = null WHERE u.email = :email")
    void resetFailedLoginAttempts(@Param("email") String email);

    /**
     * Lock account until specified time
     */
    @Modifying
    @Query("UPDATE UserLocalIdentity u SET u.lockedUntil = :lockUntil WHERE u.email = :email")
    void lockAccount(@Param("email") String email, @Param("lockUntil") LocalDateTime lockUntil);

    /**
     * Clear email verification token
     */
    @Modifying
    @Query("UPDATE UserLocalIdentity u SET u.emailVerificationToken = null, u.emailVerificationExpiresAt = null, u.isEmailVerified = true WHERE u.emailVerificationToken = :token")
    void clearEmailVerificationToken(@Param("token") String token);

    /**
     * Clear password reset token
     */
    @Modifying
    @Query("UPDATE UserLocalIdentity u SET u.passwordResetToken = null, u.passwordResetExpiresAt = null WHERE u.passwordResetToken = :token")
    void clearPasswordResetToken(@Param("token") String token);
}