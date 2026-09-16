package com.medops.shared.util;

/**
 * Minimal PII masking for log statements.
 *
 * <p>Raw email addresses and client IPs must never appear in logs; a log
 * aggregator breach would otherwise turn observability data into a PII leak.
 * Audit records (database rows) still carry the full values - this helper is
 * for log lines only.
 */
public final class LogMasking {

    /** Prevents instantiation of this log-masking utility. */
    private LogMasking() {
    }

    /**
     * Masks an email address, keeping the domain for debuggability.
     *
     * @param email the raw email address, may be {@code null}
     * @return a masked address such as {@code p***@medops.dev}, or {@code "(none)"} when blank
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "(none)";
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) {
            return "***";
        }
        return trimmed.charAt(0) + "***" + trimmed.substring(at);
    }

    /**
     * Masks a client IP address, keeping only coarse structure.
     *
     * @param ip the raw client IP, may be {@code null}
     * @return the masked IP (last octet/hextet replaced), or {@code "(none)"} when blank
     */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "(none)";
        }
        String trimmed = ip.trim();
        if (trimmed.contains(".")) {
            int lastDot = trimmed.lastIndexOf('.');
            return trimmed.substring(0, lastDot + 1) + "xxx";
        }
        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon >= 0) {
            return trimmed.substring(0, lastColon + 1) + "xxxx";
        }
        return "***";
    }
}
