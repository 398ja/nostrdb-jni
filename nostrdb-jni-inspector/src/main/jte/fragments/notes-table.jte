@param java.util.List<xyz.tcheeric.nostrdb.inspector.model.NoteViewModel> notes
@param int total
@param int limit
@param int offset
@param boolean hasNext
@param boolean hasPrev

<table>
    <thead>
        <tr>
            <th>ID</th>
            <th>Kind</th>
            <th>Author</th>
            <th>Content</th>
            <th>Created</th>
        </tr>
    </thead>
    <tbody>
        @if(notes.isEmpty())
            <tr><td colspan="5" class="text-secondary">No notes found.</td></tr>
        @else
            @for(var note : notes)
                <tr style="cursor: pointer;"
                    hx-get="/fragments/note-row-detail?id=${note.id()}"
                    hx-target="next .row-detail-container"
                    hx-swap="innerHTML">
                    <td><a href="/notes/${note.id()}" class="text-mono">${note.idShort()}</a></td>
                    <td>${note.kindLabel()}</td>
                    <td>
                        @if(note.authorName() != null)
                            ${note.authorName()}
                        @else
                            <span class="text-mono">${note.pubkeyShort()}</span>
                        @endif
                    </td>
                    <td>${note.contentTruncated()}</td>
                    <td class="text-secondary">${note.createdAtRelative()}</td>
                </tr>
                <tr><td colspan="5" class="row-detail-container" style="padding: 0; border: none;"></td></tr>
            @endfor
        @endif
    </tbody>
</table>

<div class="pagination">
    @if(hasPrev)
        <button class="btn" hx-get="/fragments/notes-table?offset=${offset - limit}&limit=${limit}"
                hx-target="#notes-results" hx-swap="innerHTML">Previous</button>
    @endif
    <span class="info">Showing ${offset + 1}-${Math.min(offset + notes.size(), total)} of ${total}</span>
    @if(hasNext)
        <button class="btn" hx-get="/fragments/notes-table?offset=${offset + limit}&limit=${limit}"
                hx-target="#notes-results" hx-swap="innerHTML">Next</button>
    @endif
</div>
