# Cache as a File: Persistent JSON Caching in Kotlin

Most application caches are fast because they live in memory. That speed comes with a tradeoff: when the process restarts, the cache is gone.

This project explores a small, explicit alternative: keep cache entries in memory for fast reads, but persist every mutation to a JSON file so the cache can be reloaded on the next startup.

The repository contains two modules:

- `file-cache-core`: the reusable Kotlin library.
- `file-cache-demo`: a Spring Boot application that demonstrates the library with `@Cacheable`, `@CachePut`, and `@CacheEvict`.

This article focuses on the core logic, which is the part worth publishing and reusing.

## The Core Idea

The cache has two layers:

1. An in-memory `ConcurrentHashMap` for fast reads.
2. A JSON file per named cache for persistence.

For a Spring application with multiple named caches, the structure can look like this:

```text
cache/
|-- users.json
`-- usersByUsername.json
```

Each cache file stores a full JSON object snapshot. On startup, the file is read back into memory.

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

Internally, the cache keeps values in a `ConcurrentHashMap`:

```kotlin
private val cache: ConcurrentHashMap<K, V> = ConcurrentHashMap()
```

Reads are simple map lookups:

```kotlin
override fun get(key: K): V? =
    cache[key]
```

Writes update memory first, then persist the new snapshot:

```kotlin
override fun put(key: K, value: V) {
    cache[key] = value
    saveToFile()
}
```

Eviction only writes to disk when something was actually removed:

```kotlin
override fun evict(key: K): V? {
    val removed: V? = cache.remove(key)
    if (removed != null) {
        saveToFile()
    }
    return removed
}
```

This gives the cache predictable semantics: memory is the source of truth while the process is running, and the JSON file is the restart recovery mechanism.

## Loading Data on Startup

During initialization, `JsonFileCache` creates the cache directory and tries to load an existing cache file:

```kotlin
init {
    Files.createDirectories(cacheDirectory)
    loadFromFile()
}
```

The load path intentionally ignores missing and empty files:

```kotlin
if (!Files.exists(cacheFile) || Files.size(cacheFile) == 0L) {
    return
}
```

When data exists, Jackson reads it as a typed map:

```kotlin
val loadedData: Map<K, V> = objectMapper.readValue(input, mapType)
cache.clear()
cache.putAll(loadedData)
```

The explicit `keyType` and `valueType` constructor arguments matter here. They let Jackson reconstruct `Map<K, V>` instead of falling back to untyped maps.

## Persisting Safely

Every mutation persists a full snapshot.

The implementation does not write directly to the final cache file. It writes to a temporary file first:

```kotlin
val snapshot: Map<K, V> = LinkedHashMap(cache)
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

The cache uses two different tools for two different concerns:

- `ConcurrentHashMap` protects concurrent reads and updates to the in-memory cache.
- `ReentrantLock` serializes file load/save operations.

The I/O lock keeps two threads from writing the same cache file at the same time:

```kotlin
private val ioLock: ReentrantLock = ReentrantLock()
```

The implementation takes a snapshot before writing:

```kotlin
val snapshot: Map<K, V> = LinkedHashMap(cache)
```

That keeps file serialization independent from ongoing map iteration details.

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

The core library also includes a Spring adapter, but the file cache does not depend on Spring for its own behavior.

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

This is what allows a file-backed Spring cache to reload typed values after application restart.

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
            keySerializer = keySerializer
        )
    }
```

From the application side, this feels like ordinary Spring caching:

```kotlin
@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    fun cacheManager(): CacheManager =
        FileCacheManager(cacheDirectory = Path.of("cache"))
}
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

## Tradeoffs

This design is intentionally simple. That simplicity is useful, but it has clear tradeoffs.

It works well when:

- cache entries are moderate in size
- write frequency is low or medium
- readable local persistence is valuable
- startup cache recovery matters
- Redis or another external cache would be operational overhead

It is not a replacement for Redis, Caffeine, or a distributed cache when:

- many application instances must share cache state
- writes are extremely frequent
- cache files would become very large
- eviction policies, TTL, or maximum size are required
- cross-process file locking is required

The current implementation persists the whole cache snapshot on each mutation. That is easy to reason about and easy to inspect, but it is not optimized for high write throughput.

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

- JSON persistence and reload
- empty file handling
- cache name validation
- Java time serialization
- Spring value wrapping and typed reads
- loader exception handling
- cache manager reuse
- HTTP workflows in the demo app
- retrieval from cache after backing storage is removed

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

The useful idea is narrower: if a cache is local, moderate in size, and expensive to rebuild, a JSON-backed cache can be a practical middle ground between an in-memory map and a full external caching system.

The implementation stays small because the responsibilities are separated:

- `FileCache` defines behavior.
- `JsonFileCache` handles memory plus persistence.
- `FileBackedSpringCache` adapts values to Spring.
- `FileCacheManager` creates named caches on demand.

That separation is what makes the core logic easy to test, explain, and reuse.
