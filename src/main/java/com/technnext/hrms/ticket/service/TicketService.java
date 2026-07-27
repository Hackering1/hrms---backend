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
import java.util.UUID;

/**
 * SECURITY FIX: getAll/getByStatus/counts now take an explicit "scope" —
 * either null (privileged caller, sees everything) or the caller's own
 * raisedById (non-privileged caller, sees only their own tickets). Previously
 * these methods had no notion of a caller at all, so any authenticated user
 * could read every employee's tickets. The scoped repository methods below
 * already existed but were never wired up — they are now the default path.
 */
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository repository;

    public List<Ticket> getAll(UUID scopeToRaisedById) {
        return scopeToRaisedById == null
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByRaisedByIdOrderByCreatedAtDesc(scopeToRaisedById);
    }

    public List<Ticket> getByStatus(String status, UUID scopeToRaisedById) {
        return scopeToRaisedById == null
                ? repository.findByStatusOrderByCreatedAtDesc(status)
                : repository.findByRaisedByIdAndStatusOrderByCreatedAtDesc(scopeToRaisedById, status);
    }

    /**
     * Fetch a single ticket. Ownership (or privilege) must be checked by the
     * caller — see TicketController#getById — since this method has no
     * caller context to enforce it against.
     */
    public Ticket getById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
    }

    // Counts per status for the stat cards. Scoped the same way as getAll.
    public Map<String, Long> counts(UUID scopeToRaisedById) {
        if (scopeToRaisedById == null) {
            return Map.of(
                    "OPEN", repository.countByStatus("OPEN"),
                    "IN_PROGRESS", repository.countByStatus("IN_PROGRESS"),
                    "ON_HOLD", repository.countByStatus("ON_HOLD"),
                    "CLOSED", repository.countByStatus("CLOSED")
            );
        }
        return Map.of(
                "OPEN", repository.countByRaisedByIdAndStatus(scopeToRaisedById, "OPEN"),
                "IN_PROGRESS", repository.countByRaisedByIdAndStatus(scopeToRaisedById, "IN_PROGRESS"),
                "ON_HOLD", repository.countByRaisedByIdAndStatus(scopeToRaisedById, "ON_HOLD"),
                "CLOSED", repository.countByRaisedByIdAndStatus(scopeToRaisedById, "CLOSED")
        );
    }

    /**
     * Anyone signed in can raise a ticket, but it's always raised as the
     * caller. raisedById is passed in from the controller, already resolved
     * from the JWT — never trust a client-supplied value here.
     */
    public Ticket create(TicketCreate req, UUID raisedById, String raisedByEmail) {
        Ticket t = Ticket.builder()
                .subject(req.subject())
                .description(req.description())
                .priority(req.priority() != null ? req.priority() : "MEDIUM")
                .status("OPEN")
                .raisedById(raisedById)
                .raisedByEmail(raisedByEmail)
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