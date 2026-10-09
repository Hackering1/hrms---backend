package com.technnext.hrms.performance.dto;

import java.time.LocalDate;

public record CycleRequest(String name, LocalDate startDate, LocalDate endDate) {}