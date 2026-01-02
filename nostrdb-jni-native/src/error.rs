//! Error handling for nostrdb-jni
//!
//! This module provides error types for JNI operations and converts
//! between Rust errors and Java exceptions.

use thiserror::Error;

/// Result type alias for nostrdb-jni operations
pub type Result<T> = std::result::Result<T, Error>;

/// Errors that can occur in nostrdb-jni operations
#[derive(Error, Debug)]
pub enum Error {
    /// JNI operation failed
    #[error("JNI error: {0}")]
    Jni(#[from] jni::errors::Error),

    /// nostrdb operation failed
    #[error("Nostrdb error: {0}")]
    Nostrdb(#[from] nostrdb::Error),

    /// Invalid byte array length (expected 32 bytes for IDs/pubkeys)
    #[error("Invalid length: expected 32 bytes, got {0}")]
    InvalidIdLength(usize),

    /// Null pointer encountered
    #[error("Null pointer: {0}")]
    NullPointer(&'static str),

    /// Invalid UTF-8 string
    #[error("Invalid UTF-8 string: {0}")]
    InvalidUtf8(#[from] std::str::Utf8Error),

    /// JSON serialization/deserialization failed
    #[error("JSON error: {0}")]
    Json(#[from] serde_json::Error),

    /// Filter building failed
    #[error("Filter error: {0}")]
    Filter(String),

    /// Invalid state
    #[error("Invalid state: {0}")]
    InvalidState(String),
}

impl Error {
    /// Get the Java exception class name for this error
    pub fn exception_class(&self) -> &'static str {
        match self {
            Error::Jni(_) => "java/lang/RuntimeException",
            Error::Nostrdb(e) => match e {
                nostrdb::Error::NotFound => "java/util/NoSuchElementException",
                nostrdb::Error::DbOpenFailed => "java/io/IOException",
                _ => "xyz/tcheeric/nostrdb/NostrdbException",
            },
            Error::InvalidIdLength(_) => "java/lang/IllegalArgumentException",
            Error::NullPointer(_) => "java/lang/NullPointerException",
            Error::InvalidUtf8(_) => "java/lang/IllegalArgumentException",
            Error::Json(_) => "xyz/tcheeric/nostrdb/NostrdbException",
            Error::Filter(_) => "xyz/tcheeric/nostrdb/NostrdbException",
            Error::InvalidState(_) => "java/lang/IllegalStateException",
        }
    }
}
