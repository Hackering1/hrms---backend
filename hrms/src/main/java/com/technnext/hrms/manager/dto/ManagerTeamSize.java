package com.technnext.hrms.manager.dto;

import java.util.UUID;

public record ManagerTeamSize(
        UUID managerId,
        String managerName,
        String employeeCode,
        long teamSize
) {}