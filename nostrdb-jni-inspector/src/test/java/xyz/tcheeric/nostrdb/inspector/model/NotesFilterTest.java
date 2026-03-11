package xyz.tcheeric.nostrdb.inspector.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotesFilterTest {

    @Test
    void empty_hasEmptyCollections() {
        NotesFilter filter = NotesFilter.empty();
        assertTrue(filter.kinds().isEmpty());
        assertTrue(filter.authors().isEmpty());
        assertNull(filter.since());
        assertNull(filter.until());
        assertTrue(filter.tags().isEmpty());
        assertNull(filter.search());
    }

    @Test
    void constructWithValues() {
        NotesFilter filter = new NotesFilter(
                List.of(1, 7),
                List.of("aabb"),
                1700000000L,
                1700100000L,
                List.of("p:deadbeef"),
                "hello"
        );
        assertEquals(List.of(1, 7), filter.kinds());
        assertEquals(List.of("aabb"), filter.authors());
        assertEquals(1700000000L, filter.since());
        assertEquals(1700100000L, filter.until());
        assertEquals(List.of("p:deadbeef"), filter.tags());
        assertEquals("hello", filter.search());
    }

    @Test
    void recordEquality() {
        NotesFilter a = NotesFilter.empty();
        NotesFilter b = NotesFilter.empty();
        assertEquals(a, b);
    }

    @Test
    void recordInequality() {
        NotesFilter a = NotesFilter.empty();
        NotesFilter b = new NotesFilter(List.of(1), List.of(), null, null, List.of(), null);
        assertNotEquals(a, b);
    }
}
