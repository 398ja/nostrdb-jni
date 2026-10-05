package xyz.tcheeric.nostrdb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Ndb#copyNotes(Transaction, Ndb, long[])}.
 *
 * <p>The fixture {@code tricky-notes.ldjson} holds six signed kind-1 notes made by nostrdb
 * itself ({@code scripts/gen-tricky-notes.rs}): raw control characters 0x01-0x1f, DEL,
 * U+2028 and other non-ASCII text, emoji, quotes and backslashes, and tags with odd
 * content. The ids follow NIP-01, which escapes only {@code " \ \b \f \n \r \t}.
 */
class NdbCopyNotesTest {

    private static final int FIXTURE_NOTES = 6;

    @TempDir
    Path tempDir;

    // Every tricky note, copied with nostrdb's own serialiser, lands in the target with the
    // same id. A copy through Java JSON (Jackson writes \u0001, which nostrdb's parser
    // refuses) would lose the control-character notes.
    @Test
    @DisplayName("copyNotes reproduces every tricky note with the same id")
    void copiesTrickyNotesExactly() throws Exception {
        Set<String> sourceIds;
        long[] keys;
        try (Ndb source = Ndb.open(tempDir.resolve("src"))) {
            source.processEvents(String.join("\n", fixture()) + "\n");
            keys = waitForKeys(source, FIXTURE_NOTES);
            sourceIds = ids(source);

            try (Ndb target = Ndb.open(tempDir.resolve("dst"));
                 Transaction txn = source.beginTransaction()) {
                assertEquals(FIXTURE_NOTES, source.copyNotes(txn, target, keys));
            }
        }
        assertEquals(FIXTURE_NOTES, sourceIds.size(), "every fixture note was ingested");

        try (Ndb target = Ndb.open(tempDir.resolve("dst"))) {
            waitForKeys(target, FIXTURE_NOTES);
            assertEquals(sourceIds, ids(target));
        }
    }

    // A key that does not exist is skipped and not counted; the rest still copy.
    @Test
    @DisplayName("copyNotes skips keys that are not in the source")
    void skipsMissingKeys() throws Exception {
        try (Ndb source = Ndb.open(tempDir.resolve("src2"));
             Ndb target = Ndb.open(tempDir.resolve("dst2"))) {
            source.processEvents(String.join("\n", fixture()) + "\n");
            long[] keys = waitForKeys(source, FIXTURE_NOTES);
            long[] withMissing = java.util.Arrays.copyOf(keys, keys.length + 1);
            withMissing[keys.length] = 999_999L;
            try (Transaction txn = source.beginTransaction()) {
                assertEquals(FIXTURE_NOTES, source.copyNotes(txn, target, withMissing));
            }
        }
    }

    // Copying into itself or into nothing is refused before any native call.
    @Test
    @DisplayName("copyNotes rejects a null or same target")
    void rejectsBadTarget() {
        try (Ndb source = Ndb.open(tempDir.resolve("src3"));
             Transaction txn = source.beginTransaction()) {
            assertThrows(IllegalArgumentException.class, () -> source.copyNotes(txn, null, new long[]{1}));
            assertThrows(IllegalArgumentException.class, () -> source.copyNotes(txn, source, new long[]{1}));
        }
    }

    static List<String> fixture() throws IOException {
        try (InputStream in = NdbCopyNotesTest.class.getResourceAsStream("/tricky-notes.ldjson")) {
            String all = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            for (String line : all.split("\n")) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            assertEquals(FIXTURE_NOTES, lines.size(), "fixture size");
            return lines;
        }
    }

    private static Set<String> ids(Ndb ndb) {
        Set<String> ids = new HashSet<>();
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder().kinds(1).limit(100).build()) {
            for (QueryResult r : ndb.query(txn, filter, 100)) {
                ndb.getNoteByKey(txn, r.noteKey()).ifPresent(n -> ids.add(n.id()));
            }
        }
        return ids;
    }

    private static long[] waitForKeys(Ndb ndb, int expected) throws InterruptedException {
        List<QueryResult> results = List.of();
        for (int i = 0; i < 100; i++) {
            try (Transaction txn = ndb.beginTransaction();
                 Filter filter = Filter.builder().kinds(1).limit(100).build()) {
                results = ndb.query(txn, filter, 100);
            }
            if (results.size() >= expected) {
                break;
            }
            Thread.sleep(50);
        }
        return results.stream().mapToLong(QueryResult::noteKey).toArray();
    }
}
