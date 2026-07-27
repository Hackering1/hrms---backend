package com.technnext.hrms.organization.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "shifts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Builder.Default
    @Column(name = "grace_minutes")
    private Integer graceMinutes = 15;

    @Builder.Default
    @Column(name = "is_night_shift")
    private Boolean isNightShift = false;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;
}