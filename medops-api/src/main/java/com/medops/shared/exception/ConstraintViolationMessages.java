package com.medops.shared.exception;

import java.util.LinkedHashMap;
import java.util.Map;

final class ConstraintViolationMessages {

    private static final Map<String, String> MESSAGES = new LinkedHashMap<>();

    static {
        MESSAGES.put("uq_patient_profiles_phone_number", "An account with this phone number already exists");
        MESSAGES.put("uq_doctor_profiles_phone_number",  "An account with this phone number already exists");
        MESSAGES.put("phone_number",                     "An account with this phone number already exists");
        MESSAGES.put("email",                            "An account with this email already exists");
        MESSAGES.put("license_number",                   "An account with this license number already exists");
        MESSAGES.put("uq_appointments",                  "That time is no longer available");
        MESSAGES.put("slot",                             "That time is no longer available");
        MESSAGES.put("appointment",                      "That time is no longer available");
    }

    private ConstraintViolationMessages() {}

    static String resolve(String dbMessage) {
        String lower = dbMessage != null ? dbMessage.toLowerCase() : "";
        return MESSAGES.entrySet().stream()
                .filter(e -> lower.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("A conflict occurred. The resource may already exist.");
    }
}
