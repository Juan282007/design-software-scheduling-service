package co.sena.scheduling.application.port.in;

import java.time.LocalTime;

/** Datos de entrada para crear una franja horaria. */
public class CreateTimeSlotCommand {

    private String name;
    private int dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private String shift;

    public CreateTimeSlotCommand() {
    }

    public CreateTimeSlotCommand(String name, int dayOfWeek, LocalTime startTime,
                                 LocalTime endTime, String shift) {
        this.name = name;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.shift = shift;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getShift() { return shift; }
    public void setShift(String shift) { this.shift = shift; }
}
