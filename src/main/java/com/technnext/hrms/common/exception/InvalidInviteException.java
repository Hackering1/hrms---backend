package com.technnext.hrms.common.exception;

import lombok.Getter;

/**
 * Thrown when an onboarding token is not usable. Carries a machine-readable
 * `reason` (in addition to a human message) so the public onboarding page can
 * show the exact right state — "Invalid Invitation" / "Expired Invitation" /
 * "Invitation Already Used" — without having to pattern-match the message text.
 */
@Getter
public class InvalidInviteException extends RuntimeException {

    public enum Reason { NOT_FOUND, EXPIRED, ALREADY_USED, CANCELLED }

    private final Reason reason;

    public InvalidInviteException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }
}