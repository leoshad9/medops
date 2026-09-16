package com.medops.auth.security.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordResetCodecTest {

    private final PasswordResetCodec codec = new PasswordResetCodec();

    /** Verifies that generate otp returns six digits by default. */
    @Test
    void generateOtp_returnsSixDigitsByDefault() {
        String otp = codec.generateOtp(6);
        assertThat(otp).matches("\\d{6}");
    }

    /** Verifies that generate otp returns requested length. */
    @Test
    void generateOtp_returnsRequestedLength() {
        assertThat(codec.generateOtp(4)).matches("\\d{4}");
        assertThat(codec.generateOtp(9)).matches("\\d{9}");
    }

    /** Verifies that generate otp throws for length zero. */
    @Test
    void generateOtp_throwsForLengthZero() {
        assertThatThrownBy(() -> codec.generateOtp(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Verifies that generate otp throws for length ten. */
    @Test
    void generateOtp_throwsForLengthTen() {
        assertThatThrownBy(() -> codec.generateOtp(10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Verifies that generate otp throws for negative length. */
    @Test
    void generateOtp_throwsForNegativeLength() {
        assertThatThrownBy(() -> codec.generateOtp(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Verifies that hmac sha256 throws for blank secret. */
    @Test
    void hmacSha256_throwsForBlankSecret() {
        assertThatThrownBy(() -> codec.hmacSha256("123456", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> codec.hmacSha256("123456", "   "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> codec.hmacSha256("123456", null))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Verifies that hmac sha256 produces different output for different secrets. */
    @Test
    void hmacSha256_producesDifferentOutputForDifferentSecrets() {
        String a = codec.hmacSha256("123456", "secret-a");
        String b = codec.hmacSha256("123456", "secret-b");
        assertThat(a).isNotEqualTo(b);
    }

    /** Verifies that hmac sha256 produces different output for different data. */
    @Test
    void hmacSha256_producesDifferentOutputForDifferentData() {
        String a = codec.hmacSha256("123456", "secret");
        String b = codec.hmacSha256("654321", "secret");
        assertThat(a).isNotEqualTo(b);
    }

    /** Verifies that hmac sha256 is deterministic. */
    @Test
    void hmacSha256_isDeterministic() {
        String a = codec.hmacSha256("123456", "secret");
        String b = codec.hmacSha256("123456", "secret");
        assertThat(a).isEqualTo(b);
    }

    /** Verifies that sha256 is deterministic. */
    @Test
    void sha256_isDeterministic() {
        String a = codec.sha256("test-token");
        String b = codec.sha256("test-token");
        assertThat(a).isEqualTo(b);
    }

    /** Verifies that sha256 produces different output for different input. */
    @Test
    void sha256_producesDifferentOutputForDifferentInput() {
        assertThat(codec.sha256("token-a")).isNotEqualTo(codec.sha256("token-b"));
    }

    /** Verifies that constant time equals returns true for equal. */
    @Test
    void constantTimeEquals_returnsTrueForEqual() {
        String hash = codec.sha256("test");
        assertThat(codec.constantTimeEquals(hash, hash)).isTrue();
    }

    /** Verifies that constant time equals returns false for different. */
    @Test
    void constantTimeEquals_returnsFalseForDifferent() {
        assertThat(codec.constantTimeEquals("abc", "def")).isFalse();
    }

    /** Verifies that constant time equals returns false for null expected. */
    @Test
    void constantTimeEquals_returnsFalseForNullExpected() {
        assertThat(codec.constantTimeEquals(null, "abc")).isFalse();
    }

    /** Verifies that constant time equals returns false for null actual. */
    @Test
    void constantTimeEquals_returnsFalseForNullActual() {
        assertThat(codec.constantTimeEquals("abc", null)).isFalse();
    }

    /** Verifies that constant time equals returns false for both null. */
    @Test
    void constantTimeEquals_returnsFalseForBothNull() {
        assertThat(codec.constantTimeEquals(null, null)).isFalse();
    }

    /** Verifies that constant time equals returns true for empty strings. */
    @Test
    void constantTimeEquals_returnsTrueForEmptyStrings() {
        assertThat(codec.constantTimeEquals("", "")).isTrue();
    }
}
