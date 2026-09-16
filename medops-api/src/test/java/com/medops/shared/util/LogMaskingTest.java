package com.medops.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogMaskingTest {

    /** Verifies that mask email masks local part keeps domain. */
    @Test
    void maskEmail_masksLocalPart_keepsDomain() {
        assertThat(LogMasking.maskEmail("john.doe@medops.dev")).isEqualTo("j***@medops.dev");
    }

    /** Verifies that mask email returns none for null. */
    @Test
    void maskEmail_returnsNone_forNull() {
        assertThat(LogMasking.maskEmail(null)).isEqualTo("(none)");
    }

    /** Verifies that mask email returns none for blank. */
    @Test
    void maskEmail_returnsNone_forBlank() {
        assertThat(LogMasking.maskEmail("   ")).isEqualTo("(none)");
    }

    /** Verifies that mask email returns asterisks for invalid email. */
    @Test
    void maskEmail_returnsAsterisks_forInvalidEmail() {
        assertThat(LogMasking.maskEmail("noatsign")).isEqualTo("***");
        assertThat(LogMasking.maskEmail("@leading.com")).isEqualTo("***");
        assertThat(LogMasking.maskEmail("trailing@")).isEqualTo("***");
    }

    /** Verifies that mask ip masks ipv4 last octet. */
    @Test
    void maskIp_masksIpv4LastOctet() {
        assertThat(LogMasking.maskIp("192.168.1.100")).isEqualTo("192.168.1.xxx");
    }

    /** Verifies that mask ip masks ipv6 last hextet. */
    @Test
    void maskIp_masksIpv6LastHextet() {
        assertThat(LogMasking.maskIp("2001:db8::1")).isEqualTo("2001:db8::xxxx");
    }

    /** Verifies that mask ip returns none for null. */
    @Test
    void maskIp_returnsNone_forNull() {
        assertThat(LogMasking.maskIp(null)).isEqualTo("(none)");
    }

    /** Verifies that mask ip returns none for blank. */
    @Test
    void maskIp_returnsNone_forBlank() {
        assertThat(LogMasking.maskIp("   ")).isEqualTo("(none)");
    }

    /** Verifies that mask ip returns asterisks for unknown format. */
    @Test
    void maskIp_returnsAsterisks_forUnknownFormat() {
        assertThat(LogMasking.maskIp("hostname")).isEqualTo("***");
    }
}
