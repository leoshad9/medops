package com.medops.files.domain;

import com.medops.shared.exception.InvalidRequestException;

public final class PdfUploadPolicy {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    public static final String PDF_CONTENT_TYPE = "application/pdf";

    private PdfUploadPolicy() {
    }

    public static void validate(String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidRequestException("A PDF file is required");
        }
        if (content.length > MAX_BYTES) {
            throw new InvalidRequestException("PDF files must be 10 MB or smaller");
        }
        if (contentType == null || !PDF_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            throw new InvalidRequestException("Only PDF files are accepted");
        }
        if (content.length < 4
                || content[0] != '%'
                || content[1] != 'P'
                || content[2] != 'D'
                || content[3] != 'F') {
            throw new InvalidRequestException("Only PDF files are accepted");
        }

        int eofMarker = lastIndexOf(content, "%EOF");
        if (eofMarker < 0) {
            throw new InvalidRequestException("Only valid PDF files are accepted");
        }

        int trailerMarker = lastIndexOf(content, "trailer");
        if (trailerMarker < 0 || trailerMarker > eofMarker) {
            throw new InvalidRequestException("Only valid PDF files are accepted");
        }
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
