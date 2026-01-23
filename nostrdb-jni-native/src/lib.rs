//! JNI bindings for nostrdb - high-performance embedded Nostr database
//!
//! This crate provides Java Native Interface (JNI) bindings for the nostrdb
//! library, enabling Java applications to leverage the high-performance
//! embedded Nostr event database.

use jni::objects::{JByteArray, JClass, JObjectArray, JString};
use jni::sys::{jbyteArray, jint, jlong, jobjectArray};
use jni::JNIEnv;
use nostrdb::{Config, Filter, Ndb, NoteKey, Transaction};
use std::sync::Arc;

mod error;
mod util;

use error::{Error, Result};
use util::{
    box_to_ptr, catch_panic, catch_panic_void, drop_ptr, java_bytes_to_32, java_bytes_to_rust,
    java_string_to_rust, rust_bytes_to_java, with_exception,
};

// ============================================================================
// Ndb Lifecycle
// ============================================================================

/// Create new Ndb instance
///
/// # Arguments
/// * `db_path` - Path to the database directory
/// * `config_ptr` - Pointer to Config, or 0 for defaults
///
/// # Returns
/// Pointer to Arc<Ndb> as jlong, or 0 on error
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_ndbOpen(
    mut env: JNIEnv,
    _class: JClass,
    db_path: JString,
    _config_ptr: jlong, // Reserved for future use
) -> jlong {
    with_exception(&mut env, 0, |env| {
        let path = java_string_to_rust(env, &db_path)?;
        let config = Config::new();
        let ndb = Ndb::new(&path, &config)?;
        Ok(box_to_ptr(Arc::new(ndb)))
    })
}

/// Destroy Ndb instance
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_ndbClose(
    _env: JNIEnv,
    _class: JClass,
    ndb_ptr: jlong,
) {
    catch_panic_void(|| unsafe {
        drop_ptr::<Arc<Ndb>>(ndb_ptr);
    });
}

// ============================================================================
// Event Ingestion
// ============================================================================

/// Process a single JSON event
///
/// # Arguments
/// * `ndb_ptr` - Pointer to the Ndb instance
/// * `json` - JSON string of the event
///
/// # Returns
/// 1 on success, 0 on failure
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_processEvent(
    mut env: JNIEnv,
    _class: JClass,
    ndb_ptr: jlong,
    json: JString,
) -> jint {
    with_exception(&mut env, 0, |env| {
        let ndb = unsafe { util::ptr_to_ref::<Arc<Ndb>>(ndb_ptr, "ndb")? };
        let json_str = java_string_to_rust(env, &json)?;
        ndb.process_event(&json_str)?;
        Ok(1)
    })
}

/// Process batch of newline-delimited JSON events
///
/// # Arguments
/// * `ndb_ptr` - Pointer to the Ndb instance
/// * `ldjson` - Newline-delimited JSON events
///
/// # Returns
/// Number of events processed, or -1 on error
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_processEvents(
    mut env: JNIEnv,
    _class: JClass,
    ndb_ptr: jlong,
    ldjson: JString,
) -> jint {
    with_exception(&mut env, -1, |env| {
        let ndb = unsafe { util::ptr_to_ref::<Arc<Ndb>>(ndb_ptr, "ndb")? };
        let json_str = java_string_to_rust(env, &ldjson)?;
        let mut count = 0;
        for line in json_str.lines() {
            if !line.trim().is_empty() {
                if ndb.process_event(line).is_ok() {
                    count += 1;
                }
            }
        }
        Ok(count)
    })
}

// ============================================================================
// Transaction Management
// ============================================================================

/// Begin read transaction
///
/// IMPORTANT: Only one transaction per thread!
///
/// # Arguments
/// * `ndb_ptr` - Pointer to the Ndb instance
///
/// # Returns
/// Pointer to Transaction as jlong, or 0 on error
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_beginTransaction(
    mut env: JNIEnv,
    _class: JClass,
    ndb_ptr: jlong,
) -> jlong {
    with_exception(&mut env, 0, |_env| {
        let ndb = unsafe { util::ptr_to_ref::<Arc<Ndb>>(ndb_ptr, "ndb")? };
        let txn = Transaction::new(ndb)?;
        Ok(box_to_ptr(txn))
    })
}

/// End transaction
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_endTransaction(
    _env: JNIEnv,
    _class: JClass,
    txn_ptr: jlong,
) {
    catch_panic_void(|| unsafe {
        drop_ptr::<Transaction>(txn_ptr);
    });
}

// ============================================================================
// Note Retrieval
// ============================================================================

/// Get note by 32-byte event ID
///
/// # Arguments
/// * `ndb_ptr` - Pointer to the Ndb instance
/// * `txn_ptr` - Pointer to the Transaction
/// * `event_id` - 32-byte event ID
///
/// # Returns
/// Serialized note as byte array (JSON), or null if not found
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_getNoteById(
    mut env: JNIEnv,
    _class: JClass,
    ndb_ptr: jlong,
    txn_ptr: jlong,
    event_id: JByteArray,
) -> jbyteArray {
    with_exception(&mut env, std::ptr::null_mut(), |env| {
        let ndb = unsafe { util::ptr_to_ref::<Arc<Ndb>>(ndb_ptr, "ndb")? };
        let txn = unsafe { util::ptr_to_ref::<Transaction>(txn_ptr, "transaction")? };
        let id = java_bytes_to_32(env, &event_id)?;

        match ndb.get_note_by_id(txn, &id) {
            Ok(note) => {
                let json = serialize_note(&note)?;
                Ok(rust_bytes_to_java(env, &json))
            }
            Err(nostrdb::Error::NotFound) => Ok(std::ptr::null_mut()),
            Err(e) => Err(e.into()),
        }
    })
}

/// Get note by internal key (faster for repeated lookups)
///
/// # Arguments
/// * `ndb_ptr` - Pointer to the Ndb instance
/// * `txn_ptr` - Pointer to the Transaction
/// * `note_key` - Internal note key
///
/// # Returns
/// Serialized note as byte array (JSON), or null if not found
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_getNoteByKey(
    mut env: JNIEnv,
    _class: JClass,
    ndb_ptr: jlong,
    txn_ptr: jlong,
    note_key: jlong,
) -> jbyteArray {
    with_exception(&mut env, std::ptr::null_mut(), |env| {
        let ndb = unsafe { util::ptr_to_ref::<Arc<Ndb>>(ndb_ptr, "ndb")? };
        let txn = unsafe { util::ptr_to_ref::<Transaction>(txn_ptr, "transaction")? };
        let key = NoteKey::new(note_key as u64);

        match ndb.get_note_by_key(txn, key) {
            Ok(note) => {
                let json = serialize_note(&note)?;
                Ok(rust_bytes_to_java(env, &json))
            }
            Err(nostrdb::Error::NotFound) => Ok(std::ptr::null_mut()),
            Err(e) => Err(e.into()),
        }
    })
}

// ============================================================================
// Query Execution
// ============================================================================

/// Execute query with filter
///
/// # Arguments
/// * `ndb_ptr` - Pointer to the Ndb instance
/// * `txn_ptr` - Pointer to the Transaction
/// * `filter_ptr` - Pointer to the Filter
/// * `limit` - Maximum number of results
///
/// # Returns
/// Serialized results: [count:4][key1:8][key2:8]...
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_query(
    mut env: JNIEnv,
    _class: JClass,
    ndb_ptr: jlong,
    txn_ptr: jlong,
    filter_ptr: jlong,
    limit: jint,
) -> jbyteArray {
    with_exception(&mut env, std::ptr::null_mut(), |env| {
        let ndb = unsafe { util::ptr_to_ref::<Arc<Ndb>>(ndb_ptr, "ndb")? };
        let txn = unsafe { util::ptr_to_ref::<Transaction>(txn_ptr, "transaction")? };
        let filter = unsafe { util::ptr_to_ref::<Filter>(filter_ptr, "filter")? };

        let results = ndb.query(txn, &[filter.clone()], limit)?;

        // Serialize results: [count:4][key1:8][key2:8]...
        let mut buf = Vec::with_capacity(4 + results.len() * 8);
        buf.extend_from_slice(&(results.len() as u32).to_le_bytes());
        for result in results {
            buf.extend_from_slice(&result.note_key.as_u64().to_le_bytes());
        }

        Ok(rust_bytes_to_java(env, &buf))
    })
}

// ============================================================================
// Filter Building
// ============================================================================

/// Create new filter builder
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_filterNew(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    catch_panic(0, || {
        let filter = Filter::new();
        box_to_ptr(filter)
    })
}

/// Add kinds to filter
///
/// # Arguments
/// * `filter_ptr` - Pointer to the FilterBuilder
/// * `kinds` - Serialized kinds: [kind1:4][kind2:4]...
///
/// # Returns
/// New filter pointer (old one is consumed)
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_filterKinds(
    mut env: JNIEnv,
    _class: JClass,
    filter_ptr: jlong,
    kinds: JByteArray,
) -> jlong {
    with_exception(&mut env, filter_ptr, |env| {
        let filter = unsafe { Box::from_raw(filter_ptr as *mut nostrdb::FilterBuilder) };
        let bytes = java_bytes_to_rust(env, &kinds)?;

        let kinds: Vec<u64> = bytes
            .chunks_exact(4)
            .map(|chunk| {
                let arr: [u8; 4] = chunk.try_into().unwrap();
                u32::from_le_bytes(arr) as u64
            })
            .collect();

        let new_filter = filter.kinds(kinds);
        Ok(box_to_ptr(new_filter))
    })
}

/// Add authors to filter (array of 32-byte pubkeys)
///
/// # Arguments
/// * `filter_ptr` - Pointer to the FilterBuilder
/// * `authors` - Serialized authors: [pubkey1:32][pubkey2:32]...
///
/// # Returns
/// New filter pointer (old one is consumed)
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_filterAuthors(
    mut env: JNIEnv,
    _class: JClass,
    filter_ptr: jlong,
    authors: JByteArray,
) -> jlong {
    with_exception(&mut env, filter_ptr, |env| {
        let filter = unsafe { Box::from_raw(filter_ptr as *mut nostrdb::FilterBuilder) };
        let bytes = java_bytes_to_rust(env, &authors)?;

        let authors: Vec<[u8; 32]> = bytes
            .chunks_exact(32)
            .map(|chunk| {
                let mut arr = [0u8; 32];
                arr.copy_from_slice(chunk);
                arr
            })
            .collect();

        let author_refs: Vec<&[u8; 32]> = authors.iter().collect();
        let new_filter = filter.authors(author_refs);
        Ok(box_to_ptr(new_filter))
    })
}

/// Add tag filter
///
/// # Arguments
/// * `filter_ptr` - Pointer to the FilterBuilder
/// * `tag_name` - Single-character tag name (e.g., "d", "p", "e")
/// * `tag_values` - Array of tag values
///
/// # Returns
/// New filter pointer (old one is consumed)
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_filterTag(
    mut env: JNIEnv,
    _class: JClass,
    filter_ptr: jlong,
    tag_name: JString,
    tag_values: jobjectArray,
) -> jlong {
    with_exception(&mut env, filter_ptr, |env| {
        let filter = unsafe { Box::from_raw(filter_ptr as *mut nostrdb::FilterBuilder) };
        let tag = java_string_to_rust(env, &tag_name)?;
        let tag_char = tag.chars().next().ok_or(Error::Filter("Empty tag name".to_string()))?;

        // Get array length - use JObjectArray for proper type
        let arr_obj = unsafe { JObjectArray::from_raw(tag_values) };
        let len = env.get_array_length(&arr_obj)?;

        let mut values: Vec<String> = Vec::with_capacity(len as usize);
        for i in 0..len {
            let obj = env.get_object_array_element(&arr_obj, i)?;
            let s = java_string_to_rust(env, &JString::from(obj))?;
            values.push(s);
        }

        let value_refs: Vec<&str> = values.iter().map(|s| s.as_str()).collect();
        let new_filter = filter.tags(value_refs, tag_char);
        Ok(box_to_ptr(new_filter))
    })
}

/// Set since timestamp
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_filterSince(
    _env: JNIEnv,
    _class: JClass,
    filter_ptr: jlong,
    since: jlong,
) -> jlong {
    catch_panic(filter_ptr, || {
        let filter = unsafe { Box::from_raw(filter_ptr as *mut nostrdb::FilterBuilder) };
        let new_filter = filter.since(since as u64);
        box_to_ptr(new_filter)
    })
}

/// Set until timestamp
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_filterUntil(
    _env: JNIEnv,
    _class: JClass,
    filter_ptr: jlong,
    until: jlong,
) -> jlong {
    catch_panic(filter_ptr, || {
        let filter = unsafe { Box::from_raw(filter_ptr as *mut nostrdb::FilterBuilder) };
        let new_filter = filter.until(until as u64);
        box_to_ptr(new_filter)
    })
}

/// Set limit
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_filterLimit(
    _env: JNIEnv,
    _class: JClass,
    filter_ptr: jlong,
    limit: jlong,
) -> jlong {
    catch_panic(filter_ptr, || {
        let filter = unsafe { Box::from_raw(filter_ptr as *mut nostrdb::FilterBuilder) };
        let new_filter = filter.limit(limit as u64);
        box_to_ptr(new_filter)
    })
}

/// Full-text search
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_filterSearch(
    mut env: JNIEnv,
    _class: JClass,
    filter_ptr: jlong,
    search: JString,
) -> jlong {
    with_exception(&mut env, filter_ptr, |env| {
        let filter = unsafe { Box::from_raw(filter_ptr as *mut nostrdb::FilterBuilder) };
        let search_str = java_string_to_rust(env, &search)?;
        let new_filter = filter.search(&search_str);
        Ok(box_to_ptr(new_filter))
    })
}

/// Build filter (finalize)
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_filterBuild(
    _env: JNIEnv,
    _class: JClass,
    filter_ptr: jlong,
) -> jlong {
    catch_panic(0, || {
        let mut filter = unsafe { Box::from_raw(filter_ptr as *mut nostrdb::FilterBuilder) };
        let built = filter.build();
        box_to_ptr(built)
    })
}

/// Destroy filter
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_filterDestroy(
    _env: JNIEnv,
    _class: JClass,
    filter_ptr: jlong,
) {
    catch_panic_void(|| unsafe {
        drop_ptr::<Filter>(filter_ptr);
    });
}

// ============================================================================
// Profile Operations
// ============================================================================

/// Get profile by 32-byte pubkey
///
/// # Arguments
/// * `ndb_ptr` - Pointer to the Ndb instance
/// * `txn_ptr` - Pointer to the Transaction
/// * `pubkey` - 32-byte public key
///
/// # Returns
/// Serialized profile as byte array (JSON), or null if not found
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_getProfileByPubkey(
    mut env: JNIEnv,
    _class: JClass,
    ndb_ptr: jlong,
    txn_ptr: jlong,
    pubkey: JByteArray,
) -> jbyteArray {
    with_exception(&mut env, std::ptr::null_mut(), |env| {
        let ndb = unsafe { util::ptr_to_ref::<Arc<Ndb>>(ndb_ptr, "ndb")? };
        let txn = unsafe { util::ptr_to_ref::<Transaction>(txn_ptr, "transaction")? };
        let pk = java_bytes_to_32(env, &pubkey)?;

        match ndb.get_profile_by_pubkey(txn, &pk) {
            Ok(profile) => {
                let json = serialize_profile(&profile)?;
                Ok(rust_bytes_to_java(env, &json))
            }
            Err(nostrdb::Error::NotFound) => Ok(std::ptr::null_mut()),
            Err(e) => Err(e.into()),
        }
    })
}

/// Search profiles by name
///
/// # Arguments
/// * `ndb_ptr` - Pointer to the Ndb instance
/// * `txn_ptr` - Pointer to the Transaction
/// * `query` - Search query string
/// * `limit` - Maximum number of results
///
/// # Returns
/// Array of 32-byte pubkeys
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_searchProfiles(
    mut env: JNIEnv,
    _class: JClass,
    ndb_ptr: jlong,
    txn_ptr: jlong,
    query: JString,
    limit: jint,
) -> jbyteArray {
    with_exception(&mut env, std::ptr::null_mut(), |env| {
        let ndb = unsafe { util::ptr_to_ref::<Arc<Ndb>>(ndb_ptr, "ndb")? };
        let txn = unsafe { util::ptr_to_ref::<Transaction>(txn_ptr, "transaction")? };
        let search_str = java_string_to_rust(env, &query)?;

        let results = ndb.search_profile(txn, &search_str, limit as u32)?;

        // Serialize as concatenated pubkeys
        let mut buf = Vec::with_capacity(4 + results.len() * 32);
        buf.extend_from_slice(&(results.len() as u32).to_le_bytes());
        for pubkey in results {
            buf.extend_from_slice(pubkey);
        }

        Ok(rust_bytes_to_java(env, &buf))
    })
}

// ============================================================================
// Subscription (for future async support)
// ============================================================================

/// Subscribe to events matching filter
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_subscribe(
    mut env: JNIEnv,
    _class: JClass,
    ndb_ptr: jlong,
    filter_ptr: jlong,
) -> jlong {
    with_exception(&mut env, 0, |_env| {
        let ndb = unsafe { util::ptr_to_ref::<Arc<Ndb>>(ndb_ptr, "ndb")? };
        let filter = unsafe { util::ptr_to_ref::<Filter>(filter_ptr, "filter")? };

        let sub = ndb.subscribe(&[filter.clone()])?;
        Ok(sub.id() as jlong)
    })
}

/// Poll for new notes on subscription
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_pollForNotes(
    mut env: JNIEnv,
    _class: JClass,
    ndb_ptr: jlong,
    sub_id: jlong,
    max_notes: jint,
) -> jbyteArray {
    with_exception(&mut env, std::ptr::null_mut(), |env| {
        let ndb = unsafe { util::ptr_to_ref::<Arc<Ndb>>(ndb_ptr, "ndb")? };
        let sub = nostrdb::Subscription::new(sub_id as u64);

        let note_keys = ndb.poll_for_notes(sub, max_notes as u32);

        // Serialize as [count:4][key1:8][key2:8]...
        let mut buf = Vec::with_capacity(4 + note_keys.len() * 8);
        buf.extend_from_slice(&(note_keys.len() as u32).to_le_bytes());
        for key in note_keys {
            buf.extend_from_slice(&key.as_u64().to_le_bytes());
        }

        Ok(rust_bytes_to_java(env, &buf))
    })
}

/// Unsubscribe from a subscription
#[no_mangle]
pub extern "system" fn Java_xyz_tcheeric_nostrdb_NostrdbNative_unsubscribe(
    mut env: JNIEnv,
    _class: JClass,
    ndb_ptr: jlong,
    sub_id: jlong,
) {
    let _ = with_exception(&mut env, (), |_env| {
        let ndb = unsafe { util::ptr_to_mut::<Arc<Ndb>>(ndb_ptr, "ndb")? };
        let sub = nostrdb::Subscription::new(sub_id as u64);
        // Note: unsubscribe requires &mut self
        let ndb_mut = Arc::get_mut(ndb).ok_or(Error::InvalidState(
            "Cannot unsubscribe: Ndb has multiple references".to_string(),
        ))?;
        ndb_mut.unsubscribe(sub)?;
        Ok(())
    });
}

// ============================================================================
// Helper Functions
// ============================================================================

/// Serialize a Note to JSON bytes
fn serialize_note(note: &nostrdb::Note) -> Result<Vec<u8>> {
    let tags: Vec<Vec<String>> = note
        .tags()
        .iter()
        .map(|tag| {
            let mut tag_vec = Vec::new();
            for i in 0..tag.count() {
                if let Some(s) = tag.get_str(i as u16) {
                    tag_vec.push(s.to_string());
                }
            }
            tag_vec
        })
        .collect();

    let json = serde_json::json!({
        "id": hex::encode(note.id()),
        "pubkey": hex::encode(note.pubkey()),
        "kind": note.kind(),
        "created_at": note.created_at(),
        "content": note.content(),
        "sig": hex::encode(note.sig()),
        "tags": tags,
    });

    Ok(serde_json::to_vec(&json)?)
}

/// Serialize a ProfileRecord to JSON bytes
fn serialize_profile(profile_record: &nostrdb::ProfileRecord) -> Result<Vec<u8>> {
    let record = profile_record.record();

    // Get the profile from the record
    let profile = record.profile();

    let json = if let Some(p) = profile {
        serde_json::json!({
            "name": p.name(),
            "display_name": p.display_name(),
            "about": p.about(),
            "picture": p.picture(),
            "banner": p.banner(),
            "website": p.website(),
            "lud06": p.lud06(),
            "lud16": p.lud16(),
            "nip05": p.nip05(),
        })
    } else {
        serde_json::json!({})
    };

    Ok(serde_json::to_vec(&json)?)
}
