package com.technnext.hrms.employee.dto;

public record EducationDto(
        Integer id,
        String level,
        String institution,
        String specialization,
        String percentage,
        Integer fromMonth,
        Integer fromYear,
        Integer toMonth,
        Integer toYear,
        String documentUrl
) {}