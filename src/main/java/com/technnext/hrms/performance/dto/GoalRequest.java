package com.technnext.hrms.performance.dto;

import java.time.LocalDate;
import java.util.UUID;

/** employeeId and cycleId are only used on create; progress defaults to 0. */
public record GoalRequest(UUID employeeId, Integer cycleId, String title, String description,
                          LocalDate targetDate, Integer progress) {}