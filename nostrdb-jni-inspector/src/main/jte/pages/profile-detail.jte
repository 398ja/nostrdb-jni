@param xyz.tcheeric.nostrdb.inspector.model.ProfileViewModel profile
@param java.util.List<xyz.tcheeric.nostrdb.inspector.model.NoteViewModel> recentNotes

@template.layout.main(activePage = "profiles", content = @`
    <div class="flex justify-between items-center mb-2">
        <h1>Profile Detail</h1>
        <a href="/profiles" class="btn">Back to Profiles</a>
    </div>

    <div class="profile-header">
        @if(profile.banner() != null)
            <img src="${profile.banner()}" alt="Banner" class="profile-banner">
        @endif
        @if(profile.picture() != null)
            <img src="${profile.picture()}" alt="Avatar" class="profile-avatar">
        @endif
    </div>

    <div class="card mb-2">
        <h2>${profile.bestName() != null ? profile.bestName() : "Unknown"}</h2>
        @if(profile.nip05() != null)
            <div class="text-secondary">${profile.nip05()}</div>
        @endif
        <div class="text-mono text-secondary mt-1">${profile.pubkey()}</div>

        @if(profile.about() != null)
            <p class="mt-1">${profile.about()}</p>
        @endif

        <div class="mt-1">
            @if(profile.website() != null)
                <div><strong>Website:</strong> ${profile.website()}</div>
            @endif
            @if(profile.lud16() != null)
                <div><strong>Lightning:</strong> ${profile.lud16()}</div>
            @endif
            <div><strong>Notes in cache:</strong> ${profile.noteCount()}</div>
        </div>
    </div>

    <div class="flex justify-between items-center mb-1">
        <h2>Recent Notes</h2>
        <a href="/notes?author=${profile.pubkey()}" class="btn">View all notes</a>
    </div>

    @if(recentNotes.isEmpty())
        <p class="text-secondary">No notes found for this author.</p>
    @else
        <table>
            <thead>
                <tr>
                    <th>ID</th>
                    <th>Kind</th>
                    <th>Content</th>
                    <th>Created</th>
                </tr>
            </thead>
            <tbody>
                @for(var note : recentNotes)
                    <tr>
                        <td><a href="/notes/${note.id()}" class="text-mono">${note.idShort()}</a></td>
                        <td>${note.kindLabel()}</td>
                        <td>${note.contentTruncated()}</td>
                        <td class="text-secondary">${note.createdAtRelative()}</td>
                    </tr>
                @endfor
            </tbody>
        </table>
    @endif
`)
