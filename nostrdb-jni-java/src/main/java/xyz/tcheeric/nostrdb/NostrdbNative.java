package xyz.tcheeric.nostrdb;

/**
 * Native method declarations for nostrdb JNI bindings.
 * This class loads the native library and declares all JNI methods.
 *
 * <p>The native library is loaded from either:
 * <ul>
 *   <li>java.library.path (for development)</li>
 *   <li>JAR resources (for distribution)</li>
 * </ul>
 */
final class NostrdbNative {

    private static volatile boolean loaded = false;
    private static volatile Throwable loadError = null;

    static {
        loadNativeLibrary();
    }

    private static synchronized void loadNativeLibrary() {
        if (loaded) return;

        try {
            // Try loading from java.library.path first
            System.loadLibrary("nostrdb_jni");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            try {
                // Fall back to loading from JAR resources
                NativeLoader.loadFromJar("nostrdb_jni");
                loaded = true;
            } catch (Exception ex) {
                loadError = ex;
                throw new RuntimeException("Failed to load nostrdb native library", ex);
            }
        }
    }

    /**
     * Check if the native library is loaded.
     */
    static boolean isLoaded() {
        return loaded;
    }

    /**
     * Get the error that occurred during loading, if any.
     */
    static Throwable getLoadError() {
        return loadError;
    }

    private NostrdbNative() {}

    // ========================================================================
    // Lifecycle
    // ========================================================================

    /**
     * Open a nostrdb database.
     *
     * @param dbPath Path to the database directory
     * @param configPtr Pointer to configuration (0 for defaults)
     * @return Pointer to the Ndb instance, or 0 on error
     */
    static native long ndbOpen(String dbPath, long configPtr);

    /**
     * Close a nostrdb database.
     *
     * @param ndbPtr Pointer to the Ndb instance
     */
    static native void ndbClose(long ndbPtr);

    // ========================================================================
    // Event Ingestion
    // ========================================================================

    /**
     * Process a single JSON event.
     *
     * @param ndbPtr Pointer to the Ndb instance
     * @param json JSON string of the event
     * @return 1 on success, 0 on failure
     */
    static native int processEvent(long ndbPtr, String json);

    /**
     * Process multiple newline-delimited JSON events.
     *
     * @param ndbPtr Pointer to the Ndb instance
     * @param ldjson Newline-delimited JSON events
     * @return Number of events processed, or -1 on error
     */
    static native int processEvents(long ndbPtr, String ldjson);

    // ========================================================================
    // Transaction
    // ========================================================================

    /**
     * Begin a read transaction.
     * IMPORTANT: Only one transaction per thread!
     *
     * @param ndbPtr Pointer to the Ndb instance
     * @return Pointer to the Transaction, or 0 on error
     */
    static native long beginTransaction(long ndbPtr);

    /**
     * End a transaction.
     *
     * @param txnPtr Pointer to the Transaction
     */
    static native void endTransaction(long txnPtr);

    // ========================================================================
    // Note Retrieval
    // ========================================================================

    /**
     * Get a note by its 32-byte event ID.
     *
     * @param ndbPtr Pointer to the Ndb instance
     * @param txnPtr Pointer to the Transaction
     * @param eventId 32-byte event ID
     * @return Serialized note as JSON bytes, or null if not found
     */
    static native byte[] getNoteById(long ndbPtr, long txnPtr, byte[] eventId);

    /**
     * Get a note by its internal key (faster for repeated lookups).
     *
     * @param ndbPtr Pointer to the Ndb instance
     * @param txnPtr Pointer to the Transaction
     * @param noteKey Internal note key
     * @return Serialized note as JSON bytes, or null if not found
     */
    static native byte[] getNoteByKey(long ndbPtr, long txnPtr, long noteKey);

    // ========================================================================
    // Query
    // ========================================================================

    /**
     * Execute a query with a filter.
     *
     * @param ndbPtr Pointer to the Ndb instance
     * @param txnPtr Pointer to the Transaction
     * @param filterPtr Pointer to the Filter
     * @param limit Maximum number of results
     * @return Serialized results: [count:4][key1:8][key2:8]...
     */
    static native byte[] query(long ndbPtr, long txnPtr, long filterPtr, int limit);

    // ========================================================================
    // Filter Building
    // ========================================================================

    /**
     * Create a new filter builder.
     *
     * @return Pointer to the FilterBuilder
     */
    static native long filterNew();

    /**
     * Add kinds to the filter.
     *
     * @param filterPtr Pointer to the FilterBuilder
     * @param kinds Serialized kinds: [kind1:4][kind2:4]...
     * @return New filter pointer (old one is consumed)
     */
    static native long filterKinds(long filterPtr, byte[] kinds);

    /**
     * Add authors to the filter.
     *
     * @param filterPtr Pointer to the FilterBuilder
     * @param authors Serialized authors: [pubkey1:32][pubkey2:32]...
     * @return New filter pointer (old one is consumed)
     */
    static native long filterAuthors(long filterPtr, byte[] authors);

    /**
     * Add a tag filter.
     *
     * @param filterPtr Pointer to the FilterBuilder
     * @param tagName Single-character tag name (e.g., "d", "p", "e")
     * @param tagValues Array of tag values
     * @return New filter pointer (old one is consumed)
     */
    static native long filterTag(long filterPtr, String tagName, String[] tagValues);

    /**
     * Set the since timestamp.
     *
     * @param filterPtr Pointer to the FilterBuilder
     * @param since Unix timestamp
     * @return New filter pointer (old one is consumed)
     */
    static native long filterSince(long filterPtr, long since);

    /**
     * Set the until timestamp.
     *
     * @param filterPtr Pointer to the FilterBuilder
     * @param until Unix timestamp
     * @return New filter pointer (old one is consumed)
     */
    static native long filterUntil(long filterPtr, long until);

    /**
     * Set the result limit.
     *
     * @param filterPtr Pointer to the FilterBuilder
     * @param limit Maximum number of results
     * @return New filter pointer (old one is consumed)
     */
    static native long filterLimit(long filterPtr, long limit);

    /**
     * Set full-text search query.
     *
     * @param filterPtr Pointer to the FilterBuilder
     * @param search Search query
     * @return New filter pointer (old one is consumed)
     */
    static native long filterSearch(long filterPtr, String search);

    /**
     * Build the filter (finalize).
     *
     * @param filterPtr Pointer to the FilterBuilder
     * @return Pointer to the built Filter
     */
    static native long filterBuild(long filterPtr);

    /**
     * Destroy a filter.
     *
     * @param filterPtr Pointer to the Filter
     */
    static native void filterDestroy(long filterPtr);

    // ========================================================================
    // Profile
    // ========================================================================

    /**
     * Get a profile by pubkey.
     *
     * @param ndbPtr Pointer to the Ndb instance
     * @param txnPtr Pointer to the Transaction
     * @param pubkey 32-byte public key
     * @return Serialized profile as JSON bytes, or null if not found
     */
    static native byte[] getProfileByPubkey(long ndbPtr, long txnPtr, byte[] pubkey);

    /**
     * Search profiles by name.
     *
     * @param ndbPtr Pointer to the Ndb instance
     * @param txnPtr Pointer to the Transaction
     * @param query Search query
     * @param limit Maximum number of results
     * @return Serialized results: [count:4][pubkey1:32][pubkey2:32]...
     */
    static native byte[] searchProfiles(long ndbPtr, long txnPtr, String query, int limit);

    // ========================================================================
    // Subscription
    // ========================================================================

    /**
     * Subscribe to events matching a filter.
     *
     * @param ndbPtr Pointer to the Ndb instance
     * @param filterPtr Pointer to the Filter
     * @return Subscription ID
     */
    static native long subscribe(long ndbPtr, long filterPtr);

    /**
     * Poll for new notes on a subscription.
     *
     * @param ndbPtr Pointer to the Ndb instance
     * @param subId Subscription ID
     * @param maxNotes Maximum notes to return
     * @return Serialized results: [count:4][key1:8][key2:8]...
     */
    static native byte[] pollForNotes(long ndbPtr, long subId, int maxNotes);

    /**
     * Unsubscribe from a subscription.
     *
     * @param ndbPtr Pointer to the Ndb instance
     * @param subId Subscription ID
     */
    static native void unsubscribe(long ndbPtr, long subId);
}
