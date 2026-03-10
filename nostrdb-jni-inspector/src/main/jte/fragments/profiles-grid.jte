@param java.util.List<xyz.tcheeric.nostrdb.inspector.model.ProfileViewModel> profiles

@if(profiles.isEmpty())
    <p class="text-secondary">No profiles found. Try searching by name.</p>
@else
    <div class="profiles-grid">
        @for(var profile : profiles)
            <a href="/profiles/${profile.pubkey()}" class="profile-card">
                @if(profile.picture() != null)
                    <img src="${profile.picture()}" alt="Avatar" class="avatar"
                         onerror="this.style.display='none'">
                @endif
                <div class="name">${profile.bestName() != null ? profile.bestName() : "Unknown"}</div>
                @if(profile.nip05() != null)
                    <div class="nip05">${profile.nip05()}</div>
                @endif
                @if(profile.aboutTruncated() != null)
                    <div class="about">${profile.aboutTruncated()}</div>
                @endif
                <div class="mt-1 text-secondary" style="font-size: 0.75rem;">
                    ${profile.noteCount()} notes
                </div>
            </a>
        @endfor
    </div>
@endif
