package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserOAuthIdentityRepository extends JpaRepository<UserOAuthIdentity, UUID> {

    /**
     * Find OAuth identity by user and provider
     */
    Optional<UserOAuthIdentity> findByUserAndProvider(User user, String provider);

    /**
     * Find OAuth identity by user ID and provider
     */
    Optional<UserOAuthIdentity> findByUserIdAndProvider(UUID userId, String provider);

    /**
     * Find OAuth identity by provider and provider user ID
     */
    Optional<UserOAuthIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);

    /**
     * Find all OAuth identities for a user
     */
    List<UserOAuthIdentity> findByUser(User user);

    /**
     * Find all OAuth identities for a user ID
     */
    List<UserOAuthIdentity> findByUserId(UUID userId);

    /**
     * Find all OAuth identities for a specific provider
     */
    List<UserOAuthIdentity> findByProvider(String provider);

    /**
     * Check if OAuth identity exists for user and provider
     */
    boolean existsByUserAndProvider(User user, String provider);

    /**
     * Check if OAuth identity exists for user ID and provider
     */
    boolean existsByUserIdAndProvider(UUID userId, String provider);

    /**
     * Check if provider user ID exists for a provider
     */
    boolean existsByProviderAndProviderUserId(String provider, String providerUserId);

    /**
     * Update access token for OAuth identity
     */
    @Modifying
    @Query("UPDATE UserOAuthIdentity u SET u.accessTokenEnc = :accessToken, u.updatedAt = :updatedAt WHERE u.user.id = :userId AND u.provider = :provider")
    void updateAccessToken(@Param("userId") UUID userId, @Param("provider") String provider, @Param("accessToken") String accessToken, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Update refresh token for OAuth identity
     */
    @Modifying
    @Query("UPDATE UserOAuthIdentity u SET u.refreshTokenEnc = :refreshToken, u.updatedAt = :updatedAt WHERE u.user.id = :userId AND u.provider = :provider")
    void updateRefreshToken(@Param("userId") UUID userId, @Param("provider") String provider, @Param("refreshToken") String refreshToken, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Update both access and refresh tokens
     */
    @Modifying
    @Query("UPDATE UserOAuthIdentity u SET u.accessTokenEnc = :accessToken, u.refreshTokenEnc = :refreshToken, u.expiresAt = :expiresAt, u.updatedAt = :updatedAt WHERE u.user.id = :userId AND u.provider = :provider")
    void updateTokens(@Param("userId") UUID userId, @Param("provider") String provider, @Param("accessToken") String accessToken, @Param("refreshToken") String refreshToken, @Param("expiresAt") LocalDateTime expiresAt, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Update token expiration
     */
    @Modifying
    @Query("UPDATE UserOAuthIdentity u SET u.expiresAt = :expiresAt, u.updatedAt = :updatedAt WHERE u.user.id = :userId AND u.provider = :provider")
    void updateTokenExpiration(@Param("userId") UUID userId, @Param("provider") String provider, @Param("expiresAt") LocalDateTime expiresAt, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Update scopes for OAuth identity
     */
    @Modifying
    @Query("UPDATE UserOAuthIdentity u SET u.scopes = :scopes, u.updatedAt = :updatedAt WHERE u.user.id = :userId AND u.provider = :provider")
    void updateScopes(@Param("userId") UUID userId, @Param("provider") String provider, @Param("scopes") Map<String, Object> scopes, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Delete OAuth identity by user and provider
     */
    @Modifying
    @Query("DELETE FROM UserOAuthIdentity u WHERE u.user.id = :userId AND u.provider = :provider")
    void deleteByUserIdAndProvider(@Param("userId") UUID userId, @Param("provider") String provider);

    /**
     * Find OAuth identities with expired tokens
     */
    @Query("SELECT u FROM UserOAuthIdentity u WHERE u.expiresAt < :now AND u.refreshTokenEnc IS NOT NULL")
    List<UserOAuthIdentity> findExpiredTokensWithRefreshToken(@Param("now") LocalDateTime now);

    /**
     * Count OAuth identities for a user
     */
    @Query("SELECT COUNT(u) FROM UserOAuthIdentity u WHERE u.user.id = :userId")
    long countByUserId(@Param("userId") UUID userId);
}