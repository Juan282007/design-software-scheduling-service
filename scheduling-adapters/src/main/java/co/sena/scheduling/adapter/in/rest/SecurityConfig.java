package co.sena.scheduling.adapter.in.rest;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // El filtro de resource-server tiene SU propio entryPoint para fallos de Bearer
            // (token inválido/expirado/firma); exceptionHandling solo cubre peticiones anónimas.
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint((request, response, authException) ->
                    writeEnvelope(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "Token ausente, inválido o expirado."))
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/health", "/api/v1/ready",
                    "/actuator/health", "/actuator/health/**").permitAll()
                .anyRequest().authenticated()
            )
            // _shared.yaml: 401/403 también viajan con el envelope Error (code,message,details,trace_id)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    writeEnvelope(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "Token ausente, inválido o expirado."))
                .accessDeniedHandler((request, response, deniedException) ->
                    writeEnvelope(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "Feature o scope insuficiente."))
            );
        return http.build();
    }

    private static void writeEnvelope(HttpServletResponse response, HttpStatus status,
                                      String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        if (status == HttpStatus.UNAUTHORIZED) {
            response.addHeader("WWW-Authenticate", "Bearer");
        }
        MAPPER.writeValue(response.getWriter(),
            TimeSlotExceptionHandler.ErrorResponse.of(code, message, List.of()));
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // La validación de `iss` (== sena-iam-service) y firma/fechas la hace el JwtDecoder
        // que configura spring.security.oauth2.resourceserver.jwt (issuer-uri + jwk-set-uri):
        // cualquier violación llega aquí ya como 401. Este converter SOLO mapea claims → autoridades.
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> features = jwt.getClaimAsStringList("features");
            return features != null
                ? features.stream()
                    .map(f -> f.contains(":") ? f.substring(0, f.indexOf(":")) : f)
                    .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                    .collect(Collectors.toList())
                : List.of();
        });
        return converter;
    }
}
