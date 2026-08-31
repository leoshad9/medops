package com.medops.files.domain;

import org.springframework.core.io.Resource;

public interface ClinicalFileStorage {

    StoredFile store(String category, byte[] content, String originalFilename, String contentType);

    Resource load(String storageKey);
}
