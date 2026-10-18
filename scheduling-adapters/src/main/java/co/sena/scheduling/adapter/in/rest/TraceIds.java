package co.sena.scheduling.adapter.in.rest;

import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

final class TraceIds {
    static final String REQUEST_ATTRIBUTE = "scheduling.trace_id";
    private static final String UNAVAILABLE_TRACE_ID = "unavailable";

    private TraceIds() {
    }

    static String currentOrUnavailable() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object traceId = attributes.getRequest().getAttribute(REQUEST_ATTRIBUTE);
            if (traceId instanceof String value && !value.isBlank()) {
                return value;
            }
        }

        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return UNAVAILABLE_TRACE_ID;
    }
}
