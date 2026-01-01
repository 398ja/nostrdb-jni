# How to Build from Source

This guide explains how to build nostrdb-jni from source code.

## Prerequisites

### Rust toolchain

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source ~/.cargo/env
```

### Java 21+

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install openjdk-21-jdk maven
```

**macOS:**
```bash
brew install openjdk@21 maven
```

**Windows:**
```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

### nostrdb-rs dependency

Clone nostrdb-rs as a sibling directory:

```bash
cd ~/IdeaProjects
git clone https://github.com/damus-io/nostrdb nostrdb-rs
```

## Build the native library

```bash
cd nostrdb-jni/nostrdb-jni-native
cargo build --release
```

Output locations:
- Linux: `target/release/libnostrdb_jni.so`
- macOS: `target/release/libnostrdb_jni.dylib`
- Windows: `target/release/nostrdb_jni.dll`

## Build the Java library

```bash
cd nostrdb-jni/nostrdb-jni-java
mvn clean package
```

## Install to local Maven repository

```bash
mvn install
```

The library is now available for other projects:

```xml
<dependency>
    <groupId>xyz.tcheeric</groupId>
    <artifactId>nostrdb-jni</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Troubleshooting

### "home" crate edition2024 error

```bash
cargo update -p home --precise 0.5.11
```

### Native library not found

Verify the JAR includes the native library:
```bash
jar tf target/*.jar | grep natives
```

Set java.library.path if needed:
```bash
java -Djava.library.path=/path/to/native/lib -jar myapp.jar
```
