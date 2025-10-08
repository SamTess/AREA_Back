package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.ActivationMode;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivationModeRepository extends JpaRepository<ActivationMode, UUID> {

    /**
     * Find activation modes by action instance
     */
    List<ActivationMode> findByActionInstance(ActionInstance actionInstance);

    /**
     * Find activation modes by action instance and type
     */
    List<ActivationMode> findByActionInstanceAndType(ActionInstance actionInstance, ActivationModeType type);

    /**
     * Find activation modes by action instance, type and enabled status
     */
    List<ActivationMode> findByActionInstanceAndTypeAndEnabled(ActionInstance actionInstance,
        ActivationModeType type, Boolean enabled);

    /**
     * Find enabled activation modes by type
     */
    List<ActivationMode> findByTypeAndEnabled(ActivationModeType type, Boolean enabled);

    /**
     * Find enabled activation modes by type with ActionInstance eagerly loaded
     */
    @Query("SELECT am FROM ActivationMode am " +
           "JOIN FETCH am.actionInstance ai " +
           "WHERE am.type = :type AND am.enabled = :enabled AND ai.enabled = true")
    List<ActivationMode> findByTypeAndEnabledWithActionInstance(ActivationModeType type, Boolean enabled);

    /**
     * Find all enabled activation modes
     */
    @Query("SELECT am FROM ActivationMode am WHERE am.enabled = true")
    List<ActivationMode> findAllEnabled();

    /**
     * Find activation modes by action instance and enabled status
     */
    List<ActivationMode> findByActionInstanceAndEnabled(ActionInstance actionInstance, Boolean enabled);
}