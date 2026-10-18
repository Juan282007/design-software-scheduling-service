package co.sena.scheduling.adapter.in.rest;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TraceCorrelationFilter extends OncePerRequestFilter {

    private final ObjectProvider<Tracer> tracerProvider;

    public TraceCorrelationFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer != null) {
            Span span = tracer.currentSpan();
            if (span != null && span.context() != null) {
                request.setAttribute(TraceIds.REQUEST_ATTRIBUTE, span.context().traceId());
            }
        }
        filterChain.doFilter(request, response);
    }
}
