package com.technnext.hrms.employee.dto;

public record ExperienceDto(
        Integer id,
        String company,
        String designation,
        Integer fromMonth,
        Integer fromYear,
        Integer toMonth,
        Integer toYear
) {}