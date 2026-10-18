package co.sena.scheduling.adapter.in.rest;

import co.sena.scheduling.application.port.in.CreateSessionCommand;
import co.sena.scheduling.application.port.in.ScheduleAccess;
import co.sena.scheduling.application.port.in.SessionFilter;
import co.sena.scheduling.application.port.in.SessionPage;
import co.sena.scheduling.application.port.in.SessionUseCase;
import co.sena.scheduling.application.port.in.UpdateSessionCommand;
import co.sena.scheduling.domain.exception.SessionValidationException;
import co.sena.scheduling.domain.model.ClassSession;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * REST adapter de sesiones de clase (HU-003). E6: no expone execution_status.
 */
@RestController
@RequestMapping("/api/v1")
public class SessionController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<String> READ_FEATURES = List.of("SCH_VIEW_ALL", "SCH_VIEW_OWN");

    private final SessionUseCase sessionUseCase;

    public SessionController(SessionUseCase sessionUseCase) {
        this.sessionUseCase = sessionUseCase;
    }

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
     * POST /schedules/{id}/sessions — Agregar sesión al borrador (E1).
     * Feature: SCH_EDIT. 409 SCHEDULE_IMMUTABLE si el padre no es DRAFT.
     */
    @PostMapping("/schedules/{id}/sessions")
    @PreAuthorize("hasAuthority('SCH_EDIT')")
    public ResponseEntity<SessionResponse> addSession(
            @PathVariable("id") UUID scheduleId,
            @RequestBody SessionCreateRequest request) {
        if (request == null) {
            throw new SessionValidationException("payload", "REQUIRED", "El payload es obligatorio");
        }
        ClassSession created = sessionUseCase.create(new CreateSessionCommand(
            scheduleId, request.getCompetencyId(), request.getEnvironmentId(),
            request.getInstructorId(), request.getTimeSlotId(), request.getSessionDate(),
            request.getNotes()), currentUserId());
        return ResponseEntity
            .created(URI.create("/api/v1/sessions/" + created.getId()))
            .body(SessionResponse.from(created));
    }

    /**
     * GET /schedules/{id}/sessions — Sesiones del horario (E2 lectura).
     * Feature: SCH_VIEW_ALL / SCH_VIEW_OWN (scope reutilizado de HU-002).
     */
    @GetMapping("/schedules/{id}/sessions")
    @PreAuthorize("hasAnyAuthority('SCH_VIEW_ALL','SCH_VIEW_OWN')")
    public ResponseEntity<SessionListResponse> listScheduleSessions(
            @PathVariable("id") UUID scheduleId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "page_size", required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(name = "sort", required = false) String sort) {
        ScheduleAccess access = ScheduleScope.resolve(READ_FEATURES);
        SessionPage result = sessionUseCase.list(
            new SessionFilter(scheduleId, ownerForcedInstructor(access, null), null,
                status, null, null,
                page == null ? 1 : page, pageSize == null ? 20 : pageSize, sort),
            access);
        return ResponseEntity.ok(SessionListResponse.from(result));
    }

    /**
     * GET /sessions — Lista global (filtros + scope; OWN_SCHEDULE fuerza instructor
     * del JWT e ignora el query param — HU-008 E2).
     */
    @GetMapping("/sessions")
    @PreAuthorize("hasAnyAuthority('SCH_VIEW_ALL','SCH_VIEW_OWN')")
    public ResponseEntity<SessionListResponse> listSessions(
            @RequestParam(name = "schedule_id", required = false) UUID scheduleId,
            @RequestParam(name = "instructor_id", required = false) UUID instructorId,
            @RequestParam(name = "environment_id", required = false) UUID environmentId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "page_size", required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(name = "sort", required = false) String sort) {
        ScheduleAccess access = ScheduleScope.resolve(READ_FEATURES);
        SessionPage result = sessionUseCase.list(
            new SessionFilter(scheduleId, ownerForcedInstructor(access, instructorId), environmentId,
                status, from, to,
                page == null ? 1 : page, pageSize == null ? 20 : pageSize, sort),
            access);
        return ResponseEntity.ok(SessionListResponse.from(result));
    }

    /** HU-008 E2: con OWN_SCHEDULE se ignora el query param y se fuerza el del JWT. */
    private UUID ownerForcedInstructor(ScheduleAccess access, UUID requested) {
        if (access != null && access.getType() == ScheduleAccess.Type.OWNER_INSTRUCTOR) {
            return access.getInstructorId();
        }
        return requested;
    }

    @GetMapping("/sessions/{id}")
    @PreAuthorize("hasAnyAuthority('SCH_VIEW_ALL','SCH_VIEW_OWN')")
    public ResponseEntity<SessionResponse> getSession(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(SessionResponse.from(
            sessionUseCase.get(id, ScheduleScope.resolve(READ_FEATURES))));
    }

    /**
     * PUT /sessions/{id} — Editar sesión del borrador (E5).
     * Feature: SCH_EDIT. 409 si el padre no es DRAFT o hay doble-booking.
     */
    @PutMapping("/sessions/{id}")
    @PreAuthorize("hasAuthority('SCH_EDIT')")
    public ResponseEntity<SessionResponse> updateSession(
            @PathVariable("id") UUID id,
            @RequestBody SessionUpdateRequest request) {
        if (request == null) {
            throw new SessionValidationException("payload", "REQUIRED", "El payload es obligatorio");
        }
        return ResponseEntity.ok(SessionResponse.from(sessionUseCase.update(id,
            new UpdateSessionCommand(request.getInstructorId(), request.getEnvironmentId(),
                request.getTimeSlotId(), request.getNotes(), request.isNotesPresent()))));
    }

    /**
     * POST /sessions/{id}/cancel — ACTIVE → CANCELLED + evento (E5).
     * Feature: SCH_EDIT. 409 si ya está CANCELLED o el padre no es DRAFT.
     */
    @PostMapping("/sessions/{id}/cancel")
    @PreAuthorize("hasAuthority('SCH_EDIT')")
    public ResponseEntity<SessionResponse> cancelSession(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(SessionResponse.from(sessionUseCase.cancel(id)));
    }

    /**
     * DELETE /sessions/{id} — Soft delete del borrador (E5). Feature: SCH_EDIT.
     */
    @DeleteMapping("/sessions/{id}")
    @PreAuthorize("hasAuthority('SCH_EDIT')")
    public ResponseEntity<Void> deleteSession(@PathVariable("id") UUID id) {
        sessionUseCase.delete(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    public static class SessionCreateRequest {
        @JsonProperty("competency_id")
        private UUID competencyId;
        @JsonProperty("environment_id")
        private UUID environmentId;
        @JsonProperty("instructor_id")
        private UUID instructorId;
        @JsonProperty("time_slot_id")
        private UUID timeSlotId;
        @JsonProperty("session_date")
        private LocalDate sessionDate;
        private String notes;

        public UUID getCompetencyId() { return competencyId; }
        public void setCompetencyId(UUID competencyId) { this.competencyId = competencyId; }
        public UUID getEnvironmentId() { return environmentId; }
        public void setEnvironmentId(UUID environmentId) { this.environmentId = environmentId; }
        public UUID getInstructorId() { return instructorId; }
        public void setInstructorId(UUID instructorId) { this.instructorId = instructorId; }
        public UUID getTimeSlotId() { return timeSlotId; }
        public void setTimeSlotId(UUID timeSlotId) { this.timeSlotId = timeSlotId; }
        public LocalDate getSessionDate() { return sessionDate; }
        public void setSessionDate(LocalDate sessionDate) { this.sessionDate = sessionDate; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class SessionUpdateRequest {
        @JsonProperty("instructor_id")
        private UUID instructorId;
        @JsonProperty("environment_id")
        private UUID environmentId;
        @JsonProperty("time_slot_id")
        private UUID timeSlotId;
        private String notes;
        private boolean notesPresent;

        public UUID getInstructorId() { return instructorId; }
        public void setInstructorId(UUID instructorId) { this.instructorId = instructorId; }
        public UUID getEnvironmentId() { return environmentId; }
        public void setEnvironmentId(UUID environmentId) { this.environmentId = environmentId; }
        public UUID getTimeSlotId() { return timeSlotId; }
        public void setTimeSlotId(UUID timeSlotId) { this.timeSlotId = timeSlotId; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; this.notesPresent = true; }
        public boolean isNotesPresent() { return notesPresent; }
    }

    /** Respuesta contrato Session (snake_case; sin execution_status — E6). */
    public record SessionResponse(
        UUID id,
        @JsonProperty("schedule_id") UUID scheduleId,
        @JsonProperty("competency_id") UUID competencyId,
        @JsonProperty("environment_id") UUID environmentId,
        @JsonProperty("instructor_id") UUID instructorId,
        @JsonProperty("time_slot_id") UUID timeSlotId,
        @JsonProperty("session_date") LocalDate sessionDate,
        @JsonProperty("day_of_week") int dayOfWeek,
        @JsonProperty("start_time") String startTime,
        @JsonProperty("end_time") String endTime,
        String status,
        String notes,
        @JsonProperty("updated_at") java.time.Instant updatedAt) {
        static SessionResponse from(ClassSession s) {
            return new SessionResponse(s.getId(), s.getScheduleId(), s.getCompetencyId(),
                s.getEnvironmentId(), s.getInstructorId(), s.getTimeSlotId(), s.getSessionDate(),
                s.getDayOfWeek(), format(s.getStartTime()), format(s.getEndTime()),
                s.getStatus() == null ? null : s.getStatus().name(),
                s.getNotes(), s.getUpdatedAt());
        }

        private static String format(LocalTime time) {
            return time == null ? null : time.format(TIME_FORMAT);
        }
    }

    public record SessionListResponse(List<SessionResponse> data, Pagination pagination) {
        static SessionListResponse from(SessionPage page) {
            return new SessionListResponse(
                page.getData().stream().map(SessionResponse::from).toList(),
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
