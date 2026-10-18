package co.sena.scheduling.adapter.out.persistence.repository;

import co.sena.scheduling.adapter.out.persistence.entity.SiteTravelTimeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteTravelTimeRepository extends JpaRepository<SiteTravelTimeEntity, UUID> {

    Optional<SiteTravelTimeEntity> findByOriginSiteIdAndDestinationSiteIdAndActiveTrue(
            UUID originSiteId, UUID destinationSiteId);
}
