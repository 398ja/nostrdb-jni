package xyz.tcheeric.nostrdb.inspector.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelsTest {

    // ========================================================================
    // KindStat
    // ========================================================================

    @Test
    void kindStat_construction() {
        KindStat stat = new KindStat(1, "Short Text Note", 500, "1.2 MB");
        assertEquals(1, stat.kind());
        assertEquals("Short Text Note", stat.label());
        assertEquals(500, stat.count());
        assertEquals("1.2 MB", stat.dataSize());
    }

    // ========================================================================
    // DashboardModel
    // ========================================================================

    @Test
    void dashboardModel_construction() {
        var kindStats = List.of(new KindStat(1, "Short Text Note", 100, "500 KB"));
        var dbStats = List.of(new DashboardModel.DbStat(0, 200, "1 KB", "10 KB"));

        var model = new DashboardModel("/db", "50.0 MB", 3, 1000, "48.5 MB", kindStats, dbStats);

        assertEquals("/db", model.dbPath());
        assertEquals("50.0 MB", model.fileSize());
        assertEquals(3, model.subscriptionCount());
        assertEquals(1000, model.totalEntries());
        assertEquals("48.5 MB", model.totalDataSize());
        assertEquals(1, model.kindStats().size());
        assertEquals(1, model.dbStats().size());
    }

    @Test
    void dashboardModel_dbStat() {
        var dbStat = new DashboardModel.DbStat(5, 300, "2 KB", "50 KB");
        assertEquals(5, dbStat.index());
        assertEquals(300, dbStat.count());
        assertEquals("2 KB", dbStat.keySize());
        assertEquals("50 KB", dbStat.valueSize());
    }

    // ========================================================================
    // NoteViewModel
    // ========================================================================

    @Test
    void noteViewModel_construction() {
        var tags = List.of(List.of("p", "deadbeef"), List.of("e", "cafebabe"));
        var note = new NoteViewModel(
                "aabb", "aabb...ccdd", "1122", "1122...3344",
                "alice", 1, "Short Text Note", "Hello world", "Hello...",
                "2026-03-10 14:30:00", "2h ago", tags, "sig123"
        );

        assertEquals("aabb", note.id());
        assertEquals("aabb...ccdd", note.idShort());
        assertEquals("1122", note.pubkey());
        assertEquals("alice", note.authorName());
        assertEquals(1, note.kind());
        assertEquals("Short Text Note", note.kindLabel());
        assertEquals("Hello world", note.content());
        assertEquals("Hello...", note.contentTruncated());
        assertEquals(2, note.tags().size());
        assertEquals("sig123", note.sig());
    }

    @Test
    void noteViewModel_nullAuthorName() {
        var note = new NoteViewModel(
                "id", "id", "pk", "pk", null,
                1, "Short Text Note", "content", "content",
                "ts", "rel", List.of(), "sig"
        );
        assertNull(note.authorName());
    }

    // ========================================================================
    // ProfileViewModel
    // ========================================================================

    @Test
    void profileViewModel_construction() {
        var profile = new ProfileViewModel(
                "aabbccdd", "aabb...ccdd",
                "alice", "Alice", "Alice",
                "Nostr enthusiast", "Nostr...",
                "https://pic.url", "https://banner.url",
                "alice@example.com", "alice@getalby.com", "https://alice.dev",
                42
        );

        assertEquals("aabbccdd", profile.pubkey());
        assertEquals("alice", profile.name());
        assertEquals("Alice", profile.displayName());
        assertEquals("Alice", profile.bestName());
        assertEquals(42, profile.noteCount());
        assertEquals("alice@example.com", profile.nip05());
        assertEquals("alice@getalby.com", profile.lud16());
    }

    // ========================================================================
    // NotesPageModel
    // ========================================================================

    @Test
    void notesPageModel_firstPage() {
        var model = new NotesPageModel(
                List.of(), NotesFilter.empty(), 100, 50, 0, true, false
        );

        assertEquals(100, model.total());
        assertEquals(50, model.limit());
        assertEquals(0, model.offset());
        assertTrue(model.hasNext());
        assertFalse(model.hasPrev());
    }

    @Test
    void notesPageModel_middlePage() {
        var model = new NotesPageModel(
                List.of(), NotesFilter.empty(), 100, 50, 50, false, true
        );

        assertTrue(model.hasPrev());
        assertFalse(model.hasNext());
    }

    @Test
    void notesPageModel_emptyResults() {
        var model = new NotesPageModel(
                List.of(), NotesFilter.empty(), 0, 50, 0, false, false
        );

        assertEquals(0, model.total());
        assertTrue(model.notes().isEmpty());
        assertFalse(model.hasNext());
        assertFalse(model.hasPrev());
    }
}
