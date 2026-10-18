package co.sena.scheduling.domain.service;

import co.sena.scheduling.domain.exception.TimeSlotValidationException;
import co.sena.scheduling.domain.model.ShiftType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotDomainServiceTest {

    private final TimeSlotDomainService service = new TimeSlotDomainService();

    @Test
    void acceptsAValidTimeSlot() {
        service.validate("Morning", 1, LocalTime.of(7, 0), LocalTime.of(10, 0), ShiftType.DAY);
    }

    @Test
    void rejectsAStartTimeAfterOrEqualToEndTime() {
        assertThatThrownBy(() -> service.validate(
                "Invalid", 1, LocalTime.of(10, 0), LocalTime.of(10, 0), ShiftType.DAY))
            .isInstanceOf(TimeSlotValidationException.class)
            .hasMessageContaining("start_time");
    }

    @Test
    void rejectsDayOfWeekOutsideTheContractRange() {
        assertThatThrownBy(() -> service.validate(
                "Invalid", 8, LocalTime.of(7, 0), LocalTime.of(10, 0), ShiftType.DAY))
            .isInstanceOf(TimeSlotValidationException.class)
            .hasMessageContaining("day_of_week");
    }

    @Test
    void rejectsBlankOrTooLongNames() {
        assertThatThrownBy(() -> service.validate(
                " ", 1, LocalTime.of(7, 0), LocalTime.of(10, 0), ShiftType.DAY))
            .isInstanceOf(TimeSlotValidationException.class)
            .hasMessageContaining("name");

        assertThatThrownBy(() -> service.validate(
                "x".repeat(51), 1, LocalTime.of(7, 0), LocalTime.of(10, 0), ShiftType.DAY))
            .isInstanceOf(TimeSlotValidationException.class)
            .hasMessageContaining("name");
    }

    @ParameterizedTest
    @CsvSource({"day,DAY", " NIGHT ,NIGHT", "mixed,MIXED"})
    void normalizesShiftToTheCanonicalValues(String raw, ShiftType expected) {
        assertThat(service.normalizeShift(raw)).isEqualTo(expected);
    }

    @Test
    void rejectsTheHistoricalMisspellingOfNight() {
        assertThatThrownBy(() -> service.normalizeShift("NIGTH"))
            .isInstanceOf(TimeSlotValidationException.class)
            .hasMessageContaining("shift");
    }
}
