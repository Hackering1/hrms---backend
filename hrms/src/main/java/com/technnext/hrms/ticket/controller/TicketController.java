package com.technnext.hrms.ticket.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.ticket.dto.TicketCreate;
import com.technnext.hrms.ticket.dto.TicketStatusUpdate;
import com.technnext.hrms.ticket.entity.Ticket;
import com.technnext.hrms.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService service;

    @GetMapping
    public ApiResponse<List<Ticket>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/status/{status}")
    public ApiResponse<List<Ticket>> getByStatus(@PathVariable String status) {
        return ApiResponse.ok(service.getByStatus(status));
    }

    @GetMapping("/counts")
    public ApiResponse<Map<String, Long>> counts() {
        return ApiResponse.ok(service.counts());
    }

    @GetMapping("/{id}")
    public ApiResponse<Ticket> getById(@PathVariable Integer id) {
        return ApiResponse.ok(service.getById(id));
    }

    // Raise a ticket — any signed-in user (employee / manager / super admin).
    @PostMapping
    public ApiResponse<Ticket> create(@RequestBody TicketCreate body) {
        return ApiResponse.ok("Ticket raised", service.create(body));
    }

    // Resolve / change status — SUPER ADMIN ONLY.
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Ticket> updateStatus(@PathVariable Integer id, @RequestBody TicketStatusUpdate body) {
        return ApiResponse.ok("Ticket updated", service.updateStatus(id, body));
    }
}