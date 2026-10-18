package co.sena.scheduling.adapter.in.rest;

import co.sena.scheduling.application.port.in.ScheduleAccess;
import co.sena.scheduling.domain.exception.ScheduleAccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

/**
 * Resuelve el scope de lectura (E2 HU-002, E-scope HU-008 #154) desde el claim
 * `features` (pares CODE:SCOPE). GLOBAL: sin filtro. TRAINING_CENTER: filtro por
 * `training_center_id` contra la proyeccion local ficha-centro. OWN_SCHEDULE:
 * filtro por actor_id. OWN_FICHA_AS_LEARNER: filtro por `ficha_id` emitido
 * contractualmente por IAM en el JWT. Otro scope: fail-closed 403. Sin JWT
 * (tests/scheduler): legacy.
 */
public final class ScheduleScope {

    private ScheduleScope() {}

    public static ScheduleAccess resolve(List<String> requiredFeatures) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            return ScheduleAccess.unrestricted();
        }
        List<String> pairs = jwtAuth.getToken().getClaimAsStringList("features");
        if (pairs == null || pairs.stream().noneMatch(p -> requiredFeatures.contains(featureOf(p)))) {
            return ScheduleAccess.unrestricted();
        }
        if (pairs.stream().anyMatch(p -> requiredFeatures.contains(featureOf(p)) && "GLOBAL".equals(scopeOf(p)))) {
            return ScheduleAccess.global();
        }
        boolean trainingCenterPair = pairs.stream()
            .anyMatch(p -> "SCH_VIEW_ALL".equals(featureOf(p)) && "TRAINING_CENTER".equals(scopeOf(p)));
        if (trainingCenterPair && requiredFeatures.contains("SCH_VIEW_ALL")) {
            String trainingCenterId = jwtAuth.getToken().getClaimAsString("training_center_id");
            if (trainingCenterId == null || trainingCenterId.isBlank()) {
                throw new ScheduleAccessDeniedException("TRAINING_CENTER sin training_center_id en el JWT");
            }
            return ScheduleAccess.trainingCenter(parseUuid(trainingCenterId, "training_center_id"));
        }
        boolean ownerPair = pairs.stream()
            .anyMatch(p -> "SCH_VIEW_OWN".equals(featureOf(p)) && "OWN_SCHEDULE".equals(scopeOf(p)));
        if (ownerPair && requiredFeatures.contains("SCH_VIEW_OWN")) {
            String actorId = jwtAuth.getToken().getClaimAsString("actor_id");
            if (actorId == null || actorId.isBlank()) {
                throw new ScheduleAccessDeniedException("OWN_SCHEDULE sin actor_id en el JWT");
            }
            return ScheduleAccess.ownerInstructor(parseUuid(actorId, "actor_id"));
        }
        boolean learnerPair = pairs.stream()
            .anyMatch(p -> "SCH_VIEW_OWN".equals(featureOf(p)) && "OWN_FICHA_AS_LEARNER".equals(scopeOf(p)));
        if (learnerPair && requiredFeatures.contains("SCH_VIEW_OWN")) {
            String fichaId = jwtAuth.getToken().getClaimAsString("ficha_id");
            if (fichaId == null || fichaId.isBlank()) {
                throw new ScheduleAccessDeniedException(
                    "OWN_FICHA_AS_LEARNER requiere ficha_id en el JWT emitido por IAM");
            }
            return ScheduleAccess.ownerFicha(parseUuid(fichaId, "ficha_id"));
        }
        throw new ScheduleAccessDeniedException(
            "Scope no resoluble para scheduling");
    }

    private static String featureOf(String pair) {
        int i = pair.indexOf(':');
        return i < 0 ? pair : pair.substring(0, i);
    }

    private static String scopeOf(String pair) {
        int i = pair.indexOf(':');
        return i < 0 ? "" : pair.substring(i + 1);
    }

    private static UUID parseUuid(String value, String claimName) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ScheduleAccessDeniedException("Claim " + claimName + " inválido en el JWT");
        }
    }
}
