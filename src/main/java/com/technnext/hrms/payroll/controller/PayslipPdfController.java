package com.technnext.hrms.payroll.controller;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.payroll.entity.Payslip;
import com.technnext.hrms.payroll.repository.PayslipRepository;
import com.technnext.hrms.payroll.service.PayslipPdfService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Downloads a single payslip as a formatted PDF (same letterhead/branding as
 * the Letters module). An employee can download their own payslip; HR/Admin
 * can download anyone's — same access rule as PayslipController.
 */
@RestController
@RequestMapping("/api/payroll/payslips")
@RequiredArgsConstructor
public class PayslipPdfController {

    private final PayslipPdfService pdfService;
    private final PayslipRepository payslipRepository;
    private final CurrentUserService currentUser;

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> download(@PathVariable Integer id, @AuthenticationPrincipal CustomUserDetails principal) {
        Payslip payslip = payslipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found: " + id));
        if (!currentUser.canAccessEmployee(principal, payslip.getEmployeeId())) {
            throw new BadRequestException("You do not have access to this payslip.");
        }

        byte[] pdf = pdfService.generate(payslip);
        String filename = "Payslip_" + payslip.getMonth() + "_" + payslip.getYear() + "_" + payslip.getEmployeeId() + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(pdf.length);
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}