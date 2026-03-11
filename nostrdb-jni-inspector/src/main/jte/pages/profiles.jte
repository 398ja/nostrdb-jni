@param java.util.List<xyz.tcheeric.nostrdb.inspector.model.ProfileViewModel> profiles
@param String query

@template.layout.main(activePage = "profiles", content = @`
    <h1>Profiles</h1>

    <div class="form-group">
        <label for="profile-search">Search profiles by name</label>
        <input type="text" id="profile-search" name="q" placeholder="Search profiles..."
               value="${query}"
               hx-get="/fragments/profiles-grid" hx-target="#profiles-results"
               hx-trigger="keyup changed delay:300ms" hx-push-url="true">
    </div>

    <span class="htmx-indicator">Searching...</span>

    <div id="profiles-results">
        @template.fragments.profiles-grid(profiles = profiles)
    </div>
`)
