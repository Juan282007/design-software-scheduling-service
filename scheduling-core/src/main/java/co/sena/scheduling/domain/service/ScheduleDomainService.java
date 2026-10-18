package co.sena.scheduling.domain.service;

import co.sena.scheduling.domain.exception.ScheduleValidationException;
import co.sena.scheduling.domain.model.*;
import java.util.List;
import java.util.UUID;

/**
 * Servicio de dominio para la lógica de negocio del Schedule.
 * Contiene reglas que no pertenecen a una sola entidad.
 */
public class ScheduleDomainService {

    /**
     * Valida que un horario pueda ser creado para la ficha y período dados.
     * Regla: solo un PUBLISHED por (ficha_id, period) es permitido.
     */
    public boolean canCreateSchedule(UUID fichaId, String period, List<Schedule> existingSchedules) {
        return existingSchedules.stream()
            .noneMatch(s -> s.getFichaId().equals(fichaId)
                && s.getPeriod().equals(period)
                && s.getStatus() == ScheduleStatus.PUBLISHED);
    }

    /** Validación de creación HU-002 E1: ficha_id + period requeridos, name opcional <= 200. */
    public void validateCreate(UUID fichaId, String period, String name) {
        if (fichaId == null) {
            throw new ScheduleValidationException("ficha_id", "REQUIRED", "ficha_id es obligatorio");
        }
        if (period == null || period.isBlank()) {
            throw new ScheduleValidationException("period", "REQUIRED", "period es obligatorio");
        }
        if (period.length() > 10) {
            throw new ScheduleValidationException("period", "MAX_LENGTH", "period no puede superar 10 caracteres");
        }
        if (name != null && name.length() > 200) {
            throw new ScheduleValidationException("name", "MAX_LENGTH", "name no puede superar 200 caracteres");
        }
    }

    /** Validación de edición HU-002 E3: solo name (opcional <= 200). */
    public void validateUpdateName(String name) {
        if (name != null && name.length() > 200) {
            throw new ScheduleValidationException("name", "MAX_LENGTH", "name no puede superar 200 caracteres");
        }
    }

    /**
     * Verifica si un schedule tiene conflictos sin resolver.
     */
    public boolean hasUnresolvedConflicts(Schedule schedule, List<SchedulingConflict> conflicts) {
        return conflicts.stream()
            .anyMatch(c -> c.getScheduleId().equals(schedule.getId()) && !c.isResolved());
    }

    /**
     * Verifica que una sesión se pueda agregar al borrador.
     * Regla: solo DRAFT puede tener sesiones agregadas.
     */
    public boolean canAddSession(Schedule schedule) {
        return schedule.getStatus() == ScheduleStatus.DRAFT;
    }

    /**
     * Valida que una sesión cumpla las reglas de duración (1-6 horas).
     */
    public boolean validateSessionDuration(java.time.LocalTime startTime, java.time.LocalTime endTime) {
        int minutes = (int) java.time.Duration.between(startTime, endTime).toMinutes();
        return minutes >= 60 && minutes <= 360;
    }
}
