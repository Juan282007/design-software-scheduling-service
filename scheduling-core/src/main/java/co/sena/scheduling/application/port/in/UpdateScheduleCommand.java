package co.sena.scheduling.application.port.in;

/** PUT /schedules/{id}: solo name + row_version vigente (contrato ScheduleUpdate). */
public class UpdateScheduleCommand {

    private final String name;
    private final int rowVersion;

    public UpdateScheduleCommand(String name, int rowVersion) {
        this.name = name;
        this.rowVersion = rowVersion;
    }

    public String getName() { return name; }
    public int getRowVersion() { return rowVersion; }
}
