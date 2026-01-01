# Building nostrdb-jni

This document provides detailed instructions for building nostrdb-jni from source.

## Prerequisites

### Rust Toolchain

```bash
# Install Rust via rustup
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source ~/.cargo/env

# Verify installation
rustc --version
cargo --version
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
Download and install from [Adoptium](https://adoptium.net/) or use:
```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

### nostrdb-rs

The native library depends on nostrdb-rs. Clone it as a sibling directory:

```bash
cd ~/IdeaProjects
git clone https://github.com/damus-io/nostrdb nostrdb-rs
```

## Build Steps

### 1. Build Native Library (Rust)

```bash
cd ~/IdeaProjects/nostrdb-jni/nostrdb-jni-native

# Debug build (faster compilation, larger binary)
cargo build

# Release build (optimized, smaller binary)
cargo build --release
```

The native library will be created at:
- Linux: `target/release/libnostrdb_jni.so`
- macOS: `target/release/libnostrdb_jni.dylib`
- Windows: `target/release/nostrdb_jni.dll`

### 2. Build Java Library

```bash
cd ~/IdeaProjects/nostrdb-jni/nostrdb-jni-java

# Compile
mvn compile

# Run tests
mvn test

# Package JAR (includes native library)
mvn package

# Install to local Maven repository
mvn install
```

The JAR file will be created at:
`target/nostrdb-jni-0.1.0-SNAPSHOT.jar`

## Build Outputs

After a successful build:

```
nostrdb-jni/
├── nostrdb-jni-native/
│   └── target/
│       └── release/
│           └── libnostrdb_jni.so    # Native library (~1.9 MB)
└── nostrdb-jni-java/
    └── target/
        ├── nostrdb-jni-0.1.0-SNAPSHOT.jar
        └── classes/
            └── natives/
                └── libnostrdb_jni.so    # Copied for JAR inclusion
```

## Using in Your Project

### Option 1: Maven Local Repository

After running `mvn install`, add to your project's `pom.xml`:

```xml
<dependency>
    <groupId>xyz.tcheeric</groupId>
    <artifactId>nostrdb-jni</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Option 2: Direct JAR Reference

```xml
<dependency>
    <groupId>xyz.tcheeric</groupId>
    <artifactId>nostrdb-jni</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/nostrdb-jni-0.1.0-SNAPSHOT.jar</systemPath>
</dependency>
```

### Option 3: java.library.path

For development, you can set the library path directly:

```bash
java -Djava.library.path=/path/to/nostrdb-jni-native/target/release -jar myapp.jar
```

## Cross-Compilation

### Linux (on Linux)

```bash
cargo build --release
```

### macOS (on macOS)

```bash
cargo build --release
```

### Windows (on Windows or via cross-compilation)

On Windows:
```powershell
cargo build --release
```

Cross-compile from Linux:
```bash
# Install cross-compilation toolchain
rustup target add x86_64-pc-windows-gnu
sudo apt install mingw-w64

# Build
cargo build --release --target x86_64-pc-windows-gnu
```

## Troubleshooting

### "home" crate edition2024 error

If you see an error about `home v0.5.12` requiring Rust edition 2024:

```bash
# The Cargo.toml already pins home = "=0.5.11"
# If you still see this error, update your Cargo.lock:
cargo update -p home --precise 0.5.11
```

### Native library not found

```
java.lang.UnsatisfiedLinkError: no nostrdb_jni in java.library.path
```

Solutions:
1. Ensure the release build completed successfully
2. Check that the JAR includes the native library: `jar tf target/*.jar | grep natives`
3. Set java.library.path: `-Djava.library.path=/path/to/native/lib`

### LMDB errors

```
Failed to open database
```

Ensure the database directory exists and is writable:
```bash
mkdir -p ~/.nostrdb
chmod 755 ~/.nostrdb
```

### JNI signature mismatch

If you modify native functions, regenerate JNI headers:

```bash
cd nostrdb-jni-java
javac -h ../nostrdb-jni-native/src \
  src/main/java/xyz/tcheeric/nostrdb/NostrdbNative.java
```

## Development Workflow

### Making Changes to Native Code

1. Edit Rust code in `nostrdb-jni-native/src/`
2. Build: `cargo build --release`
3. Rebuild Java: `mvn clean package -DskipTests`
4. Test: `mvn test`

### Making Changes to Java Code

1. Edit Java code in `nostrdb-jni-java/src/`
2. Build and test: `mvn clean test`

### Running Individual Tests

```bash
# Run a specific test class
mvn test -Dtest=NdbIntegrationTest

# Run a specific test method
mvn test -Dtest=NdbIntegrationTest#testNoteFromJson
```

## IDE Setup

### IntelliJ IDEA

1. Open the `nostrdb-jni` directory as a project
2. Import `nostrdb-jni-java` as a Maven module
3. Set up Rust plugin for `nostrdb-jni-native` (optional)
4. Configure run configuration with:
   - VM options: `-Djava.library.path=../nostrdb-jni-native/target/release`

### VS Code

1. Install "Rust Analyzer" extension for native code
2. Install "Extension Pack for Java" for Java code
3. Open the project root directory

## Continuous Integration

Example GitHub Actions workflow:

```yaml
name: Build

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Rust
        uses: dtolnay/rust-action@stable

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Clone nostrdb-rs
        run: git clone https://github.com/damus-io/nostrdb ../nostrdb-rs

      - name: Build Native
        run: |
          cd nostrdb-jni-native
          cargo build --release

      - name: Build & Test Java
        run: |
          cd nostrdb-jni-java
          mvn clean verify
```
