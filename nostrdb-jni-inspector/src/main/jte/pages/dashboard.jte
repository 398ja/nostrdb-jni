@param xyz.tcheeric.nostrdb.inspector.model.DashboardModel model

@template.layout.main(activePage = "dashboard", content = @`
    <h1>Dashboard</h1>

    <div id="stats-panel" hx-get="/fragments/stats-panel" hx-trigger="every 5s" hx-swap="innerHTML">
        @template.fragments.stats-panel(model = model)
    </div>

    <h2>Kind Breakdown</h2>
    <table>
        <thead>
            <tr>
                <th>Kind</th>
                <th>Label</th>
                <th>Count</th>
                <th>Data Size</th>
            </tr>
        </thead>
        <tbody id="kind-table">
            @for(var kind : model.kindStats())
                <tr>
                    <td>${kind.kind()}</td>
                    <td>${kind.label()}</td>
                    <td>${kind.count()}</td>
                    <td>${kind.dataSize()}</td>
                </tr>
            @endfor
        </tbody>
    </table>

    <h2>Sub-Databases</h2>
    <table>
        <thead>
            <tr>
                <th>Index</th>
                <th>Count</th>
                <th>Key Size</th>
                <th>Value Size</th>
            </tr>
        </thead>
        <tbody>
            @for(var db : model.dbStats())
                <tr>
                    <td>${db.index()}</td>
                    <td>${db.count()}</td>
                    <td>${db.keySize()}</td>
                    <td>${db.valueSize()}</td>
                </tr>
            @endfor
        </tbody>
    </table>
`)
