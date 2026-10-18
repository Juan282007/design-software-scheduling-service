package co.sena.scheduling.adapter.config;

import co.sena.scheduling.application.port.out.EnvironmentAvailabilityProjectionPort;
import co.sena.scheduling.application.port.out.InstructorAvailabilityProjectionPort;
import co.sena.scheduling.application.port.out.SiteTravelTimePort;
import co.sena.scheduling.domain.service.ConflictDetectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConflictDetectionConfig {

    @Bean
    public ConflictDetectionService conflictDetectionService(
            EnvironmentAvailabilityProjectionPort envAvailabilityPort,
            InstructorAvailabilityProjectionPort instructorAvailabilityPort,
            SiteTravelTimePort siteTravelTimePort) {
        return new ConflictDetectionService(envAvailabilityPort, instructorAvailabilityPort, siteTravelTimePort);
    }
}
