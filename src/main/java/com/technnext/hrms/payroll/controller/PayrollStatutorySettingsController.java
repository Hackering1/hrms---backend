package com.technnext.hrms.payroll.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.payroll.entity.PfSettings;
import com.technnext.hrms.payroll.entity.PtSlab;
import com.technnext.hrms.payroll.entity.TaxSettings;
import com.technnext.hrms.payroll.entity.TaxSlab;
import com.technnext.hrms.payroll.service.PfSettingsService;
import com.technnext.hrms.payroll.service.PtSlabService;
import com.technnext.hrms.payroll.service.TaxSlabService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Statutory config (PF wage ceiling, state PT slabs, income-tax slabs) —
 * these change by government notification, so HR/Finance can update them
 * here without needing a code deploy. Kept in one controller since they're
 * small, closely related admin screens (mirrors "Payroll > Statutory Settings"
 * in the frontend).
 */
@RestController
@RequestMapping("/api/payroll/statutory")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
public class PayrollStatutorySettingsController {

    private final PfSettingsService pfSettingsService;
    private final PtSlabService ptSlabService;
    private final TaxSlabService taxSlabService;

    // ── Provident Fund ──────────────────────────────────────────────────
    @GetMapping("/pf")
    public ApiResponse<List<PfSettings>> pfHistory() {
        return ApiResponse.ok(pfSettingsService.getAll());
    }

    @GetMapping("/pf/current")
    public ApiResponse<PfSettings> pfCurrent() {
        return ApiResponse.ok(pfSettingsService.current());
    }

    @PostMapping("/pf")
    public ApiResponse<PfSettings> pfCreateRevision(@Valid @RequestBody PfSettings body) {
        return ApiResponse.ok("PF settings updated", pfSettingsService.create(body));
    }

    // ── Professional Tax ────────────────────────────────────────────────
    @GetMapping("/pt")
    public ApiResponse<List<PtSlab>> ptSlabs() {
        return ApiResponse.ok(ptSlabService.getAll());
    }

    @PostMapping("/pt")
    public ApiResponse<PtSlab> ptCreate(@Valid @RequestBody PtSlab body) {
        return ApiResponse.ok("PT slab added", ptSlabService.create(body));
    }

    @PutMapping("/pt/{id}")
    public ApiResponse<PtSlab> ptUpdate(@PathVariable Integer id, @Valid @RequestBody PtSlab body) {
        return ApiResponse.ok("PT slab updated", ptSlabService.update(id, body));
    }

    @DeleteMapping("/pt/{id}")
    public ApiResponse<Void> ptDelete(@PathVariable Integer id) {
        ptSlabService.delete(id);
        return ApiResponse.ok("PT slab deactivated", null);
    }

    // ── Income Tax (TDS) ────────────────────────────────────────────────
    @GetMapping("/tax-slabs")
    public ApiResponse<List<TaxSlab>> taxSlabs(@RequestParam String financialYear, @RequestParam(required = false) String regime) {
        return ApiResponse.ok(taxSlabService.getSlabs(financialYear, regime));
    }

    @PutMapping("/tax-slabs")
    public ApiResponse<List<TaxSlab>> replaceTaxSlabs(
            @RequestParam String financialYear,
            @RequestParam(defaultValue = "NEW") String regime,
            @Valid @RequestBody List<TaxSlab> slabs) {
        return ApiResponse.ok("Tax slabs updated", taxSlabService.replaceSlabs(financialYear, regime, slabs));
    }

    @GetMapping("/tax-settings")
    public ApiResponse<TaxSettings> taxSettings(@RequestParam String financialYear) {
        return ApiResponse.ok(taxSlabService.getSettings(financialYear));
    }

    @PutMapping("/tax-settings")
    public ApiResponse<TaxSettings> upsertTaxSettings(@Valid @RequestBody TaxSettings body) {
        return ApiResponse.ok("Tax settings updated", taxSlabService.upsertSettings(body));
    }
}