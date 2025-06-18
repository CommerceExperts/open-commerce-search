package de.cxp.ocs.repository;

import de.cxp.ocs.model.ConfigEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface ConfigRepository extends JpaRepository<ConfigEntity, Long> {
    Optional<ConfigEntity> findFirstByServiceAndIsActiveTrueOrderByCreatedAtDesc(String service);

    Page<ConfigEntity> findByServiceOrderByCreatedAtDesc(String service, Pageable pageable);

    Optional<ConfigEntity> findByIdAndService(Long id, String service);

    @Transactional
    @Modifying
    @Query("UPDATE ConfigEntity c SET c.isActive = false WHERE c.service = :service")
    void deactivateAllByService(String service);
}