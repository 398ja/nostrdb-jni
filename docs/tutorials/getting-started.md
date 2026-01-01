# Getting Started with nostrdb-jni

This tutorial walks you through setting up nostrdb-jni and performing your first queries. By the end, you'll have a working application that can store and query Nostr events.

## What you'll learn

- Install nostrdb-jni in a Java project
- Open a database and ingest events
- Query events using filters
- Look up user profiles

## Prerequisites

- Java 21 or later
- Maven 3.6 or later
- nostrdb-jni installed in your local Maven repository (see [Building from Source](../how-to/build-from-source.md))

## Step 1: Create a new project

Create a new Maven project with the following `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>nostrdb-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
    </properties>

    <dependencies>
        <dependency>
            <groupId>xyz.tcheeric</groupId>
            <artifactId>nostrdb-jni</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
</project>
```

## Step 2: Open a database

Create a main class that opens a database:

```java
import xyz.tcheeric.nostrdb.*;
import java.nio.file.Path;

public class NostrdbDemo {
    public static void main(String[] args) {
        // Choose where to store the database
        Path dbPath = Path.of(System.getProperty("user.home"), ".nostrdb-demo");

        // Open the database (creates it if it doesn't exist)
        try (Ndb ndb = Ndb.open(dbPath)) {
            System.out.println("Database opened successfully!");

            // We'll add more code here
        }
    }
}
```

Run this to verify everything is working:

```bash
mvn compile exec:java -Dexec.mainClass="NostrdbDemo"
```

You should see "Database opened successfully!" printed.

## Step 3: Ingest an event

Nostr events are JSON objects. Let's add one to our database:

```java
try (Ndb ndb = Ndb.open(dbPath)) {
    // A sample Nostr event (kind 1 = text note)
    String eventJson = """
        {
            "id": "d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027",
            "pubkey": "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245",
            "created_at": 1700000000,
            "kind": 1,
            "tags": [
                ["p", "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"]
            ],
            "content": "Hello from nostrdb-jni!",
            "sig": "908a15e46fb4d8675bab026fc230a0e3542bfade63da02d542fb78b2a8513fcd..."
        }
        """;

    // Ingest the event
    ndb.processEvent(eventJson);
    System.out.println("Event ingested!");
}
```

> **Note**: nostrdb validates event signatures. Events with invalid signatures are silently rejected. In production, you'll ingest events received from relays which have valid signatures.

## Step 4: Query events

Now let's query the database. All reads require a transaction:

```java
try (Ndb ndb = Ndb.open(dbPath)) {

    // Begin a read transaction
    try (Transaction txn = ndb.beginTransaction()) {

        // Build a filter for kind 1 (text notes)
        try (Filter filter = Filter.builder()
                .kinds(1)
                .limit(10)
                .build()) {

            // Execute the query
            List<Note> notes = ndb.queryNotes(txn, filter, 10);

            System.out.println("Found " + notes.size() + " notes:");
            for (Note note : notes) {
                System.out.println("  - " + note.content());
            }
        }
    }
}
```

## Step 5: Look up a specific event

You can retrieve an event by its ID:

```java
try (Transaction txn = ndb.beginTransaction()) {
    String eventId = "d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027";

    Optional<Note> note = ndb.getNoteById(txn, eventId);

    note.ifPresentOrElse(
        n -> {
            System.out.println("Found event!");
            System.out.println("  Author: " + n.pubkey());
            System.out.println("  Content: " + n.content());
            System.out.println("  Created: " + n.createdAt());
        },
        () -> System.out.println("Event not found")
    );
}
```

## Step 6: Work with tags

Nostr events use tags for references and metadata. Here's how to access them:

```java
note.ifPresent(n -> {
    // Get first value of "p" tag (mentioned pubkey)
    String mentionedPubkey = n.getTagValue("p");
    if (mentionedPubkey != null) {
        System.out.println("Mentions: " + mentionedPubkey);
    }

    // Get all values of a tag type
    List<String> allMentions = n.getTagValues("p");
    System.out.println("All mentions: " + allMentions);

    // Access raw tags
    for (List<String> tag : n.tags()) {
        System.out.println("Tag: " + tag);
    }
});
```

## Complete example

Here's the full program:

```java
import xyz.tcheeric.nostrdb.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class NostrdbDemo {
    public static void main(String[] args) {
        Path dbPath = Path.of(System.getProperty("user.home"), ".nostrdb-demo");

        try (Ndb ndb = Ndb.open(dbPath)) {
            System.out.println("Database opened at: " + dbPath);

            // Query for text notes
            try (Transaction txn = ndb.beginTransaction();
                 Filter filter = Filter.builder()
                     .kinds(1)
                     .limit(10)
                     .build()) {

                List<Note> notes = ndb.queryNotes(txn, filter, 10);
                System.out.println("\nFound " + notes.size() + " text notes:");

                for (Note note : notes) {
                    System.out.println("\n--- Note ---");
                    System.out.println("ID: " + note.id().substring(0, 16) + "...");
                    System.out.println("Author: " + note.pubkey().substring(0, 16) + "...");
                    System.out.println("Content: " + note.content());
                }
            }
        }
    }
}
```

## Next steps

Now that you have the basics working, explore these topics:

- [How to query events](../how-to/query-events.md) - Advanced filtering techniques
- [How to subscribe to real-time events](../how-to/subscribe-events.md) - Get notified of new events
- [API Reference](../reference/api.md) - Complete API documentation
- [Architecture](../explanation/architecture.md) - Understand how nostrdb works
