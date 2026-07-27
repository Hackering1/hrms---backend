package com.technnext.hrms.attendance.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Attendance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Builder.Default
    private String status = "PRESENT";

    @Column(name = "working_hours")
    private BigDecimal workingHours;

    @Builder.Default
    @Column(name = "is_regularized")
    private Boolean isRegularized = false;

    private String remarks;

    // ── NEW: check-in geolocation + selfie ───────────────────────────────────────
    // Captured at check-in time. Nullable because bulk-marked / regularized rows and
    // all existing historical rows won't have them. New self-service check-ins require
    // them (enforced in AttendanceService.checkIn).

    @Column(name = "check_in_latitude")
    private Double checkInLatitude;

    @Column(name = "check_in_longitude")
    private Double checkInLongitude;

    /** References file_storage.id — the selfie captured at check-in. */
    @Column(name = "check_in_photo_id")
    private UUID checkInPhotoId;

    // ── NEW: check-out geolocation + selfie ──────────────────────────────────────
    // Same idea as the check-in trio above, captured at check-out time. Nullable
    // for the same reasons (bulk-marked rows, historical rows, regularizations).

    @Column(name = "check_out_latitude")
    private Double checkOutLatitude;

    @Column(name = "check_out_longitude")
    private Double checkOutLongitude;

    /** References file_storage.id — the selfie captured at check-out. */
    @Column(name = "check_out_photo_id")
    private UUID checkOutPhotoId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}