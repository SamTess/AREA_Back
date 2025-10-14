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
     * Check if user exists by email
     */
    boolean existsByEmail(String email);

    /**
     * Find enabled users
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true")
    java.util.List<User> findAllEnabledUsers();

    /**
     * Find users connected per day
     */
    @Query(value = "SELECT DATE(last_login_at) as date, COUNT(*) as count " +
                   "FROM area.a_users " +
                   "WHERE last_login_at BETWEEN :startDate AND :endDate " +
                   "GROUP BY DATE(last_login_at) " +
                   "ORDER BY date", nativeQuery = true)
    java.util.List<Object[]> findUsersConnectedPerDay(@Param("startDate") java.time.LocalDate startDate, 
                                                       @Param("endDate") java.time.LocalDate endDate);

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