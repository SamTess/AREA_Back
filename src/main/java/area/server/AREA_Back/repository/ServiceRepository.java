package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<Service, UUID> {

    /**
     * Find service by key
     */
    Optional<Service> findByKey(String key);

    /**
     * Find service by name
     */
    Optional<Service> findByName(String name);

    /**
     * Check if service exists by key
     */
    boolean existsByKey(String key);

    /**
     * Find all enabled services
     */
    @Query("SELECT s FROM Service s WHERE s.isActive = true")
    List<Service> findAllEnabledServices();

    /**
     * Find services by authentication type
     */
    List<Service> findByAuth(Service.AuthType auth);

    /**
     * Find services by name containing (case-insensitive search)
     */
    @Query("SELECT s FROM Service s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Service> findByNameContainingIgnoreCase(String name);
}