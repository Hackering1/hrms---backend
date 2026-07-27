package com.technnext.hrms.ticket.dto;

import java.util.UUID;

public record TicketCreate(
        String subject,
        String description,
        String priority,
        UUID raisedById,
        String raisedByEmail
) {}