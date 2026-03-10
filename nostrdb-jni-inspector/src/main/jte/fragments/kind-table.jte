@param java.util.List<xyz.tcheeric.nostrdb.inspector.model.KindStat> kindStats

@for(var kind : kindStats)
    <tr>
        <td>${kind.kind()}</td>
        <td>${kind.label()}</td>
        <td>${kind.count()}</td>
        <td>${kind.dataSize()}</td>
    </tr>
@endfor
