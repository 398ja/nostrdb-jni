# Stage 1: Build native library (Rust)
FROM rust:1.82-bookworm AS native-builder

RUN apt-get update && apt-get install -y \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build/nostrdb-jni-native
COPY nostrdb-jni-native/ .
RUN cargo build --release

# Stage 2: Build Java modules (Maven)
FROM maven:3.9-eclipse-temurin-21 AS java-builder

WORKDIR /build

# Copy native library from previous stage
COPY --from=native-builder /build/nostrdb-jni-native/target/release/*.so \
    nostrdb-jni-native/target/release/

# Copy Java module sources
COPY nostrdb-jni-java/ nostrdb-jni-java/
COPY nostrdb-jni-inspector/ nostrdb-jni-inspector/

# Build and install nostrdb-jni core JAR (with native libs bundled)
WORKDIR /build/nostrdb-jni-java
RUN mvn clean install -DskipTests -q

# Build inspector fat JAR
WORKDIR /build/nostrdb-jni-inspector
RUN mvn clean package -DskipTests -q

# Stage 3: Runtime image
FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --gid 1000 nostrdb && \
    useradd --uid 1000 --gid nostrdb --create-home nostrdb

WORKDIR /app

COPY --from=java-builder /build/nostrdb-jni-inspector/target/nostrdb-jni-inspector-0.1.0.jar \
    nostrdb-inspector.jar

# Default data directory
RUN mkdir -p /data && chown nostrdb:nostrdb /data
VOLUME /data

USER nostrdb

EXPOSE 7777

ENTRYPOINT ["java", "-jar", "nostrdb-inspector.jar"]
CMD ["--db-path", "/data", "--host", "0.0.0.0"]
