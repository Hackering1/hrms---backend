package com.technnext.hrms.leave.service;

import com.technnext.hrms.organization.entity.Holiday;
import com.technnext.hrms.organization.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes the ACTUAL number of leave days between two dates — the way a real
 * company counts them — instead of trusting a number sent by the browser.
 *
 * Rules:
 *  - Saturdays and Sundays don't count.
 *  - PUBLIC holidays (from the Holidays page) don't count. RESTRICTED (optional)
 *    holidays DO count, since taking them is the employee's choice.
 *  - A HALF_DAY on a single working day counts as 0.5.
 *
 * NOTE ON WEEKENDS: this treats Sat + Sun as the weekend. If your company works
 * alternate Saturdays or a different week-off, that rule changes here (one place),
 * not scattered across the app.
 */
@Service
@RequiredArgsConstructor
public class LeaveDaysCalculator {

    private final HolidayRepository holidayRepository;

    public BigDecimal calculate(LocalDate from, LocalDate to, String dayType) {
        if (from == null || to == null || to.isBefore(from)) {
            return BigDecimal.ZERO;
        }

        Set<LocalDate> publicHolidays = holidayRepository
                .findByHolidayDateBetween(from, to).stream()
                .filter(h -> "PUBLIC".equalsIgnoreCase(h.getType()))
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        // Half-day only makes sense for a single working day.
        if ("HALF_DAY".equalsIgnoreCase(dayType) && from.equals(to)) {
            return isWorkingDay(from, publicHolidays)
                    ? BigDecimal.valueOf(0.5)
                    : BigDecimal.ZERO;
        }

        long count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (isWorkingDay(d, publicHolidays)) count++;
        }
        return BigDecimal.valueOf(count);
    }

    private boolean isWorkingDay(LocalDate d, Set<LocalDate> publicHolidays) {
        DayOfWeek dow = d.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false;
        return !publicHolidays.contains(d);
    }
}