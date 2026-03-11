package xyz.tcheeric.nostrdb.inspector.util;

import java.util.Map;

/**
 * Maps Nostr event kind numbers to human-readable labels.
 */
public final class KindLabels {

    private static final Map<Integer, String> LABELS = Map.ofEntries(
            Map.entry(0, "Metadata"),
            Map.entry(1, "Short Text Note"),
            Map.entry(2, "Recommend Relay"),
            Map.entry(3, "Contacts"),
            Map.entry(4, "Encrypted DM"),
            Map.entry(5, "Event Deletion"),
            Map.entry(6, "Repost"),
            Map.entry(7, "Reaction"),
            Map.entry(8, "Badge Award"),
            Map.entry(16, "Generic Repost"),
            Map.entry(40, "Channel Creation"),
            Map.entry(41, "Channel Metadata"),
            Map.entry(42, "Channel Message"),
            Map.entry(43, "Channel Hide Message"),
            Map.entry(44, "Channel Mute User"),
            Map.entry(1063, "File Metadata"),
            Map.entry(1984, "Reporting"),
            Map.entry(9735, "Zap"),
            Map.entry(9734, "Zap Request"),
            Map.entry(10000, "Mute List"),
            Map.entry(10001, "Pin List"),
            Map.entry(10002, "Relay List"),
            Map.entry(13194, "Wallet Info"),
            Map.entry(22242, "Client Authentication"),
            Map.entry(23194, "Wallet Request"),
            Map.entry(23195, "Wallet Response"),
            Map.entry(24133, "Nostr Connect"),
            Map.entry(27235, "HTTP Auth"),
            Map.entry(30000, "Categorized People List"),
            Map.entry(30001, "Categorized Bookmark List"),
            Map.entry(30008, "Profile Badges"),
            Map.entry(30009, "Badge Definition"),
            Map.entry(30023, "Long-form Content"),
            Map.entry(30078, "Application-specific Data")
    );

    private KindLabels() {}

    /**
     * Get the label for a kind number, or "Kind {n}" if unknown.
     */
    public static String getLabel(int kind) {
        return LABELS.getOrDefault(kind, "Kind " + kind);
    }
}
