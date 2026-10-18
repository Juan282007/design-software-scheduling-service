package co.sena.scheduling.adapter.config;

import co.sena.scheduling.adapter.out.persistence.AffectedSessionAdapter;
import co.sena.scheduling.adapter.out.persistence.AvailabilityProjectionAdapter;
import co.sena.scheduling.domain.service.AvailabilityProjectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AvailabilityProjectionConfig {

    @Bean
    public AvailabilityProjectionService availabilityProjectionService(
            AvailabilityProjectionAdapter availabilityProjectionAdapter,
            AffectedSessionAdapter affectedSessionAdapter) {
        return new AvailabilityProjectionService(availabilityProjectionAdapter, affectedSessionAdapter);
    }
}
