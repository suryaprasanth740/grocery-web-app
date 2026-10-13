package com.suryaprasanth.grocery;

import com.suryaprasanth.grocery.service.SlotService;
import com.suryaprasanth.grocery.service.SlotService.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The time-slot rules on their own, with fixed clock times (no database, no Spring), so the
 * boundary cases are tested exactly and never depend on when the tests run.
 */
class SlotRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final Window SEVEN_TO_NINE = new Window(LocalTime.of(7, 0), LocalTime.of(9, 0));

    @Test
    @DisplayName("A 7:00-22:00 shop gets 2-hour slots, and a last 1-hour slot 21:00-22:00")
    void windowsOfADay() {
        List<Window> w = SlotService.windows(LocalTime.of(7, 0), LocalTime.of(22, 0));
        assertThat(w).hasSize(8);
        assertThat(w.get(0)).isEqualTo(new Window(LocalTime.of(7, 0), LocalTime.of(9, 0)));
        assertThat(w.get(7)).isEqualTo(new Window(LocalTime.of(21, 0), LocalTime.of(22, 0)));
    }

    @Test
    @DisplayName("A leftover shorter than 1 hour is not offered; a day ending at 23:59 never wraps past midnight")
    void shortAndLateWindows() {
        assertThat(SlotService.windows(LocalTime.of(8, 0), LocalTime.of(10, 30)))
                .containsExactly(new Window(LocalTime.of(8, 0), LocalTime.of(10, 0)));
        List<Window> allDay = SlotService.windows(LocalTime.of(0, 0), LocalTime.of(23, 59));
        assertThat(allDay).hasSize(12);
        assertThat(allDay.get(11)).isEqualTo(new Window(LocalTime.of(22, 0), LocalTime.of(23, 59)));
    }

    @Test
    @DisplayName("Cut-off boundary: 61 minutes before the start can book, 60 and 59 minutes before cannot")
    void cutOffBoundary() {
        assertThat(SlotService.openForBooking(TODAY, SEVEN_TO_NINE, TODAY.atTime(5, 59))).isTrue();
        assertThat(SlotService.openForBooking(TODAY, SEVEN_TO_NINE, TODAY.atTime(6, 0))).isFalse();
        assertThat(SlotService.openForBooking(TODAY, SEVEN_TO_NINE, TODAY.atTime(6, 1))).isFalse();
        assertThat(SlotService.openForBooking(TODAY, SEVEN_TO_NINE, TODAY.atTime(8, 0))).isFalse(); // already running
    }

    @Test
    @DisplayName("Only today and tomorrow: yesterday and the day after tomorrow are refused")
    void bookingWindowOfDays() {
        LocalDateTime now = TODAY.atTime(10, 0);
        assertThat(SlotService.openForBooking(TODAY.plusDays(1), SEVEN_TO_NINE, now)).isTrue();
        assertThat(SlotService.openForBooking(TODAY.minusDays(1), SEVEN_TO_NINE, now)).isFalse();
        assertThat(SlotService.openForBooking(TODAY.plusDays(2), SEVEN_TO_NINE, now)).isFalse();
        // Late at night, tomorrow's first slot is still bookable
        assertThat(SlotService.openForBooking(TODAY.plusDays(1), SEVEN_TO_NINE, TODAY.atTime(23, 50))).isTrue();
    }
}
