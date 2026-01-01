//! JNI utility functions for nostrdb-jni
//!
//! This module provides helper functions for working with JNI,
//! including exception throwing, type conversions, and pointer handling.

use jni::objects::{JByteArray, JString};
use jni::sys::{jbyteArray, jlong};
use jni::JNIEnv;

use crate::error::{Error, Result};

/// Throw a Java exception with the given message
///
/// # Arguments
/// * `env` - The JNI environment
/// * `error` - The error to throw as an exception
pub fn throw_exception(env: &mut JNIEnv, error: &Error) {
    let class = error.exception_class();
    let message = error.to_string();

    if let Err(e) = env.throw_new(class, &message) {
        // If we can't throw the specific exception, try a generic RuntimeException
        tracing::error!("Failed to throw {}: {}. Attempting RuntimeException", class, e);
        let _ = env.throw_new("java/lang/RuntimeException", &message);
    }
}

/// Convert a Java string to a Rust String
///
/// # Arguments
/// * `env` - The JNI environment
/// * `s` - The Java string
///
/// # Returns
/// The Rust String, or an error if conversion fails
pub fn java_string_to_rust(env: &mut JNIEnv, s: &JString) -> Result<String> {
    let java_str = env.get_string(s)?;
    Ok(java_str.into())
}

/// Convert a Java byte array to a Rust Vec<u8>
///
/// # Arguments
/// * `env` - The JNI environment
/// * `arr` - The Java byte array
///
/// # Returns
/// The Rust Vec<u8>, or an error if conversion fails
pub fn java_bytes_to_rust(env: &mut JNIEnv, arr: &JByteArray) -> Result<Vec<u8>> {
    let bytes = env.convert_byte_array(arr)?;
    Ok(bytes)
}

/// Convert a Rust byte slice to a Java byte array
///
/// # Arguments
/// * `env` - The JNI environment
/// * `bytes` - The Rust byte slice
///
/// # Returns
/// The Java byte array as a raw pointer, or null on error
pub fn rust_bytes_to_java(env: &mut JNIEnv, bytes: &[u8]) -> jbyteArray {
    match env.byte_array_from_slice(bytes) {
        Ok(arr) => arr.into_raw(),
        Err(e) => {
            throw_exception(env, &Error::Jni(e));
            std::ptr::null_mut()
        }
    }
}

/// Convert a Java byte array to a 32-byte array (for event IDs and pubkeys)
///
/// # Arguments
/// * `env` - The JNI environment
/// * `arr` - The Java byte array
///
/// # Returns
/// A 32-byte array, or an error if the length is incorrect
pub fn java_bytes_to_32(env: &mut JNIEnv, arr: &JByteArray) -> Result<[u8; 32]> {
    let bytes = java_bytes_to_rust(env, arr)?;
    if bytes.len() != 32 {
        return Err(Error::InvalidIdLength(bytes.len()));
    }
    let mut result = [0u8; 32];
    result.copy_from_slice(&bytes);
    Ok(result)
}

/// Safely cast a jlong to a pointer type
///
/// # Arguments
/// * `ptr` - The jlong pointer value
/// * `name` - Name of the pointer for error messages
///
/// # Returns
/// A reference to the pointed value, or an error if null
///
/// # Safety
/// The caller must ensure the pointer is valid and properly aligned
pub unsafe fn ptr_to_ref<'a, T>(ptr: jlong, name: &'static str) -> Result<&'a T> {
    if ptr == 0 {
        return Err(Error::NullPointer(name));
    }
    Ok(&*(ptr as *const T))
}

/// Safely cast a jlong to a mutable pointer type
///
/// # Arguments
/// * `ptr` - The jlong pointer value
/// * `name` - Name of the pointer for error messages
///
/// # Returns
/// A mutable reference to the pointed value, or an error if null
///
/// # Safety
/// The caller must ensure the pointer is valid and properly aligned
#[allow(dead_code)]
pub unsafe fn ptr_to_mut<'a, T>(ptr: jlong, name: &'static str) -> Result<&'a mut T> {
    if ptr == 0 {
        return Err(Error::NullPointer(name));
    }
    Ok(&mut *(ptr as *mut T))
}

/// Box a value and return it as a jlong pointer
///
/// # Arguments
/// * `value` - The value to box
///
/// # Returns
/// A jlong representing the pointer to the boxed value
pub fn box_to_ptr<T>(value: T) -> jlong {
    Box::into_raw(Box::new(value)) as jlong
}

/// Unbox a pointer and return the owned value
///
/// # Arguments
/// * `ptr` - The jlong pointer value
/// * `name` - Name of the pointer for error messages
///
/// # Returns
/// The owned boxed value, or an error if null
///
/// # Safety
/// The caller must ensure the pointer was created by `box_to_ptr` and hasn't been freed
#[allow(dead_code)]
pub unsafe fn ptr_to_box<T>(ptr: jlong, name: &'static str) -> Result<Box<T>> {
    if ptr == 0 {
        return Err(Error::NullPointer(name));
    }
    Ok(Box::from_raw(ptr as *mut T))
}

/// Drop a boxed value by pointer
///
/// # Arguments
/// * `ptr` - The jlong pointer value
///
/// # Safety
/// The caller must ensure the pointer was created by `box_to_ptr` and hasn't been freed
pub unsafe fn drop_ptr<T>(ptr: jlong) {
    if ptr != 0 {
        let _ = Box::from_raw(ptr as *mut T);
    }
}

/// Execute a closure and handle errors by throwing Java exceptions
///
/// # Arguments
/// * `env` - The JNI environment
/// * `default` - The default value to return on error
/// * `f` - The closure to execute
///
/// # Returns
/// The result of the closure, or the default value on error
pub fn with_exception<T, F>(env: &mut JNIEnv, default: T, f: F) -> T
where
    F: FnOnce(&mut JNIEnv) -> Result<T>,
{
    match f(env) {
        Ok(value) => value,
        Err(e) => {
            throw_exception(env, &e);
            default
        }
    }
}
