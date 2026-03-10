@param boolean readOnly
@param String dbPath
@param String fileSize
@param long totalEntries

@template.layout.main(activePage = "management", content = @`
    <h1>Database Management</h1>

    <div class="card mb-2">
        <h3>Database Info</h3>
        <div class="mt-1">
            <div><strong>Path:</strong> <span class="text-mono">${dbPath}</span></div>
            <div><strong>File Size:</strong> ${fileSize}</div>
            <div><strong>Total Entries:</strong> ${totalEntries}</div>
        </div>
    </div>

    @if(readOnly)
        <div class="alert alert-warning">
            Database management is disabled in read-only mode.
        </div>
    @else
        <div class="card">
            <h3>Flush Database</h3>
            <p class="text-secondary mt-1 mb-1">
                This will delete all data in the database and create a fresh empty database.
                This action cannot be undone.
            </p>

            <div class="form-group">
                <label for="flush-confirm">Type FLUSH to confirm</label>
                <input type="text" id="flush-confirm" placeholder="Type FLUSH to enable the button"
                       oninput="document.getElementById('flush-btn').disabled = this.value !== 'FLUSH'">
            </div>

            <button id="flush-btn" class="btn btn-danger" disabled
                    hx-post="/api/flush"
                    hx-target="#flush-result"
                    hx-vals='{"confirm": true}'
                    hx-confirm="Are you sure you want to flush the entire database?">
                Flush Database
            </button>

            <div id="flush-result" class="mt-2"></div>
        </div>
    @endif
`)
