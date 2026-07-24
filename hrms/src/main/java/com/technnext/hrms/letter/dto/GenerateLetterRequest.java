package com.technnext.hrms.letter.dto;

import java.time.LocalDate;
import java.util.UUID;

public record GenerateLetterRequest(
        UUID employeeId,
        Integer templateId,
        LocalDate letterDate,
        UUID generatedBy
) {}