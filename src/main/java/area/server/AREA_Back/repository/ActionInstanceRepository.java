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
     * Delete all action instances by area ID
     */
    void deleteByAreaId(UUID areaId);

    /**
     * Find active GitHub action instances (event-capable actions with POLL activation mode)
     */
    @Query("SELECT DISTINCT ai FROM ActionInstance ai "
           + "JOIN FETCH ai.actionDefinition ad "
           + "JOIN FETCH ad.service s "
           + "JOIN FETCH ai.user u "
           + "JOIN ActivationMode am ON am.actionInstance = ai "
           + "WHERE s.key = 'github' "
           + "AND ad.isEventCapable = true "
           + "AND ai.enabled = true "
           + "AND am.type = 'POLL' "
           + "AND am.enabled = true")
    List<ActionInstance> findActiveGitHubActionInstances();

    /**
     * Find enabled action instances by user and service key
     */
    @Query("SELECT ai FROM ActionInstance ai "
           + "JOIN ai.actionDefinition ad "
           + "JOIN ad.service s "
           + "WHERE ai.user.id = :userId "
           + "AND s.key = :serviceKey "
           + "AND ai.enabled = true")
    List<ActionInstance> findEnabledActionInstancesByUserAndService(@Param("userId") UUID userId, 
                                                                   @Param("serviceKey") String serviceKey);

    /**
     * Find enabled action instances by service key
     */
    @Query("SELECT ai FROM ActionInstance ai "
           + "JOIN ai.actionDefinition ad "
           + "JOIN ad.service s "
           + "WHERE s.key = :serviceKey "
           + "AND ai.enabled = true")
    List<ActionInstance> findEnabledActionInstancesByService(@Param("serviceKey") String serviceKey);

    /**
     * Find action instance with all relations loaded (for execution)
     */
    @Query("SELECT ai FROM ActionInstance ai "
           + "JOIN FETCH ai.actionDefinition ad "
           + "JOIN FETCH ad.service s "
           + "JOIN FETCH ai.user u "
           + "WHERE ai.id = :id")
    java.util.Optional<ActionInstance> findByIdWithActionDefinition(@Param("id") UUID id);

    /**
     * Find reaction instances (executable actions) for an area with all relations loaded
     */
    @Query("SELECT ai FROM ActionInstance ai "
           + "JOIN FETCH ai.actionDefinition ad "
           + "JOIN FETCH ad.service s "
           + "JOIN FETCH ai.user u "
           + "WHERE ai.area = :area "
           + "AND ai.enabled = true "
           + "AND ad.isExecutable = true")
    List<ActionInstance> findReactionsByArea(@Param("area") Area area);

    /**
     * Find action instances by area ID
     */
    @Query("SELECT ai FROM ActionInstance ai WHERE ai.area.id = :areaId")
    List<ActionInstance> findByAreaId(@Param("areaId") UUID areaId);

    /**
     * Count action instances by service ID
     */
    @Query("SELECT COUNT(ai) FROM ActionInstance ai WHERE ai.actionDefinition.service.id = :serviceId")
    Long countByActionDefinition_Service_Id(@Param("serviceId") UUID serviceId);
}