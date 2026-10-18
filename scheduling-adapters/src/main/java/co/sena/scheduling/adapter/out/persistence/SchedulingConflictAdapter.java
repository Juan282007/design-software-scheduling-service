package co.sena.scheduling.adapter.out.persistence;

import co.sena.scheduling.adapter.out.persistence.entity.SchedulingConflictEntity;
import co.sena.scheduling.adapter.out.persistence.repository.SchedulingConflictRepository;
import co.sena.scheduling.application.port.out.SchedulingConflictPort;
import co.sena.scheduling.domain.model.ConflictType;
import co.sena.scheduling.domain.model.SchedulingConflict;
import co.sena.scheduling.domain.service.ConflictDetectionService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SchedulingConflictAdapter implements SchedulingConflictPort {

    private final SchedulingConflictRepository repository;

    public SchedulingConflictAdapter(SchedulingConflictRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SchedulingConflict> saveAll(List<SchedulingConflict> conflicts) {
        List<SchedulingConflictEntity> entities = conflicts.stream().map(this::toEntity).collect(Collectors.toList());
        return repository.saveAll(entities).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<SchedulingConflict> findByScheduleId(UUID scheduleId) {
        return repository.findByScheduleId(scheduleId).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<SchedulingConflict> findByScheduleIdAndBlocking(UUID scheduleId, boolean blocking) {
        return repository.findByScheduleIdAndBlockingPublication(scheduleId, blocking).stream().map(this::toDomain).collect(Collectors.toList());
    }

    /** HU-SCH-006 E1: conflicto por id. */
    @Override
    public java.util.Optional<SchedulingConflict> findById(UUID conflictId) {
        return repository.findById(conflictId).map(this::toDomain);
    }

    /**
     * HU-SCH-006 E1: resolución condicional atómica — delega en la sentencia
     * {@code UPDATE ... WHERE is_resolved = false} del repositorio para que la
     * BD garantice exactamente un ganador ante concurrencia (PR #12 QA).
     */
    @Override
    @Transactional
    public boolean resolveIfPending(UUID conflictId, UUID resolvedBy,
                                    String resolutionReason, Instant resolvedAt) {
        return repository.resolveIfPending(conflictId, resolvedBy, resolvedAt, resolutionReason) == 1;
    }

    /** HU-SCH-005 E3: reconciliación idempotente de la corrida de detección. */
    @Override
    @Transactional
    public List<SchedulingConflict> reconcileDetected(UUID scheduleId, Set<UUID> scopedSessionIds,
                                                      List<SchedulingConflict> detected) {
        if (scheduleId == null) {
            return List.of();
        }
        Set<UUID> scope = scopedSessionIds == null ? Set.of() : scopedSessionIds;

        List<SchedulingConflictEntity> inScope = repository.findByScheduleId(scheduleId).stream()
                .filter(entity -> scope.contains(entity.getSessionAId()))
                .toList();
        Map<String, SchedulingConflictEntity> existingByKey = inScope.stream()
                .collect(Collectors.toMap(this::entityKey, Function.identity(), (first, second) -> first,
                        java.util.LinkedHashMap::new));

        Set<String> detectedKeys = new LinkedHashSet<>();
        List<SchedulingConflictEntity> toInsert = new ArrayList<>();
        if (detected != null) {
            for (SchedulingConflict conflict : detected) {
                String key = ConflictDetectionService.conflictKey(
                        conflict.getConflictType().name(), conflict.getSessionAId(), conflict.getSessionBId());
                if (!detectedKeys.add(key) || existingByKey.containsKey(key)) {
                    continue;
                }
                toInsert.add(toEntity(conflict));
            }
        }

        List<SchedulingConflictEntity> stale = inScope.stream()
                .filter(entity -> !entity.isResolved())
                .filter(entity -> !detectedKeys.contains(entityKey(entity)))
                .toList();
        if (!stale.isEmpty()) {
            repository.deleteAll(stale);
        }
        if (!toInsert.isEmpty()) {
            repository.saveAll(toInsert);
        }
        return repository.findByScheduleId(scheduleId).stream()
                .filter(entity -> scope.contains(entity.getSessionAId()))
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private String entityKey(SchedulingConflictEntity entity) {
        return ConflictDetectionService.conflictKey(
                entity.getConflictType(), entity.getSessionAId(), entity.getSessionBId());
    }

    private SchedulingConflictEntity toEntity(SchedulingConflict d) {
        SchedulingConflictEntity e = new SchedulingConflictEntity();
        e.setId(d.getId() != null ? d.getId() : UUID.randomUUID());
        e.setScheduleId(d.getScheduleId());
        e.setSessionAId(d.getSessionAId());
        e.setSessionBId(d.getSessionBId());
        e.setConflictType(d.getConflictType().name());
        e.setDescription(d.getDescription());
        e.setResolved(d.isResolved());
        e.setDetectedAt(d.getDetectedAt());
        e.setSeverity(d.getSeverity() != null ? d.getSeverity().name() : "MEDIUM");
        e.setResolvedBy(d.getResolvedBy());
        e.setResolvedAt(d.getResolvedAt());
        e.setResolutionReason(d.getResolutionReason());
        e.setBlockingPublication(d.isBlockingPublication());
        return e;
    }

    private SchedulingConflict toDomain(SchedulingConflictEntity e) {
        SchedulingConflict d = new SchedulingConflict();
        d.setId(e.getId());
        d.setScheduleId(e.getScheduleId());
        d.setSessionAId(e.getSessionAId());
        d.setSessionBId(e.getSessionBId());
        d.setConflictType(ConflictType.valueOf(e.getConflictType()));
        d.setDescription(e.getDescription());
        d.setResolved(e.isResolved());
        d.setDetectedAt(e.getDetectedAt());
        d.setSeverity(e.getSeverity() != null ? co.sena.scheduling.domain.model.ConflictSeverity.valueOf(e.getSeverity()) : co.sena.scheduling.domain.model.ConflictSeverity.MEDIUM);
        d.setResolvedBy(e.getResolvedBy());
        d.setResolvedAt(e.getResolvedAt());
        d.setResolutionReason(e.getResolutionReason());
        d.setBlockingPublication(e.isBlockingPublication());
        return d;
    }
}
