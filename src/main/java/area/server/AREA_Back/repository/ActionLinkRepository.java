package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.ActionLink;
import area.server.AREA_Back.entity.ActionLinkId;
import area.server.AREA_Back.entity.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActionLinkRepository extends JpaRepository<ActionLink, ActionLinkId> {

    /**
     * Finds all links for a given area
     */
    List<ActionLink> findByAreaOrderByOrder(Area area);

    /**
     * Finds all links for a given area by its ID
     */
    @Query("SELECT al FROM ActionLink al WHERE al.area.id = :areaId ORDER BY al.order")
    List<ActionLink> findByAreaIdOrderByOrder(@Param("areaId") UUID areaId);

    /**
     * Finds all links where an action is the source
     */
    List<ActionLink> findBySourceActionInstance(ActionInstance sourceActionInstance);

    /**
     * Finds all links where an action is the source by its ID
     */
    List<ActionLink> findBySourceActionInstanceId(UUID sourceActionInstanceId);

    /**
     * Finds all links where an action is the source by its ID with fetch join
     */
    @Query("SELECT al FROM ActionLink al "
           + "LEFT JOIN FETCH al.targetActionInstance "
           + "WHERE al.sourceActionInstance.id = :sourceActionInstanceId")
    List<ActionLink> findBySourceActionInstanceIdWithTargetFetch(
            @Param("sourceActionInstanceId") UUID sourceActionInstanceId);

    /**
     * Finds all links where an action is the target
     */
    List<ActionLink> findByTargetActionInstance(ActionInstance targetActionInstance);

    /**
     * Finds all links involving a specific action (source or target)
     */
    @Query("SELECT al FROM ActionLink al WHERE al.sourceActionInstance = :actionInstance "
            + "OR al.targetActionInstance = :actionInstance")
    List<ActionLink> findByActionInstance(@Param("actionInstance") ActionInstance actionInstance);

    /**
     * Finds all links involving a specific action by its ID (source or target)
     */
    @Query("SELECT al FROM ActionLink al WHERE al.sourceActionInstance.id = :actionInstanceId "
            + "OR al.targetActionInstance.id = :actionInstanceId")
    List<ActionLink> findByActionInstanceId(@Param("actionInstanceId") UUID actionInstanceId);

    /**
     * Checks if a link exists between two actions
     */
    boolean existsBySourceActionInstanceAndTargetActionInstance(
            ActionInstance sourceActionInstance, ActionInstance targetActionInstance);

    /**
     * Deletes all links for a given area
     */
    void deleteByArea(Area area);

    /**
     * Deletes all links involving a specific action
     */
    @Query("DELETE FROM ActionLink al WHERE al.sourceActionInstance = :actionInstance "
            + "OR al.targetActionInstance = :actionInstance")
    void deleteByActionInstance(@Param("actionInstance") ActionInstance actionInstance);
}