# File-Based Caching Implementation for Spring Boot

This project demonstrates a custom file-based caching implementation in Spring Boot using Kotlin. The cache stores data in memory and persists it to JSON files, allowing cached entries to survive application restarts.

## Features

- **Generic Cache Interface**: `FileCache<K, V>` provides a type-safe caching interface
- **JSON Persistence**: `JsonFileCache<K, V>` stores cache data in JSON files using Jackson
- **Spring Integration**: Full integration with Spring's caching abstraction
- **Automatic Loading**: Cache data is automatically loaded from files at startup
- **Multiple Caches**: Support for multiple named caches, each stored in separate files
- **Thread-Safe**: Uses `ConcurrentHashMap` for thread-safe operations

## Architecture

### Core Components

1. **`FileCache<K, V>`** - Generic interface defining cache operations
2. **`JsonFileCache<K, V>`** - Implementation that stores data in memory and JSON files
3. **`FileBackedSpringCache<K, V>`** - Adapter that implements Spring's `Cache` interface
4. **`FileCacheManager`** - Spring `CacheManager` implementation
5. **`CacheConfig`** - Spring configuration that registers the cache manager

### File Structure

Cache files are stored in the `cache/` directory with the following naming convention:
- `cache/{cacheName}.json` - One JSON file per cache

## Usage

### Basic Caching Annotations

The implementation works seamlessly with Spring's caching annotations:

```kotlin
@Service
class UserService {
    
    @Cacheable(value = ["users"], key = "#userId")
    fun getUserById(userId: String): User? {
        // This will be cached and persisted to cache/users.json
        return userRepository.findById(userId)
    }
    
    @CachePut(value = ["users"], key = "#result.id")
    fun createUser(user: User): User {
        // This will update the cache with the new user
        return userRepository.save(user)
    }
    
    @CacheEvict(value = ["users"], key = "#userId")
    fun deleteUser(userId: String) {
        // This will remove the user from cache
        userRepository.deleteById(userId)
    }
    
    @CacheEvict(value = ["users"], allEntries = true)
    fun clearCache() {
        // This will clear all entries in the users cache
    }
}
```

### Cache Configuration

The cache manager is automatically configured as the default cache manager:

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

## API Endpoints

The project includes a REST API to demonstrate the caching functionality:

### User Operations

- `GET /api/users/{userId}` - Get user by ID (cached)
- `GET /api/users/username/{username}` - Get user by username (cached)
- `POST /api/users` - Create new user (updates cache)
- `PUT /api/users/{userId}/login` - Update last login (updates cache)
- `DELETE /api/users/{userId}` - Delete user (removes from cache)
- `GET /api/users` - Get all users (not cached)
- `DELETE /api/users/cache` - Clear all caches
- `GET /api/users/cache/stats` - Get cache statistics

### Example Usage

1. **Start the application**:
   ```bash
   ./gradlew bootRun
   ```

2. **Get a user (first call loads from storage)**:
   ```bash
   curl http://localhost:8080/api/users/user1
   ```

3. **Get the same user again (uses cache)**:
   ```bash
   curl http://localhost:8080/api/users/user1
   ```

4. **Create a new user**:
   ```bash
   curl -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{
       "username": "newuser",
       "email": "newuser@example.com",
       "firstName": "New",
       "lastName": "User"
     }'
   ```

5. **Check cache statistics**:
   ```bash
   curl http://localhost:8080/api/users/cache/stats
   ```

## Cache File Format

Cache files are stored as JSON objects where keys are the cache keys and values are the cached objects:

```json
{
  "user1": {
    "id": "user1",
    "username": "john.doe",
    "email": "john.doe@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "createdAt": "2024-01-15T10:30:00",
    "lastLoginAt": null
  },
  "user2": {
    "id": "user2",
    "username": "jane.smith",
    "email": "jane.smith@example.com",
    "firstName": "Jane",
    "lastName": "Smith",
    "createdAt": "2024-01-15T10:30:00",
    "lastLoginAt": "2024-01-15T11:45:00"
  }
}
```

## Benefits

1. **Persistence**: Cache data survives application restarts
2. **Transparency**: Cache files are human-readable JSON
3. **Debugging**: Easy to inspect cache contents
4. **Backup**: Cache files can be backed up or version controlled
5. **Migration**: Cache data can be moved between environments

## Limitations

1. **Type Safety**: Current implementation uses `String` keys and `Object` values for simplicity
2. **Performance**: File I/O on every cache operation (could be optimized with batching)
3. **Concurrency**: File writes are not atomic (could be improved with file locking)
4. **Memory Usage**: All cache data is kept in memory

## Future Improvements

1. **Type Reflection**: Use reflection to determine actual key/value types
2. **Batch Operations**: Implement batch writes to reduce I/O
3. **File Locking**: Add proper file locking for concurrent access
4. **Compression**: Add optional compression for large cache files
5. **TTL Support**: Add time-to-live functionality
6. **Cache Size Limits**: Add maximum cache size limits

## Building and Running

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Run tests
./gradlew test
```

The application will start on `http://localhost:8080` and create a `cache/` directory in the project root.
