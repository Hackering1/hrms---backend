package com.technnext.hrms.attendance.repository;

import com.technnext.hrms.attendance.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, Integer> {
    Optional<Attendance> findByEmployeeIdAndAttendanceDate(UUID employeeId, LocalDate attendanceDate);
    List<Attendance> findByEmployeeIdOrderByAttendanceDateDesc(UUID employeeId);
    List<Attendance> findByAttendanceDate(LocalDate attendanceDate);
    // File-access checks: which attendance rows reference a given photo file id.
    List<Attendance> findByCheckInPhotoId(UUID checkInPhotoId);
    List<Attendance> findByCheckOutPhotoId(UUID checkOutPhotoId);
}