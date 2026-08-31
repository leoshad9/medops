package com.medops.files.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.medops.shared.exception.InvalidRequestException;

class PdfUploadPolicyTest {

    @Test
    void acceptsPdfMagicAndContentType() {
        byte[] pdf = "%PDF-1.4 mock".getBytes();
        assertDoesNotThrow(() -> PdfUploadPolicy.validate("application/pdf", pdf));
    }

    @Test
    void rejectsEmpty() {
        assertThrows(InvalidRequestException.class, () -> PdfUploadPolicy.validate("application/pdf", new byte[0]));
    }

    @Test
    void rejectsWrongType() {
        byte[] pdf = "%PDF-1.4 mock".getBytes();
        assertThrows(InvalidRequestException.class, () -> PdfUploadPolicy.validate("image/png", pdf));
    }

    @Test
    void rejectsNonPdfBytes() {
        assertThrows(InvalidRequestException.class,
                () -> PdfUploadPolicy.validate("application/pdf", "not-a-pdf".getBytes()));
    }
}
