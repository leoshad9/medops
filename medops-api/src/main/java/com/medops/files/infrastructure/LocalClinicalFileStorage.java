package com.medops.files.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.medops.files.domain.ClinicalFileStorage;
import com.medops.files.domain.StoredFile;
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalClinicalFileStorage implements ClinicalFileStorage {

    private static final int MAX_FILENAME_LENGTH = 200;

    private final ClinicalFileProperties properties;

    @Override
    public StoredFile store(String category, byte[] content, String originalFilename, String contentType) {
        String safeName = sanitizeFilename(originalFilename);
        String storageKey = category + "/" + UUID.randomUUID() + ".pdf";
        Path destination = root().resolve(storageKey).normalize();
        if (!destination.startsWith(root())) {
            throw new IllegalStateException("Refusing to write outside the clinical files directory");
        }
        try {
            Files.createDirectories(destination.getParent());
            Files.write(destination, content);
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to store clinical file", ex);
        }
        return new StoredFile(storageKey, safeName, contentType, content.length);
    }

    @Override
    public Resource load(String storageKey) {
        Path path = root().resolve(storageKey).normalize();
        if (!path.startsWith(root()) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("File not found");
        }
        return new FileSystemResource(path);
    }

    private Path root() {
        return Path.of(properties.root()).toAbsolutePath().normalize();
    }

    static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "document.pdf";
        }
        String name = Path.of(originalFilename).getFileName().toString();
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!name.toLowerCase().endsWith(".pdf")) {
            name = name + ".pdf";
        }
        return name.length() > MAX_FILENAME_LENGTH
                ? name.substring(name.length() - MAX_FILENAME_LENGTH)
                : name;
    }
}
