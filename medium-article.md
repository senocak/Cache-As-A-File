# Building a Persistent File-Based Cache in Spring Boot with Kotlin

## Introduction

Caching is a fundamental technique for improving application performance by storing frequently accessed data in memory. However, traditional in-memory caches have a significant limitation: they lose all data when the application restarts. This can be problematic for applications that rely on expensive computations or external API calls.

In this article, we'll explore how to build a **file-based caching system** that combines the speed of in-memory caching with the persistence of file storage. Our implementation will integrate seamlessly with Spring Boot's caching abstraction, allowing you to use familiar annotations like `@Cacheable`, `@CachePut`, and `@CacheEvict`.

## The Problem: Cache Persistence

Traditional caching solutions like Spring's default cache manager store data only in memory. When your application restarts, all cached data is lost, forcing expensive operations to be re-executed. Consider this scenario:

```kotlin
@Service
class UserService {
    @Cacheable("users")
    fun getUserById(id: String): User {
        // Expensive database query or API call
        return userRepository.findById(id)
    }
}
```

The first request after a restart will always hit the database, even if the same user was accessed thousands of times before the restart.

## The Solution: File-Based Caching

Our solution addresses this problem by implementing a cache that:
- Stores data in memory for fast access
- Persists data to JSON files for durability
- Automatically loads cached data on application startup
- Integrates seamlessly with Spring's caching abstraction

## Core Architecture

### 1. The Generic Cache Interface

We start with a generic interface that defines the core caching operations:

```kotlin
interface FileCache<K, V> {
    fun get(key: K): V?
    fun put(key: K, value: V)
    fun evict(key: K): V?
    fun clear()
    fun containsKey(key: K): Boolean
    fun size(): Int
    fun keys(): Set<K>
}
```

This interface is generic, allowing us to cache any type of key-value pairs while maintaining type safety.

### 2. JSON File Implementation

The heart of our solution is the `JsonFileCache` class:

```kotlin
class JsonFileCache<K, V>(
    private val cacheName: String,
    private val keyType: Class<K>,
    private val valueType: Class<V>
) : FileCache<K, V> {
    
    private val cache: ConcurrentHashMap<K, V> = ConcurrentHashMap()
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val cacheFile: File
    
    init {
        val cacheDir = File("cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        cacheFile = File(cacheDir, "$cacheName.json")
        loadFromFile() // Load existing data at startup
    }
}
```

**Key Features:**
- **ConcurrentHashMap**: Thread-safe in-memory storage
- **Jackson Configuration**: Proper serialization of Kotlin data classes and Java time types
- **Automatic Loading**: Existing cache data is loaded at startup
- **File Organization**: Each cache gets its own JSON file

### 3. Spring Integration Layer

To integrate with Spring's caching abstraction, we need two adapter classes:

#### FileBackedSpringCache
This class implements Spring's `Cache` interface and delegates operations to our `FileCache`:

```kotlin
class FileBackedSpringCache<K, V>(
    private val name: String,
    private val fileCache: FileCache<K, V>
) : Cache {
    
    override fun get(key: Any): Cache.ValueWrapper? {
        @Suppress("UNCHECKED_CAST")
        val value = fileCache.get(key as K)
        return if (value != null) SimpleValueWrapper(value) else null
    }
    
    override fun put(key: Any, value: Any?) {
        if (value != null) {
            @Suppress("UNCHECKED_CAST")
            fileCache.put(key as K, value as V)
        }
    }
    
    // ... other methods
}
```

#### FileCacheManager
This class implements Spring's `CacheManager` interface:

```kotlin
class FileCacheManager : CacheManager {
    private val caches: ConcurrentHashMap<String, Cache> = ConcurrentHashMap()
    
    override fun getCache(name: String): Cache? {
        return caches.computeIfAbsent(name) { cacheName ->
            val fileCache = JsonFileCache<String, Any>(
                cacheName, 
                String::class.java, 
                Any::class.java
            )
            FileBackedSpringCache(cacheName, fileCache)
        }
    }
}
```

### 4. Spring Configuration

Register our cache manager as the default:

```kotlin
@Configuration
@EnableCaching
class CacheConfig {
    
    @Bean
    @Primary
    fun cacheManager(): CacheManager {
        return FileCacheManager()
    }
}
```

## Usage: Seamless Integration

With our implementation, you can use Spring's caching annotations exactly as you would with any other cache manager:

```kotlin
@Service
class UserService {
    
    @Cacheable(value = ["users"], key = "#userId")
    fun getUserById(userId: String): User? {
        logger.info("Loading user from storage: $userId")
        return userStorage[userId]
    }
    
    @CachePut(value = ["users"], key = "#result.id")
    fun createUser(username: String, email: String, firstName: String, lastName: String): User {
        val user = User(/* ... */)
        userStorage[user.id] = user
        return user
    }
    
    @CacheEvict(value = ["users"], key = "#userId")
    fun deleteUser(userId: String): Boolean {
        return userStorage.remove(userId) != null
    }
}
```

## File Structure and Persistence

Our cache creates a clean file structure:

```
cache/
├── users.json              # Cache for user ID lookups
├── usersByUsername.json    # Cache for username lookups
└── other-cache.json        # Any other named cache
```

Each cache file contains JSON data that persists across application restarts:

```json
{
  "user_123": {
    "id": "user_123",
    "username": "john.doe",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "createdAt": "2024-01-15T10:30:00",
    "lastLoginAt": null
  }
}
```

## Key Benefits

### 1. **Performance + Persistence**
- Fast in-memory access for cached data
- Data survives application restarts
- No need to rebuild cache from scratch

### 2. **Transparency**
- Human-readable JSON files
- Easy to inspect cache contents
- Debugging and monitoring capabilities

### 3. **Flexibility**
- Multiple named caches
- Generic type support
- Seamless Spring integration

### 4. **Operational Benefits**
- Cache files can be backed up
- Data can be migrated between environments
- Cache contents can be version controlled

## Performance Considerations

### Memory Usage
- All cache data is kept in memory for fast access
- Consider implementing cache size limits for large datasets
- Monitor memory usage in production

### File I/O
- Each cache operation triggers a file write
- Consider implementing batch writes for high-frequency operations
- File writes are not atomic (could be improved with file locking)

### Type Safety
- Current implementation uses `String` keys and `Object` values
- Could be enhanced with reflection to determine actual types
- Consider implementing type validation

## Production Enhancements

For production use, consider these improvements:

### 1. **Batch Operations**
```kotlin
private fun saveToFile() {
    // Implement batching to reduce I/O
    if (shouldBatchWrite()) {
        scheduleBatchWrite()
    } else {
        writeImmediately()
    }
}
```

### 2. **Cache Size Limits**
```kotlin
override fun put(key: K, value: V) {
    if (cache.size >= maxSize) {
        evictOldestEntry()
    }
    cache[key] = value
    saveToFile()
}
```

### 3. **Time-to-Live (TTL)**
```kotlin
data class CacheEntry<V>(
    val value: V,
    val timestamp: LocalDateTime,
    val ttl: Duration
)
```

### 4. **Compression**
```kotlin
private fun saveToFile() {
    val jsonContent = objectMapper.writeValueAsString(cache)
    val compressedContent = compress(jsonContent)
    cacheFile.writeBytes(compressedContent)
}
```

## Testing the Implementation

You can test the cache functionality with these API endpoints:

```bash
# Get user (first call loads from storage, subsequent calls use cache)
curl http://localhost:8080/api/users/user123

# Create user (updates cache)
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","email":"new@example.com","firstName":"New","lastName":"User"}'

# Delete user (removes from cache)
curl -X DELETE http://localhost:8080/api/users/user123

# Clear all caches
curl -X DELETE http://localhost:8080/api/users/cache
```

## Conclusion

We've successfully built a file-based caching system that combines the best of both worlds: the speed of in-memory caching and the persistence of file storage. This implementation:

- **Integrates seamlessly** with Spring Boot's caching abstraction
- **Provides persistence** across application restarts
- **Maintains performance** with in-memory storage
- **Offers transparency** with human-readable JSON files
- **Supports multiple caches** with independent file storage

This solution is particularly valuable for:
- Applications with expensive computations
- Systems that call external APIs
- Development environments where cache warm-up is time-consuming
- Production systems where cache persistence provides operational benefits

The implementation demonstrates how to extend Spring's caching framework with custom persistence strategies while maintaining the familiar programming model that developers expect.

---

*The complete implementation is available on GitHub with full source code, tests, and documentation.*
