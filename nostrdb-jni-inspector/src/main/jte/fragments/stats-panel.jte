@param xyz.tcheeric.nostrdb.inspector.model.DashboardModel model

<div class="cards">
    <div class="card">
        <div class="label">File Size</div>
        <div class="value">${model.fileSize()}</div>
    </div>
    <div class="card">
        <div class="label">Total Entries</div>
        <div class="value">${model.totalEntries()}</div>
    </div>
    <div class="card">
        <div class="label">Total Data Size</div>
        <div class="value">${model.totalDataSize()}</div>
    </div>
    <div class="card">
        <div class="label">Subscriptions</div>
        <div class="value">${model.subscriptionCount()}</div>
    </div>
</div>
