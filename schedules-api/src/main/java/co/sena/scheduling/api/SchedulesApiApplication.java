package co.sena.scheduling.api;

import co.sena.scheduling.application.port.out.HolidayCalendarPort;
import co.sena.scheduling.domain.service.ScheduleDomainService;
import co.sena.scheduling.domain.service.SessionDomainService;
import co.sena.scheduling.domain.service.TimeSlotDomainService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "co.sena.scheduling")
@EnableScheduling
// HU-003 E5 mínimo local: OutboxMessage entra al PU (tabla creada por changeset
// 0005); el relay/broker sigue pendiente de HU-SCH-007.
@EntityScan({"co.sena.scheduling.adapter.out.persistence.entity",
    "co.sena.scheduling.adapter.out.messaging"})
@EnableJpaRepositories("co.sena.scheduling.adapter.out.persistence")
public class SchedulesApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SchedulesApiApplication.class, args);
    }

    @Bean
    public TimeSlotDomainService timeSlotDomainService() {
        return new TimeSlotDomainService();
    }

    @Bean
    public ScheduleDomainService scheduleDomainService() {
        return new ScheduleDomainService();
    }

    @Bean
    public SessionDomainService sessionDomainService(HolidayCalendarPort holidays) {
        return new SessionDomainService(holidays);
    }
}
