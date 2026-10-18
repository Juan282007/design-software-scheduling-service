package co.sena.scheduling.adapter.in.rest;

import co.sena.scheduling.application.port.in.ArchiveScheduleUseCase;
import co.sena.scheduling.application.port.in.CreateScheduleCommand;
import co.sena.scheduling.application.port.in.CreateScheduleUseCase;
import co.sena.scheduling.application.port.in.PublishScheduleUseCase;
import co.sena.scheduling.application.port.in.ScheduleAccess;
import co.sena.scheduling.application.port.in.ScheduleFilter;
import co.sena.scheduling.application.port.in.SchedulePage;
import co.sena.scheduling.application.port.in.ScheduleUseCase;
import co.sena.scheduling.application.port.in.UpdateScheduleCommand;
import co.sena.scheduling.application.port.in.ValidateScheduleUseCase;
import co.sena.scheduling.application.port.in.ListConflictsUseCase;
import co.sena.scheduling.application.port.out.PersistencePort;
import co.sena.scheduling.domain.exception.ScheduleAccessDeniedException;
import co.sena.scheduling.domain.exception.ScheduleValidationException;
import co.sena.scheduling.domain.model.Schedule;
import co.sena.scheduling.domain.model.SchedulingConflict;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Controller para el agregado Schedule.
 * Endpoints de CRUD y transiciones de estado.
 */
@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final CreateScheduleUseCase createScheduleUseCase;
    private final ValidateScheduleUseCase validateScheduleUseCase;
    private final PublishScheduleUseCase publishScheduleUseCase;
    private final ScheduleUseCase scheduleUseCase;
    private final PersistencePort persistencePort;
    private final ListConflictsUseCase listConflictsUseCase;
    private final ArchiveScheduleUseCase archiveScheduleUseCase;

    public ScheduleController(CreateScheduleUseCase createScheduleUseCase,
                              ValidateScheduleUseCase validateScheduleUseCase,
                              PublishScheduleUseCase publishScheduleUseCase,
                              ScheduleUseCase scheduleUseCase,
                              PersistencePort persistencePort,
                              ListConflictsUseCase listConflictsUseCase,
                              ArchiveScheduleUseCase archiveScheduleUseCase) {
        this.createScheduleUseCase = createScheduleUseCase;
        this.validateScheduleUseCase = validateScheduleUseCase;
        this.publishScheduleUseCase = publishScheduleUseCase;
        this.scheduleUseCase = scheduleUseCase;
        this.persistencePort = persistencePort;
        this.listConflictsUseCase = listConflictsUseCase;
        this.archiveScheduleUseCase = archiveScheduleUseCase;
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
     * Resuelve el scope E2 desde el claim `features` (pares CODE:SCOPE, HU-IAM-010).
     * GLOBAL: sin filtro. TRAINING_CENTER: filtro por training_center_id del JWT
     * contra la proyeccion local ficha-centro.
     * OWN_SCHEDULE de SCH_VIEW_OWN: filtro por actor_id. Sin JWT
     * (tests/scheduler): legacy sin filtro.
     */
    private static final java.util.List<String> E2_FEATURES =
        java.util.List.of("SCH_VIEW_ALL", "SCH_VIEW_OWN");

    private ScheduleAccess resolveE2Access() {
        return ScheduleScope.resolve(E2_FEATURES);
    }

    private static String featureOf(String pair) {
        int i = pair.indexOf(':');
        return i < 0 ? pair : pair.substring(0, i);
    }

    private static String scopeOf(String pair) {
        int i = pair.indexOf(':');
        return i < 0 ? "" : pair.substring(i + 1);
    }

    /**
     * POST /api/v1/schedules — Crear borrador de horario (HU-002 E1).
     * Feature: SCH_CREATE. 409 si ya hay PUBLISHED para (ficha_id, period).
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SCH_CREATE')")
    public ResponseEntity<ScheduleResponse> createSchedule(@RequestBody ScheduleCreateRequest request) {
        CreateScheduleCommand command = new CreateScheduleCommand(
            request.getFichaId(), request.getPeriod(), request.getName(), currentUserId());
        Schedule created = createScheduleUseCase.create(command);
        return ResponseEntity
            .created(URI.create("/api/v1/schedules/" + created.getId()))
            .body(ScheduleResponse.from(created));
    }

    /**
     * GET /api/v1/schedules — Listar horarios (HU-002 E2, paginado + filtros).
     * Feature: SCH_VIEW_ALL / SCH_VIEW_OWN (scope HU-008 solo filtra, no se agrega aquí).
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCH_VIEW_ALL','SCH_VIEW_OWN')")
    public ResponseEntity<ScheduleListResponse> listSchedules(
            @RequestParam(name = "ficha_id", required = false) UUID fichaId,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "page_size", required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(name = "sort", required = false) String sort) {
        SchedulePage result = scheduleUseCase.list(
            new ScheduleFilter(fichaId, period, status, from, to,
                page == null ? 1 : page, pageSize == null ? 20 : pageSize, sort),
            resolveE2Access());
        return ResponseEntity.ok(ScheduleListResponse.from(result));
    }

    /**
     * GET /api/v1/schedules/{id} — Detalle del horario (HU-002 E2).
     * Feature: SCH_VIEW_ALL / SCH_VIEW_OWN (contrato: sin SCH_VIEW_FICHA).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCH_VIEW_ALL','SCH_VIEW_OWN')")
    public ResponseEntity<ScheduleResponse> getSchedule(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ScheduleResponse.from(scheduleUseCase.get(id, resolveE2Access())));
    }

    /**
     * PUT /api/v1/schedules/{id} — Editar borrador, solo name + row_version (HU-002 E3).
     * Feature: SCH_EDIT. 409 SCHEDULE_IMMUTABLE si no está DRAFT, 409 ROW_VERSION_MISMATCH.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCH_EDIT')")
    public ResponseEntity<ScheduleResponse> updateSchedule(
            @PathVariable("id") UUID id,
            @RequestBody ScheduleUpdateRequest request) {
        if (request == null || request.getRowVersion() == null) {
            throw new ScheduleValidationException("row_version", "REQUIRED", "row_version es obligatorio");
        }
        Schedule updated = scheduleUseCase.update(
            id, new UpdateScheduleCommand(request.getName(), request.getRowVersion()));
        return ResponseEntity.ok(ScheduleResponse.from(updated));
    }

    /**
     * DELETE /api/v1/schedules/{id}?row_version=N — Borrado lógico solo DRAFT (HU-002 E4).
     * Feature: SCH_DELETE_DRAFT. Trigger BD rechaza borrado físico de PUBLISHED.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCH_DELETE_DRAFT')")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable("id") UUID id,
            @RequestParam(name = "row_version") Integer rowVersion) {
        if (rowVersion == null) {
            throw new ScheduleValidationException("row_version", "REQUIRED", "row_version es obligatorio");
        }
        scheduleUseCase.delete(id, rowVersion, currentUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/schedules/{id}/validate — Validar horario.
     * Transiciona DRAFT → UNDER_REVIEW (detecta conflictos, E1.2).
     * Feature: SCH_EDIT. row_version en el body (RowVersionRequest del contrato).
     */
    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('SCH_EDIT')")
    public ResponseEntity<ScheduleResponse> validateSchedule(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) RowVersionRequest body,
            @RequestParam(name = "row_version", required = false) Integer rowVersionParam) {
        Integer rowVersion = resolveRowVersion(body, rowVersionParam);
        Schedule schedule = validateScheduleUseCase.validate(id, rowVersion);
        return ResponseEntity.ok(ScheduleResponse.from(schedule));
    }

    /**
     * POST /api/v1/schedules/{id}/publish — Publicar horario.
     * Transiciona UNDER_REVIEW → PUBLISHED; 409 si hay conflictos bloqueantes (E1.3/E3).
     * Feature: SCH_PUBLISH. row_version en el body; publishedBy siempre sale del JWT.
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('SCH_PUBLISH')")
    public ResponseEntity<ScheduleResponse> publishSchedule(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) RowVersionRequest body,
            @RequestParam(name = "row_version", required = false) Integer rowVersionParam) {
        Integer rowVersion = resolveRowVersion(body, rowVersionParam);
        Schedule schedule = publishScheduleUseCase.publish(id, currentUserId(), rowVersion);
        return ResponseEntity.ok(ScheduleResponse.from(schedule));
    }

    /** row_version del body (contrato) con fallback a query param (compatibilidad). */
    private Integer resolveRowVersion(RowVersionRequest body, Integer queryParam) {
        if (body != null && body.rowVersion() != null) {
            return body.rowVersion();
        }
        if (queryParam != null) {
            return queryParam;
        }
        throw new ScheduleValidationException("row_version", "REQUIRED", "row_version es obligatorio");
    }

    /** Body del contrato RowVersionRequest para validate/publish/archive. */
    public record RowVersionRequest(@JsonProperty("row_version") Integer rowVersion) {}

    /**
     * GET /api/v1/schedules/{id}/conflicts — Listar conflictos del borrador (HU-SCH-005 E5).
     * Paginado, filtros is_resolved y conflict_type. Feature: SCH_CONFLICT_VIEW
     */
    @GetMapping("/{id}/conflicts")
    @PreAuthorize("hasAuthority('SCH_CONFLICT_VIEW')")
    public ResponseEntity<ConflictListResponse> listConflicts(
            @PathVariable("id") UUID id,
            @RequestParam(name = "is_resolved", required = false) Boolean isResolved,
            @RequestParam(name = "conflict_type", required = false) String conflictType,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "page_size", required = false, defaultValue = "20") Integer pageSize) {
        int safePage = page == null ? 1 : Math.max(page, 1);
        int safePageSize = pageSize == null ? 20 : Math.max(pageSize, 1);
        ListConflictsUseCase.Result result =
                listConflictsUseCase.listConflicts(id, isResolved, conflictType, safePage, safePageSize);
        List<ConflictResponse> response = result.items().stream().map(c -> new ConflictResponse(
                c.getId(), c.getScheduleId(), c.getSessionAId(), c.getSessionBId(),
                c.getConflictType().name(), c.getDescription(), c.isResolved(), c.getDetectedAt()
        )).toList();
        int totalPages = (int) ((result.totalItems() + safePageSize - 1) / safePageSize);
        return ResponseEntity.ok(new ConflictListResponse(response,
                new Pagination(safePage, safePageSize, result.totalItems(), totalPages)));
    }

    /**
     * POST /api/v1/schedules/{id}/archive — Archivar horario.
     * Transiciona PUBLISHED → ARCHIVED.
     * Feature: SCH_ARCHIVE. row_version en el body (RowVersionRequest del contrato).
     */
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('SCH_ARCHIVE')")
    public ResponseEntity<ScheduleResponse> archiveSchedule(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) RowVersionRequest body,
            @RequestParam(name = "row_version", required = false) Integer rowVersionParam) {
        Integer rowVersion = resolveRowVersion(body, rowVersionParam);
        Schedule schedule = archiveScheduleUseCase.archive(id, rowVersion);
        return ResponseEntity.ok(ScheduleResponse.from(schedule));
    }

    /** Envelope del contrato SchedulingConflict (E4: G1 no se expone). */
    public record ConflictListResponse(List<ConflictResponse> data, Pagination pagination) {}
    public record ConflictResponse(UUID id, @JsonProperty("schedule_id") UUID scheduleId,
                                   @JsonProperty("session_a_id") UUID sessionAId, @JsonProperty("session_b_id") UUID sessionBId,
                                   @JsonProperty("conflict_type") String conflictType, String description,
                                   @JsonProperty("is_resolved") boolean isResolved,
                                   @JsonProperty("detected_at") Instant detectedAt) {}

    /** Respuesta contrato Schedule (snake_case, guidelines §1). */
    public record ScheduleResponse(
        UUID id,
        @JsonProperty("ficha_id") UUID fichaId,
        String period,
        String name,
        String status,
        @JsonProperty("published_at") Instant publishedAt,
        @JsonProperty("published_by") UUID publishedBy,
        @JsonProperty("row_version") int rowVersion,
        @JsonProperty("created_by") UUID createdBy,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {
        static ScheduleResponse from(Schedule s) {
            return new ScheduleResponse(s.getId(), s.getFichaId(), s.getPeriod(), s.getName(),
                s.getStatus().name(), s.getPublishedAt(), s.getPublishedBy(),
                s.getRowVersion(), s.getCreatedBy(), s.getCreatedAt(), s.getUpdatedAt());
        }
    }

    /** Envelope paginado contrato: {data, pagination}. */
    public record ScheduleListResponse(List<ScheduleResponse> data, Pagination pagination) {
        static ScheduleListResponse from(SchedulePage page) {
            return new ScheduleListResponse(
                page.getData().stream().map(ScheduleResponse::from).toList(),
                new Pagination(page.getPage(), page.getPageSize(),
                    page.getTotalItems(), page.getTotalPages()));
        }
    }

    public record Pagination(
        int page,
        @JsonProperty("page_size") int pageSize,
        @JsonProperty("total_items") long totalItems,
        @JsonProperty("total_pages") int totalPages) {
    }
}
