package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}