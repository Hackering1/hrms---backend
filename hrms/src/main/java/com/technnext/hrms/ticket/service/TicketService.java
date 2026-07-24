package com.technnext.hrms.ticket.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.ticket.dto.TicketCreate;
import com.technnext.hrms.ticket.dto.TicketStatusUpdate;
import com.technnext.hrms.ticket.entity.Ticket;
import com.technnext.hrms.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository repository;

    public List<Ticket> getAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public List<Ticket> getByStatus(String status) {
        return repository.findByStatusOrderByCreatedAtDesc(status);
    }

    public Ticket getById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
    }

    // Counts per status for the stat cards.
    public Map<String, Long> counts() {
        return Map.of(
                "OPEN", repository.countByStatus("OPEN"),
                "IN_PROGRESS", repository.countByStatus("IN_PROGRESS"),
                "ON_HOLD", repository.countByStatus("ON_HOLD"),
                "CLOSED", repository.countByStatus("CLOSED")
        );
    }

    // Anyone signed in can raise a ticket.
    public Ticket create(TicketCreate req) {
        Ticket t = Ticket.builder()
                .subject(req.subject())
                .description(req.description())
                .priority(req.priority() != null ? req.priority() : "MEDIUM")
                .status("OPEN")
                .raisedById(req.raisedById())
                .raisedByEmail(req.raisedByEmail())
                .build();
        return repository.save(t);
    }

    // Super admin only — change status / resolve.
    public Ticket updateStatus(Integer id, TicketStatusUpdate req) {
        Ticket t = getById(id);
        t.setStatus(req.status());
        t.setResolvedById(req.resolvedById());
        return repository.save(t);
    }
}