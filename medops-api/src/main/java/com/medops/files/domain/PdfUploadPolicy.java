package com.medops.files.domain;

import com.medops.shared.exception.InvalidRequestException;

public final class PdfUploadPolicy {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    public static final String PDF_CONTENT_TYPE = "application/pdf";

    private static final String EOF_MARKER = "%EOF";
    /** Trailer keyword used by classic xref-table files. */
    private static final String TRAILER_KEYWORD = "trailer";
    /** End-of-file keyword written by every PDF, including xref-stream files. */
    private static final String START_XREF_KEYWORD = "startxref";

    private PdfUploadPolicy() {
    }

    public static void validate(String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidRequestException("A PDF file is required");
        }
        if (content.length > MAX_BYTES) {
            throw new InvalidRequestException("PDF files must be 10 MB or smaller");
        }
        if (!PDF_CONTENT_TYPE.equalsIgnoreCase(baseContentType(contentType))) {
            throw new InvalidRequestException("Only PDF files are accepted");
        }
        if (!hasPdfMagic(content)) {
            throw new InvalidRequestException("Only PDF files are accepted");
        }
        if (!hasPdfEndStructure(content)) {
            throw new InvalidRequestException("PDF is missing end-of-file marker");
        }
    }

    private static String baseContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int parameters = contentType.indexOf(';');
        return (parameters < 0 ? contentType : contentType.substring(0, parameters)).trim();
    }

    private static boolean hasPdfMagic(byte[] content) {
        return content.length >= 4
                && content[0] == '%'
                && content[1] == 'P'
                && content[2] == 'D'
                && content[3] == 'F';
    }

    /**
     * A readable PDF closes with the %%EOF marker preceded by a cross-reference
     * pointer. Classic xref-table files also carry a {@code trailer} keyword,
     * but PDF 1.5+ files that use cross-reference streams have no literal
     * trailer — requiring one rejected valid uploads. Either marker is accepted
     * as long as it appears before the final %%EOF.
     */
    private static boolean hasPdfEndStructure(byte[] content) {
        int eofMarker = lastIndexOf(content, EOF_MARKER);
        if (eofMarker < 0) {
            return false;
        }
        int structureMarker = Math.max(
                lastIndexOf(content, TRAILER_KEYWORD),
                lastIndexOf(content, START_XREF_KEYWORD));
        return structureMarker >= 0 && structureMarker < eofMarker;
    }

    private static int lastIndexOf(byte[] content, String marker) {
        byte[] markerBytes = marker.getBytes();
        for (int i = content.length - markerBytes.length; i >= 0; i--) {
            boolean found = true;
            for (int j = 0; j < markerBytes.length; j++) {
                if (content[i + j] != markerBytes[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }
}
