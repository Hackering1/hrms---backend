package com.technnext.hrms.invite.dto;

/**
 * @param docType one of: PROFILE_PHOTO, RESUME, AADHAAR, PAN, DEGREE_CERTIFICATE,
 *                EXPERIENCE_CERTIFICATE, BANK_PASSBOOK, OTHER
 * @param fileUrl the URL returned by POST /api/public/onboarding/{token}/upload
 */
public record OnboardingDocumentDto(String docType, String fileUrl) {
}