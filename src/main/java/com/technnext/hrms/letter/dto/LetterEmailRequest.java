package com.technnext.hrms.letter.dto;

/**
 * Request to email a generated letter PDF to a recipient. Deliberately wraps
 * the existing {@link LetterPdfRequest} unchanged (same fields the "Generate
 * & Download PDF" flow already sends) rather than adding fields to it, so
 * nothing about that record/endpoint's contract changes.
 */
public record LetterEmailRequest(
        LetterPdfRequest letter,
        String recipientEmail
) {}