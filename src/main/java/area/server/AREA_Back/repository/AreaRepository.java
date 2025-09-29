package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AreaRepository extends JpaRepository<Area, UUID> {

    /**
     * Find areas by user
     */
    List<Area> findByUser(User user);

    /**
     * Find areas by user ID
     */
    List<Area> findByUserId(UUID userId);

    /**
     * Find enabled areas by user
     */
    @Query("SELECT a FROM Area a WHERE a.user = :user AND a.enabled = true")
    List<Area> findEnabledAreasByUser(@Param("user") User user);

    /**
     * Count areas by user
     */
    long countByUser(User user);

    /**
     * Find areas by name containing (case-insensitive search)
     */
    @Query("SELECT a FROM Area a WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Area> findByNameContainingIgnoreCase(@Param("name") String name);
}