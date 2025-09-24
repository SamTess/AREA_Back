package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.ServiceAccount;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceAccountRepository extends JpaRepository<ServiceAccount, UUID> {

    /**
     * Find service account by user and service
     */
    Optional<ServiceAccount> findByUserAndService(User user, Service service);

    /**
     * Find service accounts by user
     */
    List<ServiceAccount> findByUser(User user);

    /**
     * Find service accounts by service
     */
    List<ServiceAccount> findByService(Service service);

    /**
     * Find active service accounts (not revoked)
     */
    @Query("SELECT sa FROM ServiceAccount sa WHERE sa.revokedAt IS NULL")
    List<ServiceAccount> findActiveAccounts();

    /**
     * Find service accounts that need token refresh
     */
    @Query("SELECT sa FROM ServiceAccount sa WHERE sa.expiresAt < CURRENT_TIMESTAMP "
            + "AND sa.refreshTokenEnc IS NOT NULL AND sa.revokedAt IS NULL")
    List<ServiceAccount> findAccountsNeedingRefresh();

    /**
     * Find service accounts by user and active status
     */
    @Query("SELECT sa FROM ServiceAccount sa WHERE sa.user = :user AND sa.revokedAt IS NULL")
    List<ServiceAccount> findActiveByUser(@Param("user") User user);
}