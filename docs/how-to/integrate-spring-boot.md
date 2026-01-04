# How to Integrate with Spring Boot

This guide shows how to integrate nostrdb-jni into a Spring Boot application.

## Add dependency

```xml
<dependency>
    <groupId>xyz.tcheeric</groupId>
    <artifactId>nostrdb-jni</artifactId>
    <version>0.1.1</version>
</dependency>
```

## Create a service

Create a Spring-managed service that handles the database lifecycle:

```java
@Service
public class NostrdbService {

    private Ndb ndb;

    @PostConstruct
    public void init() {
        Path dbPath = Path.of(System.getProperty("user.home"), ".myapp", "nostrdb");
        dbPath.toFile().mkdirs();
        ndb = Ndb.open(dbPath);
    }

    @PreDestroy
    public void shutdown() {
        if (ndb != null) {
            ndb.close();
        }
    }

    public void ingestEvent(String eventJson) {
        ndb.processEvent(eventJson);
    }

    public List<Note> queryNotes(int kind, int limit) {
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder()
                 .kinds(kind)
                 .limit(limit)
                 .build()) {

            return ndb.queryNotes(txn, filter, limit);
        }
    }

    public Optional<Note> findById(String eventId) {
        try (Transaction txn = ndb.beginTransaction()) {
            return ndb.getNoteById(txn, eventId);
        }
    }

    public Ndb getNdb() {
        return ndb;
    }
}
```

## Configuration

Add to `application.properties`:

```properties
nostrdb.path=${user.home}/.myapp/nostrdb
```

Use `@Value` to inject:

```java
@Service
public class NostrdbService {

    @Value("${nostrdb.path}")
    private String dbPath;

    @PostConstruct
    public void init() {
        Path path = Path.of(dbPath);
        path.toFile().mkdirs();
        ndb = Ndb.open(path);
    }
}
```

## REST controller

Expose nostrdb queries via REST:

```java
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NostrdbService nostrdbService;

    public NoteController(NostrdbService nostrdbService) {
        this.nostrdbService = nostrdbService;
    }

    @GetMapping
    public List<NoteDto> getNotes(
            @RequestParam(defaultValue = "1") int kind,
            @RequestParam(defaultValue = "50") int limit) {

        return nostrdbService.queryNotes(kind, limit).stream()
            .map(this::toDto)
            .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteDto> getNote(@PathVariable String id) {
        return nostrdbService.findById(id)
            .map(this::toDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    private NoteDto toDto(Note note) {
        return new NoteDto(
            note.id(),
            note.pubkey(),
            note.kind(),
            note.content(),
            note.createdAt()
        );
    }
}
```

## Background sync service

Sync events from relays in the background:

```java
@Service
public class RelaySyncService {

    private final NostrdbService nostrdbService;

    @Scheduled(fixedRate = 30000)
    public void syncEvents() {
        // Fetch from relays and ingest
        List<String> events = fetchFromRelays();
        for (String event : events) {
            try {
                nostrdbService.ingestEvent(event);
            } catch (Exception e) {
                // Log and continue
            }
        }
    }
}
```

Enable scheduling in your application:

```java
@SpringBootApplication
@EnableScheduling
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

## WebSocket for real-time updates

Push new events to clients via WebSocket:

```java
@Service
public class EventPushService {

    private final NostrdbService nostrdbService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    public void startSubscription() {
        // Start background thread to poll and push
        new Thread(this::pollLoop).start();
    }

    private void pollLoop() {
        Filter filter = Filter.builder().kinds(1).build();
        Subscription sub = nostrdbService.getNdb().subscribe(filter);

        while (true) {
            List<Long> keys = sub.poll(100);
            if (!keys.isEmpty()) {
                try (Transaction txn = nostrdbService.getNdb().beginTransaction()) {
                    for (Long key : keys) {
                        nostrdbService.getNdb().getNoteByKey(txn, key)
                            .ifPresent(note -> {
                                messagingTemplate.convertAndSend("/topic/notes", toDto(note));
                            });
                    }
                }
            }
            Thread.sleep(1000);
        }
    }
}
```

## Health indicator

Add a health check for nostrdb:

```java
@Component
public class NostrdbHealthIndicator implements HealthIndicator {

    private final NostrdbService nostrdbService;

    @Override
    public Health health() {
        try (Transaction txn = nostrdbService.getNdb().beginTransaction()) {
            return Health.up()
                .withDetail("status", "connected")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .build();
        }
    }
}
```
