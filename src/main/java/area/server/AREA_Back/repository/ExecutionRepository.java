package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExecutionRepository extends JpaRepository<Execution, UUID> {

    /**
     * Find executions by status
     */
    List<Execution> findByStatus(ExecutionStatus status);

    /**
     * Find executions by action instance
     */
    List<Execution> findByActionInstance(ActionInstance actionInstance);

    /**
     * Find executions by area
     */
    List<Execution> findByArea(Area area);

    /**
     * Find queued executions ordered by queue time
     */
    @Query("SELECT e FROM Execution e WHERE e.status = 'QUEUED' ORDER BY e.queuedAt ASC")
    List<Execution> findQueuedExecutionsOrderedByQueueTime();

    /**
     * Find executions that need retry (RETRY status and not recently attempted)
     */
    @Query("SELECT e FROM Execution e WHERE e.status = 'RETRY' AND "
           + "(e.startedAt IS NULL OR e.startedAt < :retryThreshold) "
           + "ORDER BY e.queuedAt ASC")
    List<Execution> findExecutionsReadyForRetry(@Param("retryThreshold") LocalDateTime retryThreshold);

    /**
     * Find running executions that may have timed out
     */
    @Query("SELECT e FROM Execution e WHERE e.status = 'RUNNING' AND "
           + "e.startedAt < :timeoutThreshold")
    List<Execution> findTimedOutExecutions(@Param("timeoutThreshold") LocalDateTime timeoutThreshold);

    /**
     * Find executions by correlation ID
     */
    List<Execution> findByCorrelationId(UUID correlationId);

    /**
     * Find executions by status and area
     */
    @Query("SELECT e FROM Execution e WHERE e.status = :status AND e.area = :area ORDER BY e.queuedAt ASC")
    List<Execution> findByStatusAndArea(@Param("status") ExecutionStatus status, @Param("area") Area area);

    /**
     * Count executions by status
     */
    long countByStatus(ExecutionStatus status);

    /**
     * Count failed executions for a specific action instance in the last period
     */
    @Query("SELECT COUNT(e) FROM Execution e WHERE e.actionInstance = :actionInstance AND "
           + "e.status = 'FAILED' AND e.queuedAt > :since")
    long countFailedExecutionsSince(@Param("actionInstance") ActionInstance actionInstance,
                                   @Param("since") LocalDateTime since);

    /**
     * Find executions by dedup key
     */
    List<Execution> findByDedupKey(String dedupKey);

    /**
     * Find recent executions for an action instance
     */
    @Query("SELECT e FROM Execution e WHERE e.actionInstance = :actionInstance AND "
          + "e.queuedAt > :since ORDER BY e.queuedAt DESC")
    List<Execution> findRecentExecutionsByActionInstance(@Param("actionInstance") ActionInstance actionInstance,
                                                        @Param("since") LocalDateTime since);

    /**
     * Find execution by ID with all relations loaded (for processing)
     */
    @Query("SELECT e FROM Execution e "
           + "JOIN FETCH e.actionInstance ai "
           + "JOIN FETCH ai.actionDefinition ad "
           + "JOIN FETCH ad.service s "
           + "JOIN FETCH ai.user u "
           + "WHERE e.id = :id")
    java.util.Optional<Execution> findByIdWithActionInstance(@Param("id") UUID id);

    /**
     * Count executions by status and created after timestamp
     */
    @Query("SELECT COUNT(e) FROM Execution e WHERE e.status = :status AND e.queuedAt > :createdAfter")
    Long countByStatusAndCreatedAtAfter(
        @Param("status") ExecutionStatus status,
        @Param("createdAfter") LocalDateTime createdAfter);

    /**
     * Find executions by area ID ordered by created at descending
     */
    @Query("SELECT e FROM Execution e WHERE e.area.id = :areaId ORDER BY e.queuedAt DESC")
    List<Execution> findByAreaIdOrderByCreatedAtDesc(@Param("areaId") UUID areaId);
}