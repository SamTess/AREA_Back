package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by email
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by username
     */
    Optional<User> findByUsername(String username);

    /**
     * Check if user exists by email
     */
    boolean existsByEmail(String email);

    /**
     * Check if user exists by username
     */
    boolean existsByUsername(String username);

    /**
     * Find enabled users
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true")
    java.util.List<User> findAllEnabledUsers();

    /**
     * Find users connected per day
     * Uses half-open interval [start, endExclusive) to properly handle timestamp ranges
     */
    @Query(value = "SELECT DATE(last_login_at) as date, COUNT(*) as count "
                   + "FROM area.a_users "
                   + "WHERE last_login_at >= :start AND last_login_at < :endExclusive "
                   + "GROUP BY DATE(last_login_at) "
                   + "ORDER BY date", nativeQuery = true)
    java.util.List<Object[]> findUsersConnectedPerDay(@Param("start") java.time.LocalDateTime start,
                                                       @Param("endExclusive") java.time.LocalDateTime endExclusive);

    /**
     * Count users created between dates
     */
    Long countByCreatedAtBetween(java.time.LocalDateTime startDate, java.time.LocalDateTime endDate);

    /**
     * Count active users
     */
    Long countByIsActive(Boolean isActive);

    /**
     * Count admin users
     */
    Long countByIsAdmin(Boolean isAdmin);

    /**
     * Count users created after date
     */
    Long countByCreatedAtAfter(java.time.LocalDateTime date);
}