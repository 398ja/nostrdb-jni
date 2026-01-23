package xyz.tcheeric.nostrdb;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A query filter for nostrdb.
 *
 * <p>Use the builder to construct filters:
 * <pre>{@code
 * try (Filter filter = Filter.builder()
 *         .kinds(1, 6, 7)
 *         .authors("pubkey1hex", "pubkey2hex")
 *         .since(Instant.now().minusSeconds(3600).getEpochSecond())
 *         .limit(100)
 *         .build()) {
 *
 *     List<QueryResult> results = ndb.query(txn, filter);
 * }
 * }</pre>
 */
public final class Filter implements Closeable {

    /**
     * Maximum allowed limit for queries.
     * This prevents integer overflow and excessive memory allocation in native code.
     * Value chosen to be large enough for practical use but safe from overflow when
     * multiplied by small factors (e.g., limit * 10 for chunk reassembly).
     */
    public static final int MAX_LIMIT = 100_000_000;

    private final long ptr;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Filter(long ptr) {
        this.ptr = ptr;
    }

    /**
     * Create a new filter builder.
     *
     * @return A new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the native pointer (for internal use).
     */
    long ptr() {
        checkOpen();
        return ptr;
    }

    private void checkOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Filter is closed");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            NostrdbNative.filterDestroy(ptr);
        }
    }

    /**
     * Builder for constructing filters.
     */
    public static final class Builder {
        private long ptr;
        private boolean built = false;

        private Builder() {
            this.ptr = NostrdbNative.filterNew();
            if (this.ptr == 0) {
                throw new NostrdbException("Failed to create filter");
            }
        }

        /**
         * Add event kinds to match.
         *
         * @param kinds The kinds to match (e.g., 1 for text notes, 0 for profiles)
         * @return this builder
         */
        public Builder kinds(int... kinds) {
            checkNotBuilt();
            if (kinds == null || kinds.length == 0) {
                return this;
            }

            ByteBuffer buf = ByteBuffer.allocate(kinds.length * 4)
                .order(ByteOrder.LITTLE_ENDIAN);
            for (int kind : kinds) {
                buf.putInt(kind);
            }

            ptr = NostrdbNative.filterKinds(ptr, buf.array());
            if (ptr == 0) {
                throw new NostrdbException("Failed to add kinds to filter");
            }
            return this;
        }

        /**
         * Add author public keys to match (hex-encoded).
         *
         * @param pubkeysHex 64-character hex public keys
         * @return this builder
         */
        public Builder authors(String... pubkeysHex) {
            checkNotBuilt();
            if (pubkeysHex == null || pubkeysHex.length == 0) {
                return this;
            }

            ByteBuffer buf = ByteBuffer.allocate(pubkeysHex.length * 32);
            for (String hex : pubkeysHex) {
                buf.put(HexUtil.decode(hex));
            }

            ptr = NostrdbNative.filterAuthors(ptr, buf.array());
            if (ptr == 0) {
                throw new NostrdbException("Failed to add authors to filter");
            }
            return this;
        }

        /**
         * Add author public keys to match (raw bytes).
         *
         * @param pubkeys 32-byte public keys
         * @return this builder
         */
        public Builder authors(byte[]... pubkeys) {
            checkNotBuilt();
            if (pubkeys == null || pubkeys.length == 0) {
                return this;
            }

            ByteBuffer buf = ByteBuffer.allocate(pubkeys.length * 32);
            for (byte[] pubkey : pubkeys) {
                if (pubkey.length != 32) {
                    throw new IllegalArgumentException("Pubkey must be 32 bytes");
                }
                buf.put(pubkey);
            }

            ptr = NostrdbNative.filterAuthors(ptr, buf.array());
            if (ptr == 0) {
                throw new NostrdbException("Failed to add authors to filter");
            }
            return this;
        }

        /**
         * Add a tag filter.
         *
         * @param tagName Single-character tag name (e.g., "d", "p", "e")
         * @param values Tag values to match
         * @return this builder
         */
        public Builder tag(String tagName, String... values) {
            checkNotBuilt();
            if (tagName == null || values == null || values.length == 0) {
                return this;
            }

            ptr = NostrdbNative.filterTag(ptr, tagName, values);
            if (ptr == 0) {
                throw new NostrdbException("Failed to add tag to filter");
            }
            return this;
        }

        /**
         * Add a "d" tag filter (commonly used for parameterized replaceable events).
         *
         * @param values Tag values to match
         * @return this builder
         */
        public Builder dTag(String... values) {
            return tag("d", values);
        }

        /**
         * Add an "e" tag filter (event references).
         *
         * @param eventIdsHex 64-character hex event IDs
         * @return this builder
         */
        public Builder eTag(String... eventIdsHex) {
            return tag("e", eventIdsHex);
        }

        /**
         * Add a "p" tag filter (pubkey references).
         *
         * @param pubkeysHex 64-character hex public keys
         * @return this builder
         */
        public Builder pTag(String... pubkeysHex) {
            return tag("p", pubkeysHex);
        }

        /**
         * Set the since timestamp (events created after this time).
         *
         * @param since Unix timestamp in seconds
         * @return this builder
         */
        public Builder since(long since) {
            checkNotBuilt();
            ptr = NostrdbNative.filterSince(ptr, since);
            if (ptr == 0) {
                throw new NostrdbException("Failed to set since on filter");
            }
            return this;
        }

        /**
         * Set the until timestamp (events created before this time).
         *
         * @param until Unix timestamp in seconds
         * @return this builder
         */
        public Builder until(long until) {
            checkNotBuilt();
            ptr = NostrdbNative.filterUntil(ptr, until);
            if (ptr == 0) {
                throw new NostrdbException("Failed to set until on filter");
            }
            return this;
        }

        /**
         * Set the result limit.
         *
         * @param limit Maximum number of results (must be positive and at most {@link Filter#MAX_LIMIT})
         * @return this builder
         * @throws IllegalArgumentException if limit is not positive or exceeds MAX_LIMIT
         */
        public Builder limit(int limit) {
            checkNotBuilt();
            if (limit <= 0) {
                throw new IllegalArgumentException("Limit must be positive, got: " + limit);
            }
            if (limit > MAX_LIMIT) {
                throw new IllegalArgumentException(
                        "Limit exceeds maximum allowed value of " + MAX_LIMIT + ", got: " + limit);
            }
            ptr = NostrdbNative.filterLimit(ptr, limit);
            if (ptr == 0) {
                throw new NostrdbException("Failed to set limit on filter");
            }
            return this;
        }

        /**
         * Set full-text search query.
         *
         * @param search Search query string
         * @return this builder
         */
        public Builder search(String search) {
            checkNotBuilt();
            if (search == null || search.isEmpty()) {
                return this;
            }
            ptr = NostrdbNative.filterSearch(ptr, search);
            if (ptr == 0) {
                throw new NostrdbException("Failed to set search on filter");
            }
            return this;
        }

        /**
         * Build the filter.
         *
         * @return The constructed Filter
         */
        public Filter build() {
            checkNotBuilt();
            built = true;

            long filterPtr = NostrdbNative.filterBuild(ptr);
            if (filterPtr == 0) {
                throw new NostrdbException("Failed to build filter");
            }
            return new Filter(filterPtr);
        }

        private void checkNotBuilt() {
            if (built) {
                throw new IllegalStateException("Filter has already been built");
            }
        }
    }
}
