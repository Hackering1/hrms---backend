package com.technnext.hrms.email;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for EmployeeWelcomeEmailEvent and sends the email:
 *  - AFTER_COMMIT: only fires once the new employee (and their login, if any)
 *    has actually been saved to the database. If employee creation fails and
 *    the transaction rolls back, no email goes out.
 *  - @Async: runs on a separate thread so a slow/unreachable mail server never
 *    delays the HTTP response for the "Add Employee" request.
 */
@Component
@RequiredArgsConstructor
public class EmployeeWelcomeEmailListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmployeeWelcomeEmail(EmployeeWelcomeEmailEvent event) {
        emailService.sendEmployeeWelcomeEmail(event);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmployeeInviteEmail(EmployeeInviteEmailEvent event) {
        emailService.sendEmployeeInviteEmail(event);
    }
}