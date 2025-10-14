package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActionDefinitionRepository extends JpaRepository<ActionDefinition, UUID> {

    /**
     * Find action definition by service and key
     */
    Optional<ActionDefinition> findByServiceAndKey(Service service, String key);

    /**
     * Find action definitions by service
     */
    List<ActionDefinition> findByService(Service service);

    /**
     * Find action definitions by service and version
     */
    List<ActionDefinition> findByServiceAndVersion(Service service, Integer version);

    /**
     * Find event-capable action definitions
     */
    @Query("SELECT ad FROM ActionDefinition ad WHERE ad.isEventCapable = true")
    List<ActionDefinition> findEventCapableActions();

    /**
     * Find executable action definitions
     */
    @Query("SELECT ad FROM ActionDefinition ad WHERE ad.isExecutable = true")
    List<ActionDefinition> findExecutableActions();

    /**
     * Find action definitions by service key
     */
    @Query("SELECT ad FROM ActionDefinition ad WHERE ad.service.key = :serviceKey")
    List<ActionDefinition> findByServiceKey(@Param("serviceKey") String serviceKey);

    /**
     * Find action definitions by service ID
     */
    @Query("SELECT ad FROM ActionDefinition ad WHERE ad.service.id = :serviceId")
    List<ActionDefinition> findByServiceId(@Param("serviceId") UUID serviceId);
}