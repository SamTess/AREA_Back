package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {

    /**
     * Find service by name
     */
    Optional<Service> findByName(String name);

    /**
     * Check if service exists by name
     */
    boolean existsByName(String name);

    /**
     * Find all enabled services
     */
    @Query("SELECT s FROM Service s WHERE s.enabled = true")
    List<Service> findAllEnabledServices();

    /**
     * Find services by authentication type
     */
    List<Service> findByAuthType(Service.AuthType authType);

    /**
     * Find services by name containing (case-insensitive search)
     */
    @Query("SELECT s FROM Service s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Service> findByNameContainingIgnoreCase(String name);
}