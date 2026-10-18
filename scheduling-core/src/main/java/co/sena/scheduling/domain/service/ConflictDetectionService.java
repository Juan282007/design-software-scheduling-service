package co.sena.scheduling.domain.service;

import co.sena.scheduling.application.port.out.EnvironmentAvailabilityProjectionPort;
import co.sena.scheduling.application.port.out.InstructorAvailabilityProjectionPort;
import co.sena.scheduling.application.port.out.SiteTravelTimePort;
import co.sena.scheduling.domain.model.ClassSession;
import co.sena.scheduling.domain.model.ConflictType;
import co.sena.scheduling.domain.model.SchedulingConflict;
import co.sena.scheduling.domain.model.SessionStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * HU-SCH-005: Detección de conflictos de horario — 6 tipos (E2).
 *
 * <p>E1: se invoca en 3 momentos (al guardar sesión POST/PUT, validate, publish).
 * E2: los 6 tipos son reales; los 3 que dependen de read model/traslados se
 * resuelven por puerto (HU-004 y D-11). E6: la detección aplicativa es fuente de
 * verdad y sus errores se propagan; los EXCLUDE de BD son la última barrera.</p>
 */
public class ConflictDetectionService {

    private final EnvironmentAvailabilityProjectionPort envAvailabilityPort;
    private final InstructorAvailabilityProjectionPort instructorAvailabilityPort;
    private final SiteTravelTimePort siteTravelTimePort;

    public ConflictDetectionService(
            EnvironmentAvailabilityProjectionPort envAvailabilityPort,
            InstructorAvailabilityProjectionPort instructorAvailabilityPort,
            SiteTravelTimePort siteTravelTimePort) {
        this.envAvailabilityPort = envAvailabilityPort;
        this.instructorAvailabilityPort = instructorAvailabilityPort;
        this.siteTravelTimePort = siteTravelTimePort;
    }

    /**
     * Detecta los conflictos de una sesión contra las existentes del mismo
     * horario (E1.1 al guardar sesión) + sus conflictos simples (session_b nulo).
     */
    public List<SchedulingConflict> detectForNewSession(UUID scheduleId, ClassSession subject,
                                                        List<ClassSession> existingSessions) {
        List<SchedulingConflict> conflicts = new ArrayList<>();
        if (subject == null || !isActive(subject)) {
            return conflicts;
        }
        detectSingleSessionConflicts(conflicts, scheduleId, subject);
        for (ClassSession other : existingSessions) {
            if (other == null || other.getId().equals(subject.getId()) || !isActive(other)) {
                continue;
            }
            detectPairConflicts(conflicts, scheduleId, subject, other);
        }
        return conflicts;
    }

    /**
     * Validación completa del borrador (E1.2 validate y E1.3 publish): detecta
     * los 6 tipos sobre todas las sesiones activas del horario.
     */
    public List<SchedulingConflict> detectForSchedule(UUID scheduleId, List<ClassSession> sessions) {
        List<SchedulingConflict> conflicts = new ArrayList<>();
        List<ClassSession> active = sessions.stream().filter(s -> s != null && isActive(s)).toList();
        for (ClassSession session : active) {
            detectSingleSessionConflicts(conflicts, scheduleId, session);
        }
        for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
                detectPairConflicts(conflicts, scheduleId, active.get(i), active.get(j));
            }
        }
        return conflicts;
    }

    /** Conflictos simples de una sesión (session_b_id nulo): mantenimiento y disponibilidad. */
    private void detectSingleSessionConflicts(List<SchedulingConflict> out, UUID scheduleId,
                                              ClassSession session) {
        if (session.getEnvironmentId() != null
                && envAvailabilityPort.isEnvironmentUnavailable(session.getEnvironmentId(),
                                                                session.getSessionDate())) {
            out.add(new SchedulingConflict(scheduleId, session.getId(), null,
                    ConflictType.ENVIRONMENT_MAINTENANCE,
                    "Ambiente " + session.getEnvironmentId() + " en mantenimiento el día "
                            + session.getSessionDate()));
        }
        if (session.getInstructorId() != null
                && instructorAvailabilityPort.isInstructorUnavailable(session.getInstructorId(),
                                                                      session.getCompetencyId())) {
            out.add(new SchedulingConflict(scheduleId, session.getId(), null,
                    ConflictType.INSTRUCTOR_UNAVAILABLE,
                    "Instructor " + session.getInstructorId() + " no disponible el día "
                            + session.getSessionDate()));
        }
    }

    /** Conflictos de par (2 sesiones de la misma ficha en la misma fecha). */
    private void detectPairConflicts(List<SchedulingConflict> out, UUID scheduleId,
                                     ClassSession a, ClassSession b) {
        if (!isActive(a) || !isActive(b) || !sameDay(a, b)) {
            return;
        }
        if (overlaps(a, b)) {
            if (sameValue(a.getInstructorId(), b.getInstructorId())) {
                out.add(new SchedulingConflict(scheduleId, a.getId(), b.getId(),
                        ConflictType.INSTRUCTOR_DOUBLE_BOOKED,
                        "Instructor " + a.getInstructorId() + " doble-asignado el día " + a.getSessionDate()));
            }
            if (sameValue(a.getEnvironmentId(), b.getEnvironmentId())) {
                out.add(new SchedulingConflict(scheduleId, a.getId(), b.getId(),
                        ConflictType.ENVIRONMENT_DOUBLE_BOOKED,
                        "Ambiente " + a.getEnvironmentId() + " doble-asignado el día " + a.getSessionDate()));
            }
            out.add(new SchedulingConflict(scheduleId, a.getId(), b.getId(),
                    ConflictType.SESSIONS_OVERLAP,
                    "Sesiones solapadas el día " + a.getSessionDate()));
            return;
        }
        // Traslado: mismo instructor, ambientes distintos y sin solapamiento.
        if (sameValue(a.getInstructorId(), b.getInstructorId())
                && !sameValue(a.getEnvironmentId(), b.getEnvironmentId())) {
            detectTravelConflict(out, scheduleId, a, b);
        }
    }

    /**
     * INSTRUCTOR_TRAVEL_CONFLICT (D-11): el instructor no alcanza a trasladarse
     * entre los dos ambientes antes de la segunda sesión.
     */
    private void detectTravelConflict(List<SchedulingConflict> out, UUID scheduleId,
                                      ClassSession a, ClassSession b) {
        ClassSession earlier = a.getStartTime().isBefore(b.getStartTime()) ? a : b;
        ClassSession later = earlier == a ? b : a;
        UUID origin = earlier.getEnvironmentId();
        UUID destination = later.getEnvironmentId();
        if (origin == null || destination == null) {
            return;
        }
        Optional<Integer> travelMinutes = siteTravelTimePort.getTravelTimeMinutes(origin, destination);
        if (travelMinutes.isEmpty()) {
            return;
        }
        long minutesBetween = Duration.between(earlier.getEndTime(), later.getStartTime()).toMinutes();
        if (minutesBetween < travelMinutes.get()) {
            out.add(new SchedulingConflict(scheduleId, later.getId(), earlier.getId(),
                    ConflictType.INSTRUCTOR_TRAVEL_CONFLICT,
                    "Traslado insuficiente entre ambientes: se requieren " + travelMinutes.get()
                            + " min y hay " + minutesBetween + " min entre sesiones"));
        }
    }

    /** Sesión activa: no borrada lógicamente ni cancelada. */
    private boolean isActive(ClassSession session) {
        return session.isActive() && session.getStatus() != SessionStatus.CANCELLED;
    }

    private boolean sameDay(ClassSession a, ClassSession b) {
        return a.getSessionDate() != null && a.getSessionDate().equals(b.getSessionDate());
    }

    private boolean sameValue(UUID a, UUID b) {
        return a != null && a.equals(b);
    }

    private boolean overlaps(ClassSession a, ClassSession b) {
        return a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
    }

    /**
     * Clave idempotente de un conflicto para reconciliación (E3): tipo + par de
     * sesiones en orden canónico (nulo para conflictos simples).
     */
    public static String conflictKey(String conflictType, UUID sessionAId, UUID sessionBId) {
        String a = sessionAId == null ? "" : sessionAId.toString();
        String b = sessionBId == null ? "" : sessionBId.toString();
        if (b.compareTo(a) < 0) {
            String tmp = a;
            a = b;
            b = tmp;
        }
        return conflictType + "|" + a + "|" + b;
    }
}
