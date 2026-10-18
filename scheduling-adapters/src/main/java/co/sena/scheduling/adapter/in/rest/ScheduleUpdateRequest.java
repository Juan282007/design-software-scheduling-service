package co.sena.scheduling.adapter.in.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ScheduleUpdateRequest {
    private String name;

    @JsonProperty("row_version")
    private Integer rowVersion;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }
}
