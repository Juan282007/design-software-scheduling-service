package co.sena.scheduling.application.port.out;

import co.sena.scheduling.application.port.in.SessionFilter;
import co.sena.scheduling.application.port.in.SessionPage;
import co.sena.scheduling.domain.model.ClassSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de sesiones de clase (HU-003). */
public interface SessionPersistencePort {

    ClassSession save(ClassSession session);

    Optional<ClassSession> findActiveById(UUID id);

    List<ClassSession> findByScheduleId(UUID scheduleId);

    SessionPage search(SessionFilter filter);

    SessionPage search(SessionFilter filter, UUID ownerInstructorId);

    SessionPage search(SessionFilter filter, UUID ownerInstructorId, UUID ownerFichaId);

    SessionPage search(SessionFilter filter, UUID ownerInstructorId, UUID ownerFichaId, UUID trainingCenterId);
}
