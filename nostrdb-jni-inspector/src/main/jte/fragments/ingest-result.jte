@param java.util.Map<String, Object> result

<div class="alert ${(int) result.get("errors") > 0 ? "alert-warning" : "alert-success"}">
    <strong>Ingest Complete</strong><br>
    Processed: ${result.get("processed")} / ${result.get("total")}<br>
    @if((int) result.get("errors") > 0)
        Errors: ${result.get("errors")}
    @endif
</div>
