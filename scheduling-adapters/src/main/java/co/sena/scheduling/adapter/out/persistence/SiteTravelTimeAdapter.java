package co.sena.scheduling.adapter.out.persistence;

import co.sena.scheduling.adapter.out.persistence.entity.SiteTravelTimeEntity;
import co.sena.scheduling.adapter.out.persistence.repository.SiteTravelTimeRepository;
import co.sena.scheduling.application.port.out.SiteTravelTimePort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * HU-SCH-005 E2 / D-11: lectura de la matriz de traslados (site_travel_time)
 * para detectar INSTRUCTOR_TRAVEL_CONFLICT.
 */
@Component
public class SiteTravelTimeAdapter implements SiteTravelTimePort {

    private final SiteTravelTimeRepository repository;

    public SiteTravelTimeAdapter(SiteTravelTimeRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Integer> getTravelTimeMinutes(UUID originSiteId, UUID destinationSiteId) {
        if (originSiteId == null || destinationSiteId == null || originSiteId.equals(destinationSiteId)) {
            return Optional.empty();
        }
        return repository.findByOriginSiteIdAndDestinationSiteIdAndActiveTrue(originSiteId, destinationSiteId)
                .map(SiteTravelTimeEntity::getTravelMinutes);
    }
}
