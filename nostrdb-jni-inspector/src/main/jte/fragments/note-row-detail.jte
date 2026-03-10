@param xyz.tcheeric.nostrdb.inspector.model.NoteViewModel note
@param String rawJson

<div class="row-detail">
    <div class="flex justify-between items-center mb-1">
        <strong>Note Detail</strong>
        <a href="/notes/${note.id()}" class="btn">Full Page</a>
    </div>

    <div class="field">
        <div class="field-label">Event ID</div>
        <div class="text-mono">${note.id()}</div>
    </div>

    <div class="field mt-1">
        <div class="field-label">Author</div>
        <div>
            @if(note.authorName() != null)
                ${note.authorName()} —
            @endif
            <a href="/profiles/${note.pubkey()}" class="text-mono">${note.pubkey()}</a>
        </div>
    </div>

    <div class="field mt-1">
        <div class="field-label">Kind</div>
        <div>${note.kind()} — ${note.kindLabel()}</div>
    </div>

    <div class="field mt-1">
        <div class="field-label">Created</div>
        <div>${note.createdAt()} (${note.createdAtRelative()})</div>
    </div>

    <div class="field mt-1">
        <div class="field-label">Content</div>
        <pre>${note.content()}</pre>
    </div>

    @if(!note.tags().isEmpty())
        <div class="field mt-1">
            <div class="field-label">Tags</div>
            <table>
                <tbody>
                    @for(var tag : note.tags())
                        <tr>
                            <td class="text-mono">${tag.isEmpty() ? "" : tag.get(0)}</td>
                            <td class="text-mono">
                                @for(int i = 1; i < tag.size(); i++)
                                    ${tag.get(i)}@if(i < tag.size() - 1), @endif
                                @endfor
                            </td>
                        </tr>
                    @endfor
                </tbody>
            </table>
        </div>
    @endif

    <details class="mt-1">
        <summary>Raw JSON</summary>
        <pre>${rawJson}</pre>
    </details>
</div>
