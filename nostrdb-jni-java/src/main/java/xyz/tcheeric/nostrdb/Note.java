package xyz.tcheeric.nostrdb;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A Nostr note (event).
 *
 * <p>Notes are immutable event objects containing:
 * <ul>
 *   <li>id - 32-byte event ID (sha256 hash)</li>
 *   <li>pubkey - 32-byte author public key</li>
 *   <li>created_at - Unix timestamp</li>
 *   <li>kind - Event kind number</li>
 *   <li>content - Event content string</li>
 *   <li>tags - Array of tag arrays</li>
 *   <li>sig - 64-byte Schnorr signature</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Note {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String id;
    private final String pubkey;
    private final long createdAt;
    private final int kind;
    private final String content;
    private final List<List<String>> tags;
    private final String sig;

    @JsonCreator
    public Note(
            @JsonProperty("id") String id,
            @JsonProperty("pubkey") String pubkey,
            @JsonProperty("created_at") long createdAt,
            @JsonProperty("kind") int kind,
            @JsonProperty("content") String content,
            @JsonProperty("tags") List<List<String>> tags,
            @JsonProperty("sig") String sig) {
        this.id = id;
        this.pubkey = pubkey;
        this.createdAt = createdAt;
        this.kind = kind;
        this.content = content;
        this.tags = tags;
        this.sig = sig;
    }

    /**
     * Parse a note from JSON bytes.
     */
    static Note fromBytes(byte[] data) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            return MAPPER.readValue(json, Note.class);
        } catch (JsonProcessingException e) {
            throw new NostrdbException("Failed to parse note JSON", e);
        }
    }

    /**
     * Parse a note from a JSON string.
     */
    public static Note fromJson(String json) {
        try {
            return MAPPER.readValue(json, Note.class);
        } catch (JsonProcessingException e) {
            throw new NostrdbException("Failed to parse note JSON", e);
        }
    }

    /**
     * Get the event ID (hex-encoded).
     */
    public String id() {
        return id;
    }

    /**
     * Get the event ID as raw bytes.
     */
    public byte[] idBytes() {
        return id != null ? HexUtil.decode(id) : null;
    }

    /**
     * Get the author public key (hex-encoded).
     */
    public String pubkey() {
        return pubkey;
    }

    /**
     * Get the author public key as raw bytes.
     */
    public byte[] pubkeyBytes() {
        return pubkey != null ? HexUtil.decode(pubkey) : null;
    }

    /**
     * Get the creation timestamp (Unix seconds).
     */
    public long createdAt() {
        return createdAt;
    }

    /**
     * Get the event kind.
     */
    public int kind() {
        return kind;
    }

    /**
     * Get the event content.
     */
    public String content() {
        return content;
    }

    /**
     * Get the event tags.
     */
    public List<List<String>> tags() {
        return tags;
    }

    /**
     * Get the first value of a specific tag.
     *
     * @param tagName The tag name (e.g., "d", "p", "e")
     * @return The first tag value, or null if not found
     */
    public String getTagValue(String tagName) {
        if (tags == null) return null;
        for (List<String> tag : tags) {
            if (tag.size() >= 2 && tagName.equals(tag.get(0))) {
                return tag.get(1);
            }
        }
        return null;
    }

    /**
     * Get all values of a specific tag.
     *
     * @param tagName The tag name (e.g., "d", "p", "e")
     * @return List of tag values
     */
    public List<String> getTagValues(String tagName) {
        if (tags == null) return List.of();
        return tags.stream()
            .filter(tag -> tag.size() >= 2 && tagName.equals(tag.get(0)))
            .map(tag -> tag.get(1))
            .toList();
    }

    /**
     * Get the signature (hex-encoded).
     */
    public String sig() {
        return sig;
    }

    /**
     * Convert to JSON string.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new NostrdbException("Failed to serialize note to JSON", e);
        }
    }

    @Override
    public String toString() {
        return "Note{" +
            "id='" + (id != null ? id.substring(0, 8) + "..." : "null") + '\'' +
            ", kind=" + kind +
            ", pubkey='" + (pubkey != null ? pubkey.substring(0, 8) + "..." : "null") + '\'' +
            ", createdAt=" + createdAt +
            ", content='" + (content != null && content.length() > 50
                ? content.substring(0, 50) + "..."
                : content) + '\'' +
            '}';
    }
}
