package co.sena.scheduling.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Agregado raíz: horario académico.
 * Máquina de estados: DRAFT → UNDER_REVIEW → PUBLISHED → ARCHIVED
 * Invariante: solo un PUBLISHED por (ficha_id, period) — índice parcial BD.
 * Invariante: PUBLISHED es inmutable.
 */
public class Schedule {

    private UUID id;
    private UUID fichaId;
    private String period;
    private String name;
    private ScheduleStatus status;
    private Instant publishedAt;
    private UUID publishedBy;
    private int rowVersion;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean active = true;
    private Instant deletedAt;
    private UUID deletedBy;
    private List<ClassSession> sessions;

    public Schedule() {}

    public Schedule(UUID id, UUID fichaId, String period, String name, ScheduleStatus status,
                    UUID createdBy) {
        this.id = id;
        this.fichaId = fichaId;
        this.period = period;
        this.name = name;
        this.status = status;
        this.createdBy = createdBy;
        this.rowVersion = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getFichaId() { return fichaId; }
    public void setFichaId(UUID fichaId) { this.fichaId = fichaId; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ScheduleStatus getStatus() { return status; }
    public void setStatus(ScheduleStatus status) { this.status = status; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public UUID getPublishedBy() { return publishedBy; }
    public void setPublishedBy(UUID publishedBy) { this.publishedBy = publishedBy; }

    public int getRowVersion() { return rowVersion; }
    public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<ClassSession> getSessions() { return sessions; }
    public void setSessions(List<ClassSession> sessions) { this.sessions = sessions; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public UUID getDeletedBy() { return deletedBy; }
    public void setDeletedBy(UUID deletedBy) { this.deletedBy = deletedBy; }

    public void deactivate(Instant at, UUID actor) {
        this.active = false;
        this.deletedAt = at;
        this.deletedBy = actor;
    }

    public void incrementRowVersion() {
        this.rowVersion++;
    }

    /**
     * Transiciona el estado a UNDER_REVIEW (válido desde DRAFT).
     */
    public void validate() {
        if (this.status != ScheduleStatus.DRAFT) {
            throw new IllegalStateException(
                "Solo se puede validar un horario en estado DRAFT. Estado actual: " + this.status);
        }
        this.status = ScheduleStatus.UNDER_REVIEW;
        this.updatedAt = Instant.now();
    }

    /**
     * Transiciona el estado a PUBLISHED (válido desde UNDER_REVIEW).
     */
    public void publish(UUID publishedBy) {
        if (this.status != ScheduleStatus.UNDER_REVIEW) {
            throw new IllegalStateException(
                "Solo se puede publicar un horario en estado UNDER_REVIEW. Estado actual: " + this.status);
        }
        this.status = ScheduleStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.publishedBy = publishedBy;
        this.updatedAt = Instant.now();
    }

    /**
     * Transiciona el estado a ARCHIVED (válido desde PUBLISHED).
     */
    public void archive() {
        if (this.status != ScheduleStatus.PUBLISHED) {
            throw new IllegalStateException(
                "Solo se puede archivar un horario en estado PUBLISHED. Estado actual: " + this.status);
        }
        this.status = ScheduleStatus.ARCHIVED;
    }

    /**
     * Verifica si el horario puede ser editado (solo DRAFT).
     */
    public boolean isEditable() {
        return this.status == ScheduleStatus.DRAFT;
    }
}
