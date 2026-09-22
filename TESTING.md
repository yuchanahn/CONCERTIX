# Test Infrastructure

## TEST-001

Spring Boot integration tests use isolated Testcontainers infrastructure.

- PostgreSQL: `postgres:17-alpine`
- Redis: `redis:7-alpine`
- Ports are assigned randomly by Docker.
- A single static container instance is shared during the test JVM lifecycle.
- Reusable containers are not enabled.

The test base class starts static singleton containers manually and injects datasource and Redis connection properties with `@DynamicPropertySource`. Existing environment variables such as `DB_URL` and `REDIS_PORT` do not control the test connections.

The test infrastructure also overrides `spring.data.redis.url` dynamically with the Testcontainers Redis host, mapped port, and database `/0` value. This prevents a development `SPRING_DATA_REDIS_URL` environment variable from redirecting integration tests to a local Redis instance.

Flyway migrations and JPA validation continue to run against the temporary PostgreSQL container. Tests may clear data inside the temporary containers, and only those containers are removed after the test lifecycle ends.

## Requirements

Docker must be available before running tests. See the Testcontainers lifecycle guide: https://docs.docker.com/guides/testcontainers-java-lifecycle/ The test suite fails when Testcontainers cannot start. It does not connect to or clean development PostgreSQL/Redis services.

## Run

```bash
./gradlew test
```

## Failure behavior

If Docker is unavailable or container startup fails, tests fail during infrastructure initialization. There is no fallback to localhost development services and no test skipping.
