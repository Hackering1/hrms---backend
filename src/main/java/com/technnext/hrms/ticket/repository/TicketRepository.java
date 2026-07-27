package com.technnext.hrms.ticket.repository;

import com.technnext.hrms.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, Integer> {
    List<Ticket> findAllByOrderByCreatedAtDesc();
    List<Ticket> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);

    // SECURITY: scoped variants — a non-privileged caller may only see their own tickets.
    List<Ticket> findByRaisedByIdOrderByCreatedAtDesc(UUID raisedById);
    List<Ticket> findByRaisedByIdAndStatusOrderByCreatedAtDesc(UUID raisedById, String status);
    long countByRaisedByIdAndStatus(UUID raisedById, String status);
}