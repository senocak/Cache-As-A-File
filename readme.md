# Cache as a File: JSON-Only Caching in Kotlin

Most caches are backed by memory. This project takes the opposite approach: the JSON file is the cache.

Every read operation loads the current JSON file from disk. Every write operation reads the current file, modifies the snapshot, and writes it back. There is no in-memory `ConcurrentHashMap` inside the core cache implementation.

The repository contains two modules:

- `file-cache-core`: the reusable Kotlin library.
- `file-cache-demo`: a Spring Boot application that demonstrates the library with `@Cacheable`, `@CachePut`, and `@CacheEvict`.

This article focuses on the core logic, which is the part worth publishing and reusing.

## The Core Idea

The cache has one source of truth:

1. A JSON file per named cache.

For a Spring application with multiple named caches, the structure can look like this:

```text
cache/
|-- users.json
`-- usersByUsername.json
```

Each cache file stores a full JSON object snapshot. Reads and writes operate on that file directly.

## The Minimal Cache Contract

The core module starts with a small typed contract:

```kotlin
interface FileCache<K : Any, V : Any> {
    fun get(key: K): V?
    fun put(key: K, value: V)
    fun evict(key: K): V?
    fun clear()
    fun containsKey(key: K): Boolean
    fun size(): Int
    fun keys(): Set<K>
}
```

The important design choice is that this interface does not know about Spring, HTTP, databases, or application-specific models. It only describes cache behavior.

That separation keeps `JsonFileCache` usable in plain Kotlin code and makes the Spring integration an adapter rather than the center of the design.

## JsonFileCache

`JsonFileCache<K, V>` is the main implementation.

At construction time it receives:

- `cacheName`: the logical cache name.
- `keyType`: the runtime key type for Jackson map deserialization.
- `valueType`: the runtime value type for Jackson map deserialization.
- `cacheDirectory`: where JSON files should live.
- `objectMapper`: optional, with a Kotlin and Java time friendly default.
- `applicationEventPublisher`: optional, used to publish cache insert and eviction events.

Example:

```kotlin
val cache = JsonFileCache(
    cacheName = "users",
    keyType = String::class.java,
    valueType = User::class.java,
    cacheDirectory = Path.of("cache")
)

cache.put("user-1", user)

val cachedUser: User? = cache.get("user-1")
```

Internally, `get` reads the latest file snapshot and returns the requested key:

```kotlin
override fun get(key: K): V? =
    ioLock.withLock {
        readFromFile()[key]
    }
```

Writes read the current file, update that snapshot, and persist it:

```kotlin
override fun put(key: K, value: V) {
    ioLock.withLock {
        val snapshot: LinkedHashMap<K, V> = readFromFile()
        snapshot[key] = value
        saveToFile(snapshot = snapshot)
    }
}
```

Eviction also works against the current file snapshot:

```kotlin
override fun evict(key: K): V? {
    ioLock.withLock {
        val snapshot: LinkedHashMap<K, V> = readFromFile()
        val removed: V? = snapshot.remove(key)
        if (removed != null) {
            saveToFile(snapshot = snapshot)
        }
        return removed
    }
}
```

This gives the cache predictable file-only semantics: if the file changes, the next `get`, `containsKey`, `size`, or `keys` call sees the file's current contents.

## Reading Data From File

During initialization, `JsonFileCache` only creates the cache directory:

```kotlin
init {
    Files.createDirectories(cacheDirectory)
}
```

The read path intentionally treats missing and empty files as an empty cache:

```kotlin
if (!Files.exists(cacheFile) || Files.size(cacheFile) == 0L) {
    return LinkedHashMap()
}
```

When data exists, Jackson reads it as a typed map and returns a fresh snapshot:

```kotlin
val loadedData: Map<K, V> = objectMapper.readValue(input, mapType)
return LinkedHashMap(loadedData)
```

The explicit `keyType` and `valueType` constructor arguments matter here. They let Jackson reconstruct `Map<K, V>` instead of falling back to untyped maps.

## Persisting Safely

Every mutation persists a full snapshot.

The implementation does not write directly to the final cache file. It writes to a temporary file first:

```kotlin
val tempFile: Path = Files.createTempFile(cacheDirectory, cacheFile.fileName.toString(), ".tmp")
```

Then it serializes the snapshot:

```kotlin
objectMapper.writerWithDefaultPrettyPrinter().writeValue(output, snapshot)
```

Finally, it moves the temporary file into place:

```kotlin
Files.move(
    tempFile,
    cacheFile,
    StandardCopyOption.REPLACE_EXISTING,
    StandardCopyOption.ATOMIC_MOVE
)
```

If the filesystem does not support atomic moves, the implementation falls back to a normal replace:

```kotlin
Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING)
```

This approach avoids leaving a half-written cache file during normal writes. A failed serialization deletes the temporary file and rethrows the exception.

## Thread Safety

The cache uses a `ReentrantLock` to serialize read-modify-write operations against the file:

The I/O lock keeps two threads from reading and writing the same cache file at the same time:

```kotlin
private val ioLock: ReentrantLock = ReentrantLock()
```

Every mutating operation reads a snapshot, changes it, and writes that snapshot:

```kotlin
val snapshot: LinkedHashMap<K, V> = readFromFile()
```

This is safer than reading outside the lock and writing later, because it keeps each read-modify-write cycle consistent inside the current process.

## Cache Name Validation

The cache name becomes part of the file path, so it is validated before use.

Allowed characters are:

- letters
- digits
- dots
- dashes
- underscores

Blank names and path-like names such as `users/active` are rejected. That keeps a cache name from accidentally becoming a directory traversal or nested path.

## Jackson Configuration

The default object mapper is configured for Kotlin data classes and Java time types:

```kotlin
ObjectMapper()
    .registerModule(KotlinModule.Builder().build())
    .registerModule(JavaTimeModule())
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
```

This means data classes like this can be cached directly:

```kotlin
data class User(
    val id: String,
    val username: String,
    val createdAt: LocalDateTime
)
```

The JSON remains readable and timestamp fields are written as ISO-like values instead of numeric timestamps.

## Spring Cache Integration

The core library also includes Spring adapters. File reads and writes work without Spring event publishing, but Spring applications can provide an `ApplicationEventPublisher` to receive cache events.

Spring's `Cache` interface stores values as `Any?`, while `JsonFileCache` needs a concrete value type to deserialize from disk. The bridge solves that with `SpringCacheEntry`:

```kotlin
data class SpringCacheEntry(
    val type: String,
    val value: JsonNode
)
```

Each Spring cache value is stored with:

- the Java class name of the original value
- the JSON tree for the value

When reading, the adapter loads the class and converts the JSON tree back:

```kotlin
private fun SpringCacheEntry.toValue(): Any {
    val valueClass: Class<*> = Class.forName(type)
    return objectMapper.treeToValue(value, valueClass)
}
```

This is what allows a file-only Spring cache to read typed values from disk on every lookup.

## FileBackedSpringCache

`FileBackedSpringCache` adapts a `FileCache<String, SpringCacheEntry>` to Spring's `Cache` interface.

Putting a value into Spring cache becomes:

```kotlin
override fun put(key: Any, value: Any?) {
    if (value == null) {
        return
    }
    fileCache.put(
        key = key.toCacheKey(),
        value = SpringCacheEntry(
            type = value.javaClass.name,
            value = objectMapper.valueToTree(value)
        )
    )
}
```

There are two intentional choices here:

- Spring cache keys are serialized to strings.
- Null values are ignored.

The default key serializer is `Any::toString`, but `FileCacheManager` accepts a custom serializer when an application needs stable key formatting.

## FileCacheManager

`FileCacheManager` is the Spring `CacheManager` implementation.

It lazily creates one file-backed cache per Spring cache name:

```kotlin
override fun getCache(name: String): Cache =
    caches.computeIfAbsent(name) { cacheName ->
        val fileCache = JsonFileCache(
            cacheName = cacheName,
            keyType = String::class.java,
            valueType = SpringCacheEntry::class.java,
            cacheDirectory = cacheDirectory,
            objectMapper = objectMapper
        )

        FileBackedSpringCache(
            name = cacheName,
            fileCache = fileCache,
            objectMapper = objectMapper,
            keySerializer = keySerializer,
            applicationEventPublisher = applicationEventPublisher
        )
    }
```

From the application side, this feels like ordinary Spring caching:

```kotlin
@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    fun cacheManager(applicationEventPublisher: ApplicationEventPublisher): CacheManager =
        FileCacheManager(
            cacheDirectory = Path.of("cache"),
            applicationEventPublisher = applicationEventPublisher
        )
}
```

`FileCacheManager` can also clear all managed caches periodically. The interval is optional:

```kotlin
FileCacheManager(
    cacheDirectory = Path.of("cache"),
    clearInterval = Duration.ofMinutes(10)
)
```

When `clearInterval` is `null` or `Duration.ZERO`, no background clearing task is scheduled. When it is positive, the manager starts one daemon thread and calls `clearAll()` with that fixed delay. `FileCacheManager` implements `AutoCloseable`, so the scheduler is shut down when the Spring bean is destroyed.

In the demo app this is exposed as a Spring Boot property:

```yaml
caaf:
  cache:
    directory: cache
    clear-interval: 10s # 0 means no automatic clearing
```

Then service methods can use standard annotations:

```kotlin
@Service
class UserService {
    @Cacheable(value = ["users"], key = "#userId")
    fun getUserById(userId: String): User? =
        userRepository.findById(userId)

    @CachePut(value = ["users"], key = "#result.id")
    fun createUser(user: User): User =
        userRepository.save(user)

    @CacheEvict(value = ["users"], key = "#userId")
    fun deleteUser(userId: String) {
        userRepository.deleteById(userId)
    }
}
```

## Cache Events

The core library publishes events after a cache insert or eviction succeeds.

The event model is intentionally small:

```kotlin
sealed interface CacheEvent<K : Any, V : Any>

data class CacheInsertedEvent<K : Any, V : Any>(
    val cacheName: String,
    val key: K,
    val value: V,
    val previousValue: V?
)

data class CacheEvictedEvent<K : Any, V : Any>(
    val cacheName: String,
    val key: K,
    val value: V
)
```

Events are published through Spring's `ApplicationEventPublisher`. For direct `JsonFileCache` usage, pass a publisher if you want events:

```kotlin
val cache = JsonFileCache(
    cacheName = "users",
    keyType = String::class.java,
    valueType = User::class.java,
    cacheDirectory = Path.of("cache"),
    applicationEventPublisher = applicationEventPublisher
)
```

For Spring cache usage, pass the publisher to `FileCacheManager`:

```kotlin
FileCacheManager(
    cacheDirectory = Path.of("cache"),
    applicationEventPublisher = applicationEventPublisher
)
```

`FileBackedSpringCache` publishes decoded application values, not internal `SpringCacheEntry` objects.

In a Spring application, consume the events with normal Spring event listeners:

```kotlin
@Component
class UserCacheEventListener {
    @EventListener
    fun onCacheEvent(event: CacheEvent<*, *>) {
        when (event) {
            is CacheInsertedEvent<*, *> ->
                println("Inserted ${event.key} into ${event.cacheName}")
            is CacheEvictedEvent<*, *> ->
                println("Evicted ${event.key} from ${event.cacheName}")
        }
    }
}
```

`put()` publishes `CacheInsertedEvent`, `evict()` publishes `CacheEvictedEvent` when an entry existed, and `clear()` publishes one `CacheEvictedEvent` per removed entry. Delivery follows the application's Spring event multicaster configuration.

## Tradeoffs

This design is intentionally simple. That simplicity is useful, but it has clear tradeoffs.

It works well when:

- cache entries are moderate in size
- write frequency is low or medium
- readable local persistence is valuable
- direct file-based reads are acceptable
- Redis or another external cache would be operational overhead

It is not a replacement for Redis, Caffeine, or a distributed cache when:

- many application instances must share cache state
- reads or writes are extremely frequent
- cache files would become very large
- eviction policies, TTL, or maximum size are required
- cross-process file locking is required

The current implementation reads the whole cache file for lookups and persists the whole cache snapshot on each mutation. That is easy to reason about and easy to inspect, but it is not optimized for high throughput.

## Testing

Run the core library tests:

```bash
mvn -f file-cache-core/pom.xml test
```

Run the demo tests:

```bash
mvn -f file-cache-demo/pom.xml test
```

The tests cover:

- JSON file reads and persistence
- empty file handling
- cache name validation
- Java time serialization
- Spring value wrapping and typed reads
- loader exception handling
- cache manager reuse
- configurable periodic cache clearing
- Spring `ApplicationEventPublisher` cache event publishing
- HTTP workflows in the demo app
- retrieval from file cache after backing storage is removed

## Using the Core Library

Install the core artifact locally:

```bash
mvn -f file-cache-core/pom.xml install
```

Consume it from another Maven project:

```xml
<dependency>
    <groupId>com.github.senocak</groupId>
    <artifactId>file-cache-core</artifactId>
    <version>0.0.3</version>
</dependency>
```

Or from Gradle:

```kotlin
dependencies {
    implementation("com.github.senocak:file-cache-core:0.0.3")
}
```

## Closing Thought

The main lesson in this project is not that every cache should be file-backed. Most should not.

The useful idea is narrower: if a cache is local, moderate in size, and must be inspectable as a file, a JSON-only cache can be a practical alternative to an in-memory map.

The implementation stays small because the responsibilities are separated:

- `FileCache` defines behavior.
- `JsonFileCache` handles file reads and writes.
- `FileBackedSpringCache` adapts values to Spring.
- `FileCacheManager` creates named caches on demand.

That separation is what makes the core logic easy to test, explain, and reuse.
