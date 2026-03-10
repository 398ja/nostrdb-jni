@param xyz.tcheeric.nostrdb.inspector.model.NoteViewModel note
@param String rawJson

@template.layout.main(activePage = "notes", content = @`
    <div class="flex justify-between items-center mb-2">
        <h1>Note Detail</h1>
        <a href="/notes" class="btn">Back to Notes</a>
    </div>

    <div class="note-detail">
        <div class="field">
            <div class="field-label">Event ID</div>
            <div class="field-value text-mono">${note.id()}</div>
        </div>

        <div class="field">
            <div class="field-label">Author</div>
            <div class="field-value">
                @if(note.authorName() != null)
                    <strong>${note.authorName()}</strong>
                    <br>
                @endif
                <a href="/profiles/${note.pubkey()}" class="text-mono">${note.pubkey()}</a>
            </div>
        </div>

        <div class="field">
            <div class="field-label">Kind</div>
            <div class="field-value">${note.kind()} — ${note.kindLabel()}</div>
        </div>

        <div class="field">
            <div class="field-label">Created At</div>
            <div class="field-value">${note.createdAt()} (${note.createdAtRelative()})</div>
        </div>

        <div class="field">
            <div class="field-label">Content</div>
            <div class="field-value">
                <pre>${note.content()}</pre>
            </div>
        </div>

        @if(!note.tags().isEmpty())
            <div class="field">
                <div class="field-label">Tags</div>
                <table>
                    <thead><tr><th>Tag</th><th>Values</th></tr></thead>
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

        <div class="field">
            <div class="field-label">Signature</div>
            <div class="field-value text-mono" style="word-break: break-all;">${note.sig()}</div>
        </div>

        <h2 class="mt-2">Raw JSON</h2>
        <pre>${rawJson}</pre>
    </div>
`)
