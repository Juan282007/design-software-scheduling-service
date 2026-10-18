package co.sena.scheduling.domain.model;

/**
 * Estados válidos del agregado Schedule.
 * Regla de negocio (RN-SCH-01): PUBLISHED es inmutable.
 */
public enum ScheduleStatus {
    DRAFT,
    UNDER_REVIEW,
    PUBLISHED,
    ARCHIVED
}
