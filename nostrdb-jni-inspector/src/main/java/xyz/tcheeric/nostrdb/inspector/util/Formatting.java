package xyz.tcheeric.nostrdb.inspector.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Formatting utilities for human-readable display.
 */
public final class Formatting {

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB", "TB"};

    private Formatting() {}

    /**
     * Format bytes into human-readable size (e.g., "100.0 MB").
     */
    public static String formatSize(long bytes) {
        if (bytes < 0) return "0 B";
        double size = bytes;
        int unit = 0;
        while (size >= 1024 && unit < SIZE_UNITS.length - 1) {
            size /= 1024;
            unit++;
        }
        if (unit == 0) return bytes + " B";
        return String.format("%.1f %s", size, SIZE_UNITS[unit]);
    }

    /**
     * Format a Unix timestamp as ISO 8601 string.
     */
    public static String formatTimestamp(long unixSeconds) {
        return ISO_FORMATTER.format(Instant.ofEpochSecond(unixSeconds));
    }

    /**
     * Format a Unix timestamp as a relative time string (e.g., "2 hours ago").
     */
    public static String formatRelativeTime(long unixSeconds) {
        long now = Instant.now().getEpochSecond();
        long diff = now - unixSeconds;

        if (diff < 0) return "in the future";
        if (diff < 60) return diff + "s ago";
        if (diff < 3600) return (diff / 60) + "m ago";
        if (diff < 86400) return (diff / 3600) + "h ago";
        if (diff < 2592000) return (diff / 86400) + "d ago";
        if (diff < 31536000) return (diff / 2592000) + "mo ago";
        return (diff / 31536000) + "y ago";
    }

    /**
     * Truncate a hex string: first 8 chars + "..." + last 8 chars.
     */
    public static String truncateHex(String hex) {
        if (hex == null) return null;
        if (hex.length() <= 20) return hex;
        return hex.substring(0, 8) + "..." + hex.substring(hex.length() - 8);
    }

    /**
     * Truncate content to a maximum length, adding "..." if truncated.
     */
    public static String truncateContent(String content, int maxLength) {
        if (content == null) return null;
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...";
    }

    /**
     * Check if a string is a valid 64-character hex string.
     */
    public static boolean isValidHex64(String hex) {
        if (hex == null || hex.length() != 64) return false;
        for (int i = 0; i < hex.length(); i++) {
            if (Character.digit(hex.charAt(i), 16) == -1) return false;
        }
        return true;
    }
}
