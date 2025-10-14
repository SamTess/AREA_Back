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

    List<ActionInstance> findByUser(User user);

    List<ActionInstance> findByArea(Area area);

    @Query("SELECT ai FROM ActionInstance ai WHERE ai.user = :user AND ai.enabled = true")
    List<ActionInstance> findEnabledByUser(@Param("user") User user);

    @Query("SELECT ai FROM ActionInstance ai WHERE ai.area = :area AND ai.enabled = true")
    List<ActionInstance> findEnabledByArea(@Param("area") Area area);

    long countByUser(User user);

    long countByArea(Area area);

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

    @Query("SELECT DISTINCT ai FROM ActionInstance ai "
           + "JOIN FETCH ai.actionDefinition ad "
           + "JOIN FETCH ad.service s "
           + "JOIN FETCH ai.user u "
           + "JOIN ActivationMode am ON am.actionInstance = ai "
           + "WHERE s.key = 'google' "
           + "AND ad.isEventCapable = true "
           + "AND ai.enabled = true "
           + "AND am.type = 'POLL' "
           + "AND am.enabled = true")
    List<ActionInstance> findActiveGoogleActionInstances();

    @Query("SELECT ai FROM ActionInstance ai "
           + "JOIN ai.actionDefinition ad "
           + "JOIN ad.service s "
           + "WHERE ai.user.id = :userId "
           + "AND s.key = :serviceKey "
           + "AND ai.enabled = true")
    List<ActionInstance> findEnabledActionInstancesByUserAndService(@Param("userId") UUID userId, 
                                                                   @Param("serviceKey") String serviceKey);

    @Query("SELECT ai FROM ActionInstance ai "
           + "JOIN ai.actionDefinition ad "
           + "JOIN ad.service s "
           + "WHERE s.key = :serviceKey "
           + "AND ai.enabled = true")
    List<ActionInstance> findEnabledActionInstancesByService(@Param("serviceKey") String serviceKey);

    @Query("SELECT ai FROM ActionInstance ai "
           + "JOIN FETCH ai.actionDefinition ad "
           + "JOIN FETCH ad.service s "
           + "JOIN FETCH ai.user u "
           + "WHERE ai.id = :id")
    java.util.Optional<ActionInstance> findByIdWithActionDefinition(@Param("id") UUID id);

    @Query("SELECT ai FROM ActionInstance ai "
           + "JOIN FETCH ai.actionDefinition ad "
           + "JOIN FETCH ad.service s "
           + "JOIN FETCH ai.user u "
           + "WHERE ai.area = :area "
           + "AND ai.enabled = true "
           + "AND ad.isExecutable = true")
    List<ActionInstance> findReactionsByArea(@Param("area") Area area);
}