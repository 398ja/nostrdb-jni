// Prints signed notes, one per line, in nostrdb's own JSON. nostrdb computes the id over
// NIP-01 serialisation with only " \ \b \f \n \r \t escaped, so these ids are what any
// NIP-01 relay would accept and what nostrdb verifies on ingest.
// Note: an UPPERCASE 64-hex tag value is not used. nostrdb packs it as a binary id and
// writes it back lowercase, so its id check fails and nostrdb never stores such a note.
// Nor is content equal to the bare word "tags", or a two-byte escaped string such as "\""
// or "\n" as a whole tag value: nostrdb 6956b9f refuses those notes at ingest too, so a
// cache can never hold them.
use nostrdb::NoteBuilder;
fn main() {
    let sec = [7u8; 32];
    let mut ctrl = String::new();
    for c in 1u8..0x20 { ctrl.push(c as char); }
    let cases: Vec<(&str, String, Vec<Vec<String>>)> = vec![
        ("control", format!("ctl:{}", ctrl), vec![]),
        ("del", "del:\u{7f}end".into(), vec![]),
        ("unicode", "uni:caf\u{e9} \u{2028} \u{2029} \u{feff} \u{4e2d}".into(), vec![]),
        ("emoji", "emoji:\u{1F600}\u{1F469}\u{200D}\u{1F4BB}\u{1F3F3}\u{FE0F}".into(), vec![]),
        ("quotes", "q:\"quoted\" back\\slash \\\" \\\\ \\u0041 / \\/".into(), vec![]),
        ("tags", "odd tags".into(), vec![
            vec!["t".into(), format!("ctl{}x", ctrl)],
            vec!["x".into(), "".into(), "\"\\".into(), "\u{7f}\u{2028}\u{1F600}".into()],
            vec!["e".into(), "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789".into()],
            vec!["single".into()],
        ]),
    ];
    for (i, (_name, content, tags)) in cases.iter().enumerate() {
        let mut b = NoteBuilder::new().kind(1).content(content).created_at(1_700_000_000 + i as u64);
        for t in tags {
            b = b.start_tag();
            for s in t { b = b.tag_str(s); }
        }
        let note = b.sign(&sec).build().expect("build");
        println!("{}", note.json().expect("json"));
    }
}
