package com.medops.files.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.medops.shared.exception.InvalidRequestException;

class PdfUploadPolicyTest {

    @Test
    void acceptsPdfMagicAndContentType() {
        byte[] pdf = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n".getBytes();
        assertDoesNotThrow(() -> PdfUploadPolicy.validate("application/pdf", pdf));
    }

    @Test
    void acceptsPdfWrittenWithCrossReferenceStream() {
        // PDF 1.5+ writers (Word, Chrome, LaTeX, ...) replace the classic trailer
        // keyword with an xref stream and close with "startxref" + "%%EOF".
        byte[] pdf = ("%PDF-1.5\n"
                + "1 0 obj\n<< /Type /Catalog /XRefStm 9 >>\nendobj\n"
                + "9 0 obj\n<< /Type /XRef /Length 4 >>\nstream\n\u0000\u0001\u0000EOF\nendstream\nendobj\n"
                + "startxref\n420\n%%EOF\n").getBytes();
        assertDoesNotThrow(() -> PdfUploadPolicy.validate("application/pdf", pdf));
    }

    @Test
    void acceptsContentTypeParameters() {
        byte[] pdf = "%PDF-1.7\n1 0 obj\nendobj\ntrailer\n<< /Root 1 0 R >>\nstartxref\n9\n%%EOF\n".getBytes();
        assertDoesNotThrow(() -> PdfUploadPolicy.validate("application/pdf; charset=UTF-8", pdf));
        assertDoesNotThrow(() -> PdfUploadPolicy.validate("  APPLICATION/PDF  ", pdf));
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

    @Test
    void rejectsPdfWithoutTrailerOrStartxref() {
        byte[] pdf = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n".getBytes();
        assertThrows(InvalidRequestException.class, () -> PdfUploadPolicy.validate("application/pdf", pdf));
    }

    @Test
    void rejectsPdfWithoutEof() {
        byte[] pdf = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n".getBytes();
        assertThrows(InvalidRequestException.class, () -> PdfUploadPolicy.validate("application/pdf", pdf));
    }
}
