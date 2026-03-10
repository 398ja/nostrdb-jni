@param xyz.tcheeric.nostrdb.inspector.model.NotesPageModel model

@template.layout.main(activePage = "notes", content = @`
    <h1>Notes</h1>

    <form class="filter-form" hx-get="/fragments/notes-table" hx-target="#notes-results" hx-push-url="true">
        <div class="form-group">
            <label for="kind">Kind</label>
            <input type="text" id="kind" name="kind" placeholder="e.g. 1"
                   value="${model.activeFilter().kinds().isEmpty() ? "" : model.activeFilter().kinds().get(0).toString()}">
        </div>
        <div class="form-group">
            <label for="author">Author (hex)</label>
            <input type="text" id="author" name="author" placeholder="pubkey hex"
                   value="${model.activeFilter().authors().isEmpty() ? "" : model.activeFilter().authors().get(0)}">
        </div>
        <div class="form-group">
            <label for="search">Search</label>
            <input type="text" id="search" name="search" placeholder="Full-text search"
                   value="${model.activeFilter().search() != null ? model.activeFilter().search() : ""}"
                   hx-get="/fragments/notes-table" hx-target="#notes-results" hx-trigger="keyup changed delay:300ms">
        </div>
        <button type="submit" class="btn btn-primary">Filter</button>
    </form>

    <div id="notes-results">
        @template.fragments.notes-table(notes = model.notes(), total = model.total(), limit = model.limit(), offset = model.offset(), hasNext = model.hasNext(), hasPrev = model.hasPrev())
    </div>
`)
