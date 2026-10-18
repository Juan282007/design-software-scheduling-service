package co.sena.scheduling.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TraceIdsTest {

    @Test
    void doesNotInventTraceIdWhenRequestHasNoCurrentTrace() {
        String traceId = TraceIds.currentOrUnavailable();

        assertThat(traceId).isEqualTo("unavailable");
        assertThatThrownBy(() -> UUID.fromString(traceId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
