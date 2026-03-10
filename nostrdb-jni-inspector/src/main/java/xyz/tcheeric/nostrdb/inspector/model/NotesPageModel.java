package xyz.tcheeric.nostrdb.inspector.model;

import java.util.List;

/**
 * View model for the notes browser page.
 *
 * @param notes        List of note view models
 * @param activeFilter Current active filter state
 * @param total        Total matching results
 * @param limit        Results per page
 * @param offset       Current offset
 * @param hasNext      Whether a next page exists
 * @param hasPrev      Whether a previous page exists
 */
public record NotesPageModel(
        List<NoteViewModel> notes,
        NotesFilter activeFilter,
        int total,
        int limit,
        int offset,
        boolean hasNext,
        boolean hasPrev
) {}
