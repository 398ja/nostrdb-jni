package xyz.tcheeric.nostrdb.inspector.model;

import java.util.List;

/**
 * View model for displaying a note.
 *
 * @param id                Full hex event ID
 * @param idShort           Truncated hex ID for display
 * @param pubkey            Full hex author pubkey
 * @param pubkeyShort       Truncated hex pubkey for display
 * @param authorName        Resolved profile name, or null
 * @param kind              Event kind number
 * @param kindLabel         Human-readable kind label
 * @param content           Full event content
 * @param contentTruncated  First 200 chars of content
 * @param createdAt         Formatted timestamp
 * @param createdAtRelative Relative timestamp (e.g., "2 hours ago")
 * @param tags              Event tags
 * @param sig               Full hex signature
 */
public record NoteViewModel(
        String id,
        String idShort,
        String pubkey,
        String pubkeyShort,
        String authorName,
        int kind,
        String kindLabel,
        String content,
        String contentTruncated,
        String createdAt,
        String createdAtRelative,
        List<List<String>> tags,
        String sig
) {}
