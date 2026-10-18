package co.sena.scheduling.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Eventos de dominio publicados por scheduling-service.
 */
public class ScheduleEvents {

    public static class ScheduleCreated {
        private final UUID scheduleId;
        private final UUID fichaId;
        private final String period;
        private final String name;
        private final UUID createdBy;
        private final Instant createdAt;

        public ScheduleCreated(UUID scheduleId, UUID fichaId, String period, String name,
                               UUID createdBy, Instant createdAt) {
            this.scheduleId = scheduleId;
            this.fichaId = fichaId;
            this.period = period;
            this.name = name;
            this.createdBy = createdBy;
            this.createdAt = createdAt;
        }

        public UUID getScheduleId() { return scheduleId; }
        public UUID getFichaId() { return fichaId; }
        public String getPeriod() { return period; }
        public String getName() { return name; }
        public UUID getCreatedBy() { return createdBy; }
        public Instant getCreatedAt() { return createdAt; }
    }

    public static class ScheduleValidated {
        private final UUID scheduleId;
        private final UUID fichaId;
        private final String period;
        private final Instant validatedAt;

        public ScheduleValidated(UUID scheduleId, UUID fichaId, String period, Instant validatedAt) {
            this.scheduleId = scheduleId;
            this.fichaId = fichaId;
            this.period = period;
            this.validatedAt = validatedAt;
        }

        public UUID getScheduleId() { return scheduleId; }
        public UUID getFichaId() { return fichaId; }
        public String getPeriod() { return period; }
        public Instant getValidatedAt() { return validatedAt; }
    }

    public static class SchedulePublished {
        private final UUID scheduleId;
        private final UUID fichaId;
        private final String period;
        private final int sessionCount;
        private final UUID publishedBy;
        private final Instant publishedAt;
        private final List<UUID> instructorIds;
        private final List<UUID> environmentIds;

        public SchedulePublished(UUID scheduleId, UUID fichaId, String period,
                                 int sessionCount, UUID publishedBy, Instant publishedAt) {
            this(scheduleId, fichaId, period, sessionCount, publishedBy, publishedAt,
                List.of(), List.of());
        }

        public SchedulePublished(UUID scheduleId, UUID fichaId, String period,
                                 int sessionCount, UUID publishedBy, Instant publishedAt,
                                 List<UUID> instructorIds, List<UUID> environmentIds) {
            this.scheduleId = scheduleId;
            this.fichaId = fichaId;
            this.period = period;
            this.sessionCount = sessionCount;
            this.publishedBy = publishedBy;
            this.publishedAt = publishedAt;
            this.instructorIds = instructorIds == null ? List.of() : List.copyOf(instructorIds);
            this.environmentIds = environmentIds == null ? List.of() : List.copyOf(environmentIds);
        }

        public UUID getScheduleId() { return scheduleId; }
        public UUID getFichaId() { return fichaId; }
        public String getPeriod() { return period; }
        public int getSessionCount() { return sessionCount; }
        public UUID getPublishedBy() { return publishedBy; }
        public Instant getPublishedAt() { return publishedAt; }
        public List<UUID> getInstructorIds() { return instructorIds; }
        public List<UUID> getEnvironmentIds() { return environmentIds; }
    }

    public static class ClassSessionCreated {
        private final UUID sessionId;
        private final UUID scheduleId;
        private final UUID competencyId;
        private final UUID instructorId;
        private final UUID environmentId;
        private final UUID timeSlotId;
        private final String sessionDate;

        public ClassSessionCreated(UUID sessionId, UUID scheduleId, UUID competencyId,
                                   UUID instructorId, UUID environmentId, UUID timeSlotId,
                                   String sessionDate) {
            this.sessionId = sessionId;
            this.scheduleId = scheduleId;
            this.competencyId = competencyId;
            this.instructorId = instructorId;
            this.environmentId = environmentId;
            this.timeSlotId = timeSlotId;
            this.sessionDate = sessionDate;
        }

        public UUID getSessionId() { return sessionId; }
        public UUID getScheduleId() { return scheduleId; }
        public UUID getCompetencyId() { return competencyId; }
        public UUID getInstructorId() { return instructorId; }
        public UUID getEnvironmentId() { return environmentId; }
        public UUID getTimeSlotId() { return timeSlotId; }
        public String getSessionDate() { return sessionDate; }
    }

    public static class ClassSessionCancelled {
        private final UUID sessionId;
        private final UUID scheduleId;
        private final Instant cancelledAt;

        public ClassSessionCancelled(UUID sessionId, UUID scheduleId, Instant cancelledAt) {
            this.sessionId = sessionId;
            this.scheduleId = scheduleId;
            this.cancelledAt = cancelledAt;
        }

        public UUID getSessionId() { return sessionId; }
        public UUID getScheduleId() { return scheduleId; }
        public Instant getCancelledAt() { return cancelledAt; }
    }
}
