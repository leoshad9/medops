package com.medops.files.domain;

import java.util.Arrays;

public record UploadedPdf(String originalFilename, String contentType, byte[] content) {

    public UploadedPdf {
        content = content == null ? null : content.clone();
    }

    @Override
    public byte[] content() {
        return content == null ? null : content.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UploadedPdf other)) {
            return false;
        }
        return java.util.Objects.equals(originalFilename, other.originalFilename)
                && java.util.Objects.equals(contentType, other.contentType)
                && Arrays.equals(content, other.content);
    }

    @Override
    public int hashCode() {
        int result = java.util.Objects.hash(originalFilename, contentType);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }

    @Override
    public String toString() {
        return "UploadedPdf[originalFilename=" + originalFilename
                + ", contentType=" + contentType
                + ", content=" + Arrays.toString(content) + "]";
    }
}
