package com.technnext.hrms.attendance.dto;

import java.util.UUID;

/**
 * Used for both check-in and check-out.
 *
 * latitude / longitude / checkInPhotoId are populated (and REQUIRED) on CHECK-IN.
 * checkOutLatitude / checkOutLongitude / checkOutPhotoId are populated (and
 * REQUIRED) on CHECK-OUT. Each set is ignored by the other action. The browser
 * must grant location access and capture a selfie for both actions; the photo
 * is uploaded to /api/files first, then its id passed here. See
 * AttendanceService.checkIn / checkOut for enforcement.
 */
public record CheckRequest(
        UUID employeeId,
        String ipAddress,
        String deviceInfo,
        // check-in geo + selfie
        Double latitude,
        Double longitude,
        UUID checkInPhotoId,
        // check-out geo + selfie
        Double checkOutLatitude,
        Double checkOutLongitude,
        UUID checkOutPhotoId
) {}