package com.technnext.hrms.letter.dto;

// The filled-in letter text (placeholders replaced with employee data)
public record LetterPreview(
        String letterType,
        String content
) {}