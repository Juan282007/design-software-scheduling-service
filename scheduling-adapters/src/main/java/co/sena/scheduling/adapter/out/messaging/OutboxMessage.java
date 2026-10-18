package co.sena.scheduling.adapter.out.messaging;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/**
 * Tabla Outbox para eventos de dominio.
 * Garantiza entrega at-least-once del evento scheduling.schedule.published.
 */
@Entity
@Table(name = "outbox", schema = "scheduling")
public class OutboxMessage {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(nullable = false, length = 16)
    private String status; // PENDING, PUBLISHED, FAILED

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public OutboxMessage() {}

    public OutboxMessage(UUID eventId, String eventType, String payload) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public void markPublished() {
        this.status = "PUBLISHED";
        this.publishedAt = Instant.now();
    }

    public void markFailed() {
        this.status = "FAILED";
    }
}
