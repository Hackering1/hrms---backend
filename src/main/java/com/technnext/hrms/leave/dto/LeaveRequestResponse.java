package com.technnext.hrms.leave.dto;

import com.technnext.hrms.leave.entity.LeaveRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record LeaveRequestResponse(
        Integer id,
        UUID employeeId,
        Integer leaveTypeId,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal numberOfDays,
        String dayType,
        String reason,
        String status,
        UUID approvedBy,
        LocalDateTime approvedAt,
        String approverRemarks,
        String documentUrl,
        LocalDateTime createdAt
) {
    public static LeaveRequestResponse from(LeaveRequest r) {
        return new LeaveRequestResponse(
                r.getId(), r.getEmployeeId(), r.getLeaveTypeId(), r.getFromDate(), r.getToDate(),
                r.getNumberOfDays(), r.getDayType(), r.getReason(), r.getStatus(),
                r.getApprovedBy(), r.getApprovedAt(), r.getApproverRemarks(), r.getDocumentUrl(),
                r.getCreatedAt()
        );
    }
}