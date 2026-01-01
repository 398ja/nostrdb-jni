package xyz.tcheeric.nostrdb;

/**
 * Utility class for hexadecimal encoding and decoding.
 */
public final class HexUtil {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private HexUtil() {}

    /**
     * Encode bytes to a hexadecimal string.
     *
     * @param bytes The bytes to encode
     * @return The hexadecimal string
     */
    public static String encode(byte[] bytes) {
        if (bytes == null) return null;

        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Decode a hexadecimal string to bytes.
     *
     * @param hex The hexadecimal string
     * @return The decoded bytes
     * @throws IllegalArgumentException if the string is not valid hexadecimal
     */
    public static byte[] decode(String hex) {
        if (hex == null) return null;

        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }

        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);

            if (high == -1 || low == -1) {
                throw new IllegalArgumentException("Invalid hex character at position " + i);
            }

            data[i / 2] = (byte) ((high << 4) + low);
        }
        return data;
    }

    /**
     * Check if a string is valid hexadecimal.
     *
     * @param hex The string to check
     * @return true if the string is valid hexadecimal
     */
    public static boolean isValidHex(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return false;
        }

        for (int i = 0; i < hex.length(); i++) {
            if (Character.digit(hex.charAt(i), 16) == -1) {
                return false;
            }
        }
        return true;
    }
}
