package com.technnext.hrms.file.service;

import com.technnext.hrms.attendance.repository.AttendanceRepository;
import com.technnext.hrms.document.repository.EmployeeDocumentRepository;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.file.entity.StoredFile;
import com.technnext.hrms.letter.repository.GeneratedLetterRepository;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Decides whether the logged-in user may download a given stored file.
 *
 * A stored file is not owned by an employee directly — it's referenced from
 * elsewhere (a profile photo, an employee document, a generated letter, or an
 * attendance selfie). So access is resolved by finding what references the file
 * and checking the caller's access to the employee it belongs to.
 *
 * Policy (proportionate to sensitivity):
 *   - SUPER_ADMIN: anything.
 *   - Profile photos: any signed-in user (avatars are shown across the app;
 *     they are org-internal, low-sensitivity, and blocking them breaks the UI).
 *   - The uploader: whatever they uploaded.
 *   - Documents / letters / attendance selfies: only if the caller can access the
 *     employee that file belongs to (self, a manager's team member, or admin).
 *   - Anything else (orphaned/unknown files): denied.
 */
@Service
@RequiredArgsConstructor
public class FileAccessService {

    private final CurrentUserService currentUser;
    private final EmployeeRepository employeeRepository;
    private final EmployeeDocumentRepository documentRepository;
    private final GeneratedLetterRepository letterRepository;
    private final AttendanceRepository attendanceRepository;

    public boolean canDownload(StoredFile file, CustomUserDetails principal) {
        if (principal == null) return false;
        if (currentUser.isSuperAdmin(principal)) return true;

        UUID fileId = file.getId();
        String idStr = fileId.toString();

        // Profile photos: viewable by any authenticated user.
        if (!employeeRepository.findByProfilePhotoUrlContaining(idStr).isEmpty()) {
            return true;
        }

        // The uploader can always fetch what they uploaded (own selfie, own upload, etc.).
        UUID myUserId = principal.getUser().getId();
        if (myUserId != null && myUserId.equals(file.getUploadedBy())) {
            return true;
        }

        // Otherwise: allow only if the file belongs to an employee the caller can access.
        Set<UUID> owningEmployeeIds = new HashSet<>();
        documentRepository.findByFileUrlContaining(idStr)
                .forEach(d -> owningEmployeeIds.add(d.getEmployeeId()));
        letterRepository.findByFileUrlContaining(idStr)
                .forEach(l -> owningEmployeeIds.add(l.getEmployeeId()));
        attendanceRepository.findByCheckInPhotoId(fileId)
                .forEach(a -> owningEmployeeIds.add(a.getEmployeeId()));
        attendanceRepository.findByCheckOutPhotoId(fileId)
                .forEach(a -> owningEmployeeIds.add(a.getEmployeeId()));

        for (UUID ownerId : owningEmployeeIds) {
            if (ownerId != null && currentUser.canAccessEmployee(principal, ownerId)) {
                return true;
            }
        }
        return false;
    }
}