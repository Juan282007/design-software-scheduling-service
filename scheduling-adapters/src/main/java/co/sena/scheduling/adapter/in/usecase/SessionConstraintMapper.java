package co.sena.scheduling.adapter.in.usecase;

import co.sena.scheduling.domain.exception.SessionConflictException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Mapea violaciones de integridad a 409 de HU-003 E2. La violación del EXCLUDE
 * puede surfear en el flush o en el commit: se usa desde el caso de uso (con
 * flush forzado) y desde el handler (red para el commit).
 */
public final class SessionConstraintMapper {

    private SessionConstraintMapper() {}

    public static SessionConflictException toConflict(DataIntegrityViolationException ex) {
        String haystack = chainToString(ex);
        if (haystack.contains("ex_session_instructor")) {
            return new SessionConflictException("INSTRUCTOR_DOUBLE_BOOKED",
                "El instructor ya tiene una sesión en ese rango horario");
        }
        if (haystack.contains("ex_session_environment")) {
            return new SessionConflictException("ENVIRONMENT_DOUBLE_BOOKED",
                "El ambiente ya está ocupado en ese rango horario");
        }
        return new SessionConflictException("SESSION_CONFLICT",
            "La sesión entra en conflicto con otra existente");
    }

    private static String chainToString(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            builder.append(current).append('\n');
            // Hibernate expone el constraint en getConstraintName() (no siempre en el mensaje).
            try {
                java.lang.reflect.Method getter = current.getClass().getMethod("getConstraintName");
                Object name = getter.invoke(current);
                if (name != null) {
                    builder.append(name).append('\n');
                }
            } catch (ReflectiveOperationException ignored) {
                // Sin accessor: se usa el mensaje ya agregado.
            }
        }
        return builder.toString();
    }
}
