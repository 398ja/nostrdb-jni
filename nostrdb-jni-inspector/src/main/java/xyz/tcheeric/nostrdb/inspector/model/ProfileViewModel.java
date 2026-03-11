package xyz.tcheeric.nostrdb.inspector.model;

/**
 * View model for displaying a profile.
 *
 * @param pubkey         Full hex public key
 * @param pubkeyShort    Truncated hex pubkey for display
 * @param name           Username
 * @param displayName    Display name
 * @param bestName       Best available display name
 * @param about          Bio/description
 * @param aboutTruncated Truncated bio for list display
 * @param picture        Avatar URL
 * @param banner         Banner image URL
 * @param nip05          NIP-05 identifier
 * @param lud16          Lightning address
 * @param website        Website URL
 * @param noteCount      Number of notes by this author in cache
 */
public record ProfileViewModel(
        String pubkey,
        String pubkeyShort,
        String name,
        String displayName,
        String bestName,
        String about,
        String aboutTruncated,
        String picture,
        String banner,
        String nip05,
        String lud16,
        String website,
        long noteCount
) {}
