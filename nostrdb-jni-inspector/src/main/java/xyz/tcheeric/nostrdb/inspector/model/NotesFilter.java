package xyz.tcheeric.nostrdb.inspector.model;

import java.util.List;

/**
 * Active filter state for re-populating the notes filter form.
 *
 * @param kinds   Selected kind numbers
 * @param authors Selected author pubkeys (hex)
 * @param since   Since timestamp (null if not set)
 * @param until   Until timestamp (null if not set)
 * @param tags    Tag filters as "name:value" strings
 * @param search  Full-text search query
 */
public record NotesFilter(
        List<Integer> kinds,
        List<String> authors,
        Long since,
        Long until,
        List<String> tags,
        String search
) {
    public static NotesFilter empty() {
        return new NotesFilter(List.of(), List.of(), null, null, List.of(), null);
    }
}
