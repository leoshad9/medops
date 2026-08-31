package com.medops.files.application;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

import com.medops.files.domain.UploadedPdf;
import com.medops.shared.exception.InvalidRequestException;

public final class MultipartPdfs {

    private MultipartPdfs() {
    }

    public static UploadedPdf required(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("A PDF file is required");
        }
        return read(file);
    }

    public static UploadedPdf optional(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        return read(file);
    }

    private static UploadedPdf read(MultipartFile file) {
        try {
            return new UploadedPdf(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException ex) {
            throw new InvalidRequestException("The uploaded file could not be read");
        }
    }
}
