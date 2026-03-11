package xyz.tcheeric.nostrdb.inspector.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FormattingTest {

    // ========================================================================
    // formatSize
    // ========================================================================

    @Test
    void formatSize_zero() {
        assertEquals("0 B", Formatting.formatSize(0));
    }

    @Test
    void formatSize_bytes() {
        assertEquals("512 B", Formatting.formatSize(512));
        assertEquals("1023 B", Formatting.formatSize(1023));
    }

    @Test
    void formatSize_kilobytes() {
        assertEquals("1.0 KB", Formatting.formatSize(1024));
        assertEquals("1.5 KB", Formatting.formatSize(1536));
    }

    @Test
    void formatSize_megabytes() {
        assertEquals("1.0 MB", Formatting.formatSize(1024 * 1024));
        assertEquals("100.0 MB", Formatting.formatSize(100L * 1024 * 1024));
    }

    @Test
    void formatSize_gigabytes() {
        assertEquals("1.0 GB", Formatting.formatSize(1024L * 1024 * 1024));
        assertEquals("2.5 GB", Formatting.formatSize((long) (2.5 * 1024 * 1024 * 1024)));
    }

    @Test
    void formatSize_terabytes() {
        assertEquals("1.0 TB", Formatting.formatSize(1024L * 1024 * 1024 * 1024));
    }

    @Test
    void formatSize_negative() {
        assertEquals("0 B", Formatting.formatSize(-1));
        assertEquals("0 B", Formatting.formatSize(-100));
    }

    // ========================================================================
    // formatTimestamp
    // ========================================================================

    @Test
    void formatTimestamp_epoch() {
        assertEquals("1970-01-01 00:00:00", Formatting.formatTimestamp(0));
    }

    @Test
    void formatTimestamp_knownDate() {
        // 2024-01-01 00:00:00 UTC = 1704067200
        assertEquals("2024-01-01 00:00:00", Formatting.formatTimestamp(1704067200));
    }

    @Test
    void formatTimestamp_withTime() {
        // 2023-07-15 12:00:00 UTC = 1689422400
        assertEquals("2023-07-15 12:00:00", Formatting.formatTimestamp(1689422400));
    }

    // ========================================================================
    // formatRelativeTime
    // ========================================================================

    @Test
    void formatRelativeTime_seconds() {
        long now = Instant.now().getEpochSecond();
        assertEquals("30s ago", Formatting.formatRelativeTime(now - 30));
    }

    @Test
    void formatRelativeTime_minutes() {
        long now = Instant.now().getEpochSecond();
        assertEquals("5m ago", Formatting.formatRelativeTime(now - 300));
    }

    @Test
    void formatRelativeTime_hours() {
        long now = Instant.now().getEpochSecond();
        assertEquals("2h ago", Formatting.formatRelativeTime(now - 7200));
    }

    @Test
    void formatRelativeTime_days() {
        long now = Instant.now().getEpochSecond();
        assertEquals("3d ago", Formatting.formatRelativeTime(now - 3 * 86400));
    }

    @Test
    void formatRelativeTime_months() {
        long now = Instant.now().getEpochSecond();
        assertEquals("2mo ago", Formatting.formatRelativeTime(now - 2L * 2592000));
    }

    @Test
    void formatRelativeTime_years() {
        long now = Instant.now().getEpochSecond();
        assertEquals("1y ago", Formatting.formatRelativeTime(now - 31536000));
    }

    @Test
    void formatRelativeTime_future() {
        long now = Instant.now().getEpochSecond();
        assertEquals("in the future", Formatting.formatRelativeTime(now + 3600));
    }

    @Test
    void formatRelativeTime_justNow() {
        long now = Instant.now().getEpochSecond();
        assertEquals("0s ago", Formatting.formatRelativeTime(now));
    }

    // ========================================================================
    // truncateHex
    // ========================================================================

    @Test
    void truncateHex_null() {
        assertNull(Formatting.truncateHex(null));
    }

    @Test
    void truncateHex_shortString() {
        assertEquals("abcdef", Formatting.truncateHex("abcdef"));
        assertEquals("12345678901234567890", Formatting.truncateHex("12345678901234567890"));
    }

    @Test
    void truncateHex_64charHex() {
        String hex64 = "d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027";
        assertEquals("d7dd5eb3...9fcb9027", Formatting.truncateHex(hex64));
    }

    @Test
    void truncateHex_longString() {
        String long21 = "123456789012345678901";
        assertEquals("12345678...45678901", Formatting.truncateHex(long21));
    }

    // ========================================================================
    // truncateContent
    // ========================================================================

    @Test
    void truncateContent_null() {
        assertNull(Formatting.truncateContent(null, 10));
    }

    @Test
    void truncateContent_shortContent() {
        assertEquals("Hello", Formatting.truncateContent("Hello", 10));
    }

    @Test
    void truncateContent_exactLength() {
        assertEquals("1234567890", Formatting.truncateContent("1234567890", 10));
    }

    @Test
    void truncateContent_truncated() {
        assertEquals("Hello, Wor...", Formatting.truncateContent("Hello, World!", 10));
    }

    @Test
    void truncateContent_emptyString() {
        assertEquals("", Formatting.truncateContent("", 10));
    }

    // ========================================================================
    // isValidHex64
    // ========================================================================

    @Test
    void isValidHex64_valid() {
        assertTrue(Formatting.isValidHex64("d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027"));
    }

    @Test
    void isValidHex64_validAllLowercase() {
        assertTrue(Formatting.isValidHex64("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    }

    @Test
    void isValidHex64_validMixedCase() {
        assertTrue(Formatting.isValidHex64("AABBCCDDaabbccdd1122334455667788AABBCCDDaabbccdd1122334455667788"));
    }

    @Test
    void isValidHex64_null() {
        assertFalse(Formatting.isValidHex64(null));
    }

    @Test
    void isValidHex64_tooShort() {
        assertFalse(Formatting.isValidHex64("abcdef"));
    }

    @Test
    void isValidHex64_tooLong() {
        assertFalse(Formatting.isValidHex64("d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb90270"));
    }

    @Test
    void isValidHex64_invalidChars() {
        assertFalse(Formatting.isValidHex64("g7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027"));
    }

    @Test
    void isValidHex64_spaces() {
        assertFalse(Formatting.isValidHex64("d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb902 "));
    }
}
