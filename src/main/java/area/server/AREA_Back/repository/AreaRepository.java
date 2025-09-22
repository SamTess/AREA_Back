package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AreaRepository extends JpaRepository<Area, Long> {

    /**
     * Find areas by user
     */
    List<Area> findByUser(User user);

    /**
     * Find areas by user ID
     */
    List<Area> findByUserId(Long userId);

    /**
     * Find enabled areas by user
     */
    @Query("SELECT a FROM Area a WHERE a.user = :user AND a.enabled = true")
    List<Area> findEnabledAreasByUser(@Param("user") User user);

    /**
     * Find areas by action service
     */
    List<Area> findByActionService(Service actionService);

    /**
     * Find areas by reaction service
     */
    List<Area> findByReactionService(Service reactionService);

    /**
     * Find areas by action type
     */
    List<Area> findByActionType(String actionType);

    /**
     * Find areas by reaction type
     */
    List<Area> findByReactionType(String reactionType);

    /**
     * Find areas that have been triggered recently
     */
    @Query("SELECT a FROM Area a WHERE a.lastTriggered >= :since AND a.enabled = true")
    List<Area> findRecentlyTriggeredAreas(@Param("since") LocalDateTime since);

    /**
     * Find areas that need to be triggered (enabled and not triggered recently)
     */
    @Query("SELECT a FROM Area a WHERE a.enabled = true AND (a.lastTriggered IS NULL OR a.lastTriggered < :threshold)")
    List<Area> findAreasToTrigger(@Param("threshold") LocalDateTime threshold);

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