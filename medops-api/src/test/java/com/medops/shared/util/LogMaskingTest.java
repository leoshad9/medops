package com.medops.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogMaskingTest {

    @Test
    void maskEmail_masksLocalPart_keepsDomain() {
        assertThat(LogMasking.maskEmail("john.doe@medops.dev")).isEqualTo("j***@medops.dev");
    }

    @Test
    void maskEmail_returnsNone_forNull() {
        assertThat(LogMasking.maskEmail(null)).isEqualTo("(none)");
    }

    @Test
    void maskEmail_returnsNone_forBlank() {
        assertThat(LogMasking.maskEmail("   ")).isEqualTo("(none)");
    }

    @Test
    void maskEmail_returnsAsterisks_forInvalidEmail() {
        assertThat(LogMasking.maskEmail("noatsign")).isEqualTo("***");
        assertThat(LogMasking.maskEmail("@leading.com")).isEqualTo("***");
        assertThat(LogMasking.maskEmail("trailing@")).isEqualTo("***");
    }

    @Test
    void maskIp_masksIpv4LastOctet() {
        assertThat(LogMasking.maskIp("192.168.1.100")).isEqualTo("192.168.1.xxx");
    }

    @Test
    void maskIp_masksIpv6LastHextet() {
        assertThat(LogMasking.maskIp("2001:db8::1")).isEqualTo("2001:db8::xxxx");
    }

    @Test
    void maskIp_returnsNone_forNull() {
        assertThat(LogMasking.maskIp(null)).isEqualTo("(none)");
    }

    @Test
    void maskIp_returnsNone_forBlank() {
        assertThat(LogMasking.maskIp("   ")).isEqualTo("(none)");
    }

    @Test
    void maskIp_returnsAsterisks_forUnknownFormat() {
        assertThat(LogMasking.maskIp("hostname")).isEqualTo("***");
    }
}
