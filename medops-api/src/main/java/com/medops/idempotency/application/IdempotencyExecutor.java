package com.medops.idempotency.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.idempotency.domain.IdempotencyRecord;
import com.medops.idempotency.domain.IdempotencyStore;
import com.medops.idempotency.infrastructure.IdempotencyProperties;
import com.medops.shared.exception.ConflictException;
import com.medops.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;

/**
 * Runs a write use case at most once per actor + operation + {@code Idempotency-Key}.
 * Missing keys pass through (clients may omit the header).
 */
@Service
@RequiredArgsConstructor
public class IdempotencyExecutor {

    private static final int MAX_KEY_LENGTH = 128;

    private final IdempotencyStore idempotencyStore;
    private final IdempotencyProperties properties;
    private final ObjectMapper objectMapper;

    public <T> T execute(
            String actor,
            String operation,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType,
            Supplier<T> action) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return action.get();
        }
        String key = validateKey(idempotencyKey);
        String storeKey = actor + ':' + operation + ':' + key;
        String hash = Objects.requireNonNull(requestHash, "requestHash");

        IdempotencyRecord existing = idempotencyStore.find(storeKey).orElse(null);
        if (existing != null) {
            return replayOrReject(existing, hash, responseType);
        }

        if (!idempotencyStore.tryBegin(storeKey, hash, properties.ttl())) {
            IdempotencyRecord raced = idempotencyStore.find(storeKey)
                    .orElseThrow(() -> new ConflictException(
                            "A request with this Idempotency-Key is already in progress"));
            return replayOrReject(raced, hash, responseType);
        }

        try {
            T result = action.get();
            idempotencyStore.complete(storeKey, hash, writeJson(result), properties.ttl());
            return result;
        } catch (RuntimeException ex) {
            idempotencyStore.abandon(storeKey);
            throw ex;
        }
    }

    public static String sha256(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update((part == null ? "" : part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private <T> T replayOrReject(IdempotencyRecord existing, String hash, Class<T> responseType) {
        if (!existing.requestHash().equals(hash)) {
            throw new ConflictException("Idempotency-Key was reused with a different request");
        }
        if (existing.status() == IdempotencyRecord.Status.STARTED) {
            throw new ConflictException("A request with this Idempotency-Key is already in progress");
        }
        return readJson(existing.responseJson(), responseType);
    }

    private static String validateKey(String idempotencyKey) {
        String key = idempotencyKey.trim();
        if (key.length() > MAX_KEY_LENGTH) {
            throw new InvalidRequestException("Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters");
        }
        if (!key.matches("[A-Za-z0-9._-]+")) {
            throw new InvalidRequestException("Idempotency-Key contains invalid characters");
        }
        return key;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize idempotent response", ex);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to deserialize idempotent response", ex);
        }
    }
}
