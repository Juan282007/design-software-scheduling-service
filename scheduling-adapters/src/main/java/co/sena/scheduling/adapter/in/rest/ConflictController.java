package co.sena.scheduling.adapter.in.rest;

import co.sena.scheduling.application.port.in.ResolveConflictUseCase;
import co.sena.scheduling.domain.model.SchedulingConflict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller para la resolución de conflictos (HU-SCH-006).
 */
@RestController
public class ConflictController {

    private final ResolveConflictUseCase resolveConflictUseCase;

    public ConflictController(ResolveConflictUseCase resolveConflictUseCase) {
        this.resolveConflictUseCase = resolveConflictUseCase;
    }

    /** Decisión 17: el sub del JWT de iam-service es el UUID del usuario autenticado. */
    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * POST /api/v1/conflicts/{id}/resolve — Marca un conflicto como resuelto (E1).
     * Feature: SCH_CONFLICT_RESOLVE. 404 si no existe, 409 si ya está resuelto.
     * Sin body (contrato): resolved_by desde el JWT y resolved_at del servidor (E2).
     * Devuelve el schema SchedulingConflict sin columnas G1 (E4).
     */
    @PostMapping("/api/v1/conflicts/{id}/resolve")
    @PreAuthorize("hasAuthority('SCH_CONFLICT_RESOLVE')")
    public ResponseEntity<ScheduleController.ConflictResponse> resolveConflict(
            @PathVariable("id") UUID id) {
        SchedulingConflict conflict = resolveConflictUseCase.resolve(id, currentUserId());
        return ResponseEntity.ok(new ScheduleController.ConflictResponse(
                conflict.getId(), conflict.getScheduleId(), conflict.getSessionAId(),
                conflict.getSessionBId(), conflict.getConflictType().name(),
                conflict.getDescription(), conflict.isResolved(), conflict.getDetectedAt()));
    }
}
