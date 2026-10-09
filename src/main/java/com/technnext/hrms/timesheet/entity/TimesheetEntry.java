package com.technnext.hrms.timesheet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "timesheet_entries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TimesheetEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "timesheet_id", nullable = false)
    private Integer timesheetId;

    @Column(name = "project_id", nullable = false)
    private Integer projectId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal hours;

    @Column(length = 500)
    private String description;
}