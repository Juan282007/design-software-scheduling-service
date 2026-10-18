package co.sena.scheduling.application.port.in;

import java.util.UUID;

/**
 * Comando para crear un borrador de horario.
 */
public class CreateScheduleCommand {

    private UUID fichaId;
    private String period;
    private String name;
    private UUID createdBy;

    public CreateScheduleCommand() {}

    public CreateScheduleCommand(UUID fichaId, String period, String name, UUID createdBy) {
        this.fichaId = fichaId;
        this.period = period;
        this.name = name;
        this.createdBy = createdBy;
    }

    public UUID getFichaId() { return fichaId; }
    public void setFichaId(UUID fichaId) { this.fichaId = fichaId; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
