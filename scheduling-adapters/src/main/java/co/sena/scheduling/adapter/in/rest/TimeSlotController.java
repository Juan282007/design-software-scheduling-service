package co.sena.scheduling.adapter.in.rest;

import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.sena.scheduling.application.port.in.CreateTimeSlotCommand;
import co.sena.scheduling.application.port.in.TimeSlotFilter;
import co.sena.scheduling.application.port.in.TimeSlotPage;
import co.sena.scheduling.application.port.in.TimeSlotUseCase;
import co.sena.scheduling.application.port.in.UpdateTimeSlotCommand;
import co.sena.scheduling.contract.api.TimeSlotsApi;
import co.sena.scheduling.contract.model.ListSchedules200ResponsePagination;
import co.sena.scheduling.contract.model.ListTimeSlots200Response;
import co.sena.scheduling.contract.model.TimeSlotCreate;
import co.sena.scheduling.contract.model.TimeSlotUpdate;
import co.sena.scheduling.domain.model.ShiftType;
import co.sena.scheduling.domain.model.TimeSlot;
import co.sena.scheduling.domain.service.TimeSlotDomainService;

/**
 * REST adapter (in/rest) del catálogo de franjas horarias.
 * Implementa la interfaz {@link TimeSlotsApi} generada desde el OpenAPI oficial
 * (ADR-006 SDD: el spec es la fuente; la clase de tests que no compile contra él, no mergea).
 */
@RestController
@RequestMapping("/api/v1")
public class TimeSlotController implements TimeSlotsApi {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final TimeSlotUseCase timeSlotUseCase;
    private final TimeSlotDomainService domainService;

    public TimeSlotController(TimeSlotUseCase timeSlotUseCase,
                              TimeSlotDomainService domainService) {
        this.timeSlotUseCase = timeSlotUseCase;
        this.domainService = domainService;
    }

    /** Decisión 17: el sub del JWT de iam-service es el UUID del usuario autenticado. */
    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? UUID.fromString(auth.getName()) : null;
    }

    @Override
    @PreAuthorize("hasAuthority('SCH_VIEW_ALL')")
    public ResponseEntity<ListTimeSlots200Response> listTimeSlots(Integer page, Integer pageSize,
                                                                  String sort, Integer dayOfWeek,
                                                                  String shift) {
        ShiftType normalizedShift = shift == null ? null : domainService.normalizeShift(shift);
        TimeSlotPage result = timeSlotUseCase.list(
            new TimeSlotFilter(dayOfWeek, normalizedShift, page, pageSize, sort));
        ListTimeSlots200Response body = new ListTimeSlots200Response()
            .data(result.getData().stream().map(TimeSlotController::toContract).toList())
            .pagination(new ListSchedules200ResponsePagination()
                .page(result.getPage())
                .pageSize(result.getPageSize())
                .totalItems((int) result.getTotalItems())
                .totalPages(result.getTotalPages()));
        return ResponseEntity.ok(body);
    }

    @Override
    @PreAuthorize("hasAuthority('SCH_CREATE')")
    public ResponseEntity<co.sena.scheduling.contract.model.TimeSlot> createTimeSlot(TimeSlotCreate timeSlotCreate) {
        CreateTimeSlotCommand command = new CreateTimeSlotCommand(
            timeSlotCreate.getName(),
            timeSlotCreate.getDayOfWeek(),
            LocalTime.parse(timeSlotCreate.getStartTime()),
            LocalTime.parse(timeSlotCreate.getEndTime()),
            timeSlotCreate.getShift().getValue());
        TimeSlot created = timeSlotUseCase.create(command);
        return ResponseEntity
            .created(URI.create("/api/v1/time-slots/" + created.getId()))
            .body(toContract(created));
    }

    @Override
    @PreAuthorize("hasAuthority('SCH_VIEW_ALL')")
    public ResponseEntity<co.sena.scheduling.contract.model.TimeSlot> getTimeSlot(UUID id) {
        return ResponseEntity.ok(toContract(timeSlotUseCase.get(id)));
    }

    @Override
    @PreAuthorize("hasAuthority('SCH_EDIT')")
    public ResponseEntity<co.sena.scheduling.contract.model.TimeSlot> updateTimeSlot(UUID id, TimeSlotUpdate timeSlotUpdate) {
        UpdateTimeSlotCommand command = new UpdateTimeSlotCommand(
            timeSlotUpdate.getName(),
            timeSlotUpdate.getDayOfWeek(),
            LocalTime.parse(timeSlotUpdate.getStartTime()),
            LocalTime.parse(timeSlotUpdate.getEndTime()),
            timeSlotUpdate.getShift().getValue());
        return ResponseEntity.ok(toContract(timeSlotUseCase.update(id, command)));
    }

    @Override
    @PreAuthorize("hasAuthority('SCH_EDIT')")
    public ResponseEntity<Void> deleteTimeSlot(UUID id) {
        timeSlotUseCase.delete(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    private static co.sena.scheduling.contract.model.TimeSlot toContract(TimeSlot ts) {
        return new co.sena.scheduling.contract.model.TimeSlot(
            ts.getId(),
            ts.getName(),
            ts.getDayOfWeek(),
            ts.getStartTime().format(TIME_FORMAT),
            ts.getEndTime().format(TIME_FORMAT),
            co.sena.scheduling.contract.model.TimeSlot.ShiftEnum.fromValue(ts.getShift().name()));
    }
}
