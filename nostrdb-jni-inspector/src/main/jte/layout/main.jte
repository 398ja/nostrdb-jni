@param String activePage = ""
@param gg.jte.Content content

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>nostrdb Inspector</title>
    <link rel="stylesheet" href="/static/style.css">
    <script src="/static/htmx.min.js"></script>
</head>
<body>
    <nav>
        <a href="/" class="brand">nostrdb Inspector</a>
        <a href="/" class="${"dashboard".equals(activePage) ? "active" : ""}">Dashboard</a>
        <a href="/notes" class="${"notes".equals(activePage) ? "active" : ""}">Notes</a>
        <a href="/profiles" class="${"profiles".equals(activePage) ? "active" : ""}">Profiles</a>
        <a href="/ingest" class="${"ingest".equals(activePage) ? "active" : ""}">Ingest</a>
        <a href="/management" class="${"management".equals(activePage) ? "active" : ""}">Management</a>
    </nav>

    <main>
        ${content}
    </main>
</body>
</html>
