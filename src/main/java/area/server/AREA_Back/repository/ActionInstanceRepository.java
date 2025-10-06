package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActionInstanceRepository extends JpaRepository<ActionInstance, UUID> {

    /**
     * Find action instances by user
     */
    List<ActionInstance> findByUser(User user);

    /**
     * Find action instances by area
     */
    List<ActionInstance> findByArea(Area area);

    /**
     * Find enabled action instances by user
     */
    @Query("SELECT ai FROM ActionInstance ai WHERE ai.user = :user AND ai.enabled = true")
    List<ActionInstance> findEnabledByUser(@Param("user") User user);

    /**
     * Find enabled action instances by area
     */
    @Query("SELECT ai FROM ActionInstance ai WHERE ai.area = :area AND ai.enabled = true")
    List<ActionInstance> findEnabledByArea(@Param("area") Area area);

    /**
     * Count action instances by user
     */
    long countByUser(User user);

    /**
     * Count action instances by area
     */
    long countByArea(Area area);

    /**
     * Find active GitHub action instances (event-capable actions with POLL activation mode)
     */
    @Query("SELECT DISTINCT ai FROM ActionInstance ai "
           + "JOIN ai.actionDefinition ad "
           + "JOIN ad.service s "
           + "JOIN ActivationMode am ON am.actionInstance = ai "
           + "WHERE s.key = 'github' "
           + "AND ad.isEventCapable = true "
           + "AND ai.enabled = true "
           + "AND am.type = 'POLL' "
           + "AND am.enabled = true")
    List<ActionInstance> findActiveGitHubActionInstances();
}