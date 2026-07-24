package com.technnext.hrms.ticket.dto;

import java.util.UUID;

public record TicketStatusUpdate(
        String status,
        UUID resolvedById
) {}