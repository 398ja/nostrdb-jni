@param boolean readOnly

@template.layout.main(activePage = "ingest", content = @`
    <h1>Ingest Events</h1>

    @if(readOnly)
        <div class="alert alert-warning">
            Ingest is disabled in read-only mode.
        </div>
    @else
        <p class="text-secondary mb-2">
            Paste Nostr events as JSON to ingest them into the database.
            Accepts an object with an "events" array, or line-delimited relay messages.
        </p>

        <div class="form-group">
            <label for="events-input">Events JSON</label>
            <textarea id="events-input" name="events" placeholder='{"events": [{"id":"...", "pubkey":"...", ...}]}'></textarea>
        </div>

        <button class="btn btn-primary"
                hx-post="/api/ingest"
                hx-include="#events-input"
                hx-target="#ingest-result"
                hx-headers='{"Content-Type": "application/json"}'
                hx-vals='js:{}'
                onclick="this.setAttribute('hx-vals', document.getElementById('events-input').value)">
            Ingest Events
        </button>

        <div id="ingest-result" class="mt-2"></div>
    @endif
`)
