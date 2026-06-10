# Cache As a File

Reusable Kotlin library for file-backed caching, plus a Spring Boot demo app that consumes the library.

## Modules

- `file-cache-core` - Maven-publishable library with `FileCache<K, V>`, `JsonFileCache<K, V>`, and Spring Cache adapter classes.
- `file-cache-demo` - Spring Boot REST app that demonstrates `@Cacheable`, `@CachePut`, and `@CacheEvict`.

## Library Usage

Use `file-cache-core` directly when you do not need Spring:

```kotlin
val cache = JsonFileCache(
    cacheName = "users",
    keyType = String::class.java,
    valueType = User::class.java,
    cacheDirectory = Path.of("cache")
)

cache.put("user-1", user)
val cachedUser = cache.get("user-1")
```

Each named cache is persisted as a JSON file:

```text
cache/
├── users.json
└── usersByUsername.json
```

Use the library in a Spring application:

```kotlin
@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    fun cacheManager(): CacheManager =
        FileCacheManager(cacheDirectory = Path.of("cache"))
}
```

Then use standard Spring cache annotations:

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

## Demo API

Run the Spring Boot app:

```bash
mvn -f file-cache-core/pom.xml install
./gradlew :file-cache-demo:bootRun
```

Available endpoints:

- `GET /api/users` - list demo users
- `GET /api/users/{userId}` - get user by ID using the `users` cache
- `GET /api/users/username/{username}` - get user by username using the `usersByUsername` cache
- `POST /api/users` - create user and update the `users` cache
- `PUT /api/users/{userId}/login` - update last login and update the `users` cache
- `DELETE /api/users/{userId}` - delete user and evict from the `users` cache
- `DELETE /api/users/cache` - clear user caches
- `GET /api/users/cache/stats` - show demo storage statistics

## Build

```bash
mvn -f file-cache-core/pom.xml verify
mvn -f file-cache-core/pom.xml install
./gradlew :file-cache-demo:build
```

## Publish Core Library

Only `file-cache-core` is a Maven library. The demo app remains a Gradle Spring Boot app and is not published.

Publish the core artifact to your local Maven repository:

```bash
mvn -f file-cache-core/pom.xml install
```

Another project can then consume it with:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.github.senocak:file-cache-core:0.0.1-SNAPSHOT")
}
```

The demo app writes cache files to `cache/` by default. Override it with:

```yaml
caaf:
  cache:
    directory: /tmp/my-cache
```
