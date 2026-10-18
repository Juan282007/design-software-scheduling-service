package co.sena.scheduling.adapter.in.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** POST /schedules body (contrato ScheduleCreate: ficha_id, period, name opcional). */
public class ScheduleCreateRequest {

    @JsonProperty("ficha_id")
    private UUID fichaId;
    private String period;
    private String name;

    public UUID getFichaId() { return fichaId; }
    public void setFichaId(UUID fichaId) { this.fichaId = fichaId; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
