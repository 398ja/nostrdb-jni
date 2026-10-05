package xyz.tcheeric.nostrdb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Ndb#open(Path, long)}, the overload that sets the LMDB map size.
 *
 * <p>The fixture is 40 signed kind-1 notes of about 40 KB each (about 1.6 MB in total),
 * generated with {@code nak}. That is more than a 1 MiB map can hold and far less than the
 * 32 GiB default.
 */
class NdbMapSizeTest {

    private static final int FIXTURE_NOTES = 40;
    private static final long ONE_MIB = 1024L * 1024L;

    @TempDir
    Path tempDir;

    // With the default 32 GiB map every fixture note is stored. This is the control for the
    // map-full test below: it proves the fixture itself is valid and fully ingestible.
    @Test
    @DisplayName("default open stores every fixture note")
    void defaultOpenStoresEveryNote() throws Exception {
        List<String> events = fixture();

        int stored;
        try (Ndb ndb = Ndb.open(tempDir.resolve("default"))) {
            ndb.processEvents(String.join("\n", events));
            stored = waitForCount(ndb, FIXTURE_NOTES);
        }

        assertEquals(FIXTURE_NOTES, stored);
    }

    // A 1 MiB map cannot hold 1.6 MB of notes. If the map size were ignored (nostrdb's
    // default is 32 GiB) every note would be stored. Fewer stored notes, and a file no
    // larger than the map, prove the setting reached LMDB.
    @Test
    @DisplayName("a small map size caps the database and refuses notes that do not fit")
    void smallMapSizeCapsTheDatabase() throws Exception {
        List<String> events = fixture();

        int stored;
        long fileSize;
        try (Ndb ndb = Ndb.open(tempDir.resolve("small"), ONE_MIB)) {
            ndb.processEvents(String.join("\n", events));
            stored = waitForCount(ndb, FIXTURE_NOTES);
            fileSize = ndb.getDbFileSize();
        }

        assertTrue(stored < FIXTURE_NOTES, "expected the map to fill, but stored " + stored);
        assertTrue(fileSize <= ONE_MIB, "file " + fileSize + " is larger than the 1 MiB map");
    }

    // A map large enough for the fixture behaves like the default: nothing is refused.
    @Test
    @DisplayName("an ample map size stores every note")
    void ampleMapSizeStoresEveryNote() throws Exception {
        List<String> events = fixture();

        int stored;
        try (Ndb ndb = Ndb.open(tempDir.resolve("ample"), 64 * ONE_MIB)) {
            ndb.processEvents(String.join("\n", events));
            stored = waitForCount(ndb, FIXTURE_NOTES);
        }

        assertEquals(FIXTURE_NOTES, stored);
    }

    // Zero and negative sizes are rejected in Java before any native call is made.
    @Test
    @DisplayName("a non-positive map size is rejected")
    void nonPositiveMapSizeIsRejected() {
        Path path = tempDir.resolve("bad");

        assertThrows(IllegalArgumentException.class, () -> Ndb.open(path, 0L));
        assertThrows(IllegalArgumentException.class, () -> Ndb.open(path, -1L));
        assertThrows(IllegalArgumentException.class, () -> Ndb.open((Path) null, ONE_MIB));
    }

    private static List<String> fixture() throws IOException {
        try (InputStream raw = NdbMapSizeTest.class.getResourceAsStream("/big-notes.ldjson.gz");
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().filter(l -> !l.isBlank()).toList();
            assertEquals(FIXTURE_NOTES, lines.size(), "fixture size");
            return lines;
        }
    }

    /**
     * Ingestion is asynchronous, so poll until the expected count is reached or the count
     * stops moving for a while.
     */
    private static int waitForCount(Ndb ndb, int expected) throws InterruptedException {
        int last = -1;
        int stable = 0;
        for (int i = 0; i < 100; i++) {
            int count;
            try (Transaction txn = ndb.beginTransaction();
                 Filter filter = Filter.builder().kinds(1).limit(1000).build()) {
                count = ndb.query(txn, filter, 1000).size();
            }
            if (count >= expected) {
                return count;
            }
            stable = count == last ? stable + 1 : 0;
            if (stable >= 10) {
                return count;
            }
            last = count;
            Thread.sleep(100);
        }
        return last;
    }
}
