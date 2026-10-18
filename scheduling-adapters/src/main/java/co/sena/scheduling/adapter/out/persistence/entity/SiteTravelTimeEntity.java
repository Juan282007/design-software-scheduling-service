package co.sena.scheduling.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * D-11: matriz de tiempos de traslado entre sedes
 * (refs lógicas cross-servicio, sin FK física).
 */
@Entity
@Table(name = "site_travel_time", schema = "scheduling")
public class SiteTravelTimeEntity {

    @Id
    private UUID id;

    @Column(name = "origin_site_id", nullable = false)
    private UUID originSiteId;

    @Column(name = "destination_site_id", nullable = false)
    private UUID destinationSiteId;

    @Column(name = "travel_minutes", nullable = false)
    private int travelMinutes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOriginSiteId() { return originSiteId; }
    public void setOriginSiteId(UUID originSiteId) { this.originSiteId = originSiteId; }

    public UUID getDestinationSiteId() { return destinationSiteId; }
    public void setDestinationSiteId(UUID destinationSiteId) { this.destinationSiteId = destinationSiteId; }

    public int getTravelMinutes() { return travelMinutes; }
    public void setTravelMinutes(int travelMinutes) { this.travelMinutes = travelMinutes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
