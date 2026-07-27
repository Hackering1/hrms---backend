package com.technnext.hrms.ticket.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.security.CustomUserDetails;
import com.technnext.hrms.ticket.dto.TicketCreate;
import com.technnext.hrms.ticket.dto.TicketStatusUpdate;
import com.technnext.hrms.ticket.entity.Ticket;
import com.technnext.hrms.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SECURITY FIX: previously getAll/getByStatus/counts/getById had no scoping at
 * all, so any authenticated user (including a plain EMPLOYEE) could read every
 * other employee's tickets by browsing /api/tickets or guessing /api/tickets/{id}.
 * Every read is now scoped: privileged roles (Super Admin / HR / Manager) see
 * everything, everyone else sees only tickets they raised themselves.
 *
 * create() now derives raisedById from the JWT instead of trusting the client
 * body, closing a separate hole where a caller could raise a ticket that
 * appeared to come from someone else.
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService service;

    private static final List<String> PRIVILEGED_ROLES =
            List.of("ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN", "ROLE_HR_EXECUTIVE", "ROLE_MANAGER");

    @GetMapping
    public ApiResponse<List<Ticket>> getAll(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(service.getAll(scopeFor(principal)));
    }

    @GetMapping("/status/{status}")
    public ApiResponse<List<Ticket>> getByStatus(
            @PathVariable String status,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(service.getByStatus(status, scopeFor(principal)));
    }

    @GetMapping("/counts")
    public ApiResponse<Map<String, Long>> counts(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(service.counts(scopeFor(principal)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Ticket> getById(
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        Ticket t = service.getById(id);
        if (!isPrivileged(principal) && !t.getRaisedById().equals(principal.getUser().getId())) {
            throw new AccessDeniedException("You do not have access to this ticket.");
        }
        return ApiResponse.ok(t);
    }

    // Raise a ticket — any signed-in user (employee / manager / super admin).
    // Always raised as the caller; raisedById/raisedByEmail from the body are ignored.
    @PostMapping
    public ApiResponse<Ticket> create(
            @RequestBody TicketCreate body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UUID raisedById = principal.getUser().getId();
        String raisedByEmail = principal.getUser().getEmail();
        return ApiResponse.ok("Ticket raised", service.create(body, raisedById, raisedByEmail));
    }

    // Resolve / change status — SUPER ADMIN ONLY.
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Ticket> updateStatus(@PathVariable Integer id, @RequestBody TicketStatusUpdate body) {
        return ApiResponse.ok("Ticket updated", service.updateStatus(id, body));
    }

    /** null = unscoped (privileged, sees everything); non-null = restrict to this raisedById. */
    private UUID scopeFor(CustomUserDetails principal) {
        return isPrivileged(principal) ? null : principal.getUser().getId();
    }

    private boolean isPrivileged(CustomUserDetails principal) {
        return principal.getAuthorities().stream()
                .anyMatch(a -> PRIVILEGED_ROLES.contains(a.getAuthority()));
    }
}