package xyz.tcheeric.nostrdb;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * A Nostr profile (kind 0 event content).
 *
 * <p>Profiles contain user metadata:
 * <ul>
 *   <li>name - Display name</li>
 *   <li>display_name - Alternative display name</li>
 *   <li>about - Bio/description</li>
 *   <li>picture - Avatar URL</li>
 *   <li>banner - Banner image URL</li>
 *   <li>nip05 - NIP-05 identifier</li>
 *   <li>lud16 - Lightning address</li>
 *   <li>website - Website URL</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Profile {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String displayName;
    private final String about;
    private final String picture;
    private final String banner;
    private final String nip05;
    private final String lud16;
    private final String lud06;
    private final String website;

    @JsonCreator
    public Profile(
            @JsonProperty("name") String name,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("about") String about,
            @JsonProperty("picture") String picture,
            @JsonProperty("banner") String banner,
            @JsonProperty("nip05") String nip05,
            @JsonProperty("lud16") String lud16,
            @JsonProperty("lud06") String lud06,
            @JsonProperty("website") String website) {
        this.name = name;
        this.displayName = displayName;
        this.about = about;
        this.picture = picture;
        this.banner = banner;
        this.nip05 = nip05;
        this.lud16 = lud16;
        this.lud06 = lud06;
        this.website = website;
    }

    /**
     * Parse a profile from JSON bytes.
     */
    static Profile fromBytes(byte[] data) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            return MAPPER.readValue(json, Profile.class);
        } catch (JsonProcessingException e) {
            throw new NostrdbException("Failed to parse profile JSON", e);
        }
    }

    /**
     * Parse a profile from a JSON string.
     */
    public static Profile fromJson(String json) {
        try {
            return MAPPER.readValue(json, Profile.class);
        } catch (JsonProcessingException e) {
            throw new NostrdbException("Failed to parse profile JSON", e);
        }
    }

    /**
     * Get the username.
     */
    public String name() {
        return name;
    }

    /**
     * Get the display name.
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Get the best display name available.
     *
     * <p>Returns display_name if set, otherwise name.
     */
    public String bestDisplayName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return name;
    }

    /**
     * Get the about/bio text.
     */
    public String about() {
        return about;
    }

    /**
     * Get the avatar picture URL.
     */
    public String picture() {
        return picture;
    }

    /**
     * Get the banner image URL.
     */
    public String banner() {
        return banner;
    }

    /**
     * Get the NIP-05 identifier.
     */
    public String nip05() {
        return nip05;
    }

    /**
     * Get the Lightning address (LUD-16).
     */
    public String lud16() {
        return lud16;
    }

    /**
     * Get the LNURL (LUD-06).
     */
    public String lud06() {
        return lud06;
    }

    /**
     * Get the best Lightning address available.
     *
     * <p>Returns lud16 if set, otherwise lud06.
     */
    public String bestLightningAddress() {
        if (lud16 != null && !lud16.isBlank()) {
            return lud16;
        }
        return lud06;
    }

    /**
     * Get the website URL.
     */
    public String website() {
        return website;
    }

    /**
     * Convert to JSON string.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new NostrdbException("Failed to serialize profile to JSON", e);
        }
    }

    @Override
    public String toString() {
        return "Profile{" +
            "name='" + name + '\'' +
            ", displayName='" + displayName + '\'' +
            ", nip05='" + nip05 + '\'' +
            '}';
    }
}
