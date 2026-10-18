package co.sena.scheduling.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ejercita el SecurityFilterChain REAL (con filtros) para los casos de
 * autenticación: 401 con envelope _shared#Error y trace_id.
 * Complementa TimeSlotControllerIT (addFilters=false), que prueba el wiring
 * de @PreAuthorize vía SecurityContextHolder.
 */
@SpringBootTest(
    classes = SchedulesApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.default_schema=scheduling",
    "spring.task.scheduling.enabled=false",
    "outbox.enabled=false"
})
class SecurityEnvelopeIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataSource dataSource;

    @org.junit.jupiter.api.BeforeEach
    void setUpDatabaseReadiness() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(1);
        when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
        when(statement.execute("SELECT 1")).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.isValid(0)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);
    }

    @Test
    void anonymousRequestIs401WithContractEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/time-slots"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.error.trace_id").exists());
    }

    @Test
    void garbageTokenIs401WithContractEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/time-slots")
                .header("Authorization", "Bearer no-es-un-jwt-valido"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.error.trace_id").exists());
    }

    @Test
    void healthAndReadinessArePublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/v1/ready"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.database").value("UP"));

        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
