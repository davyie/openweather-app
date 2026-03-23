# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Build all modules
mvn clean install

# Run tests
mvn verify

# Check code formatting (required before commit)
mvn spotless:check

# Apply code formatting (Google Java Format)
mvn spotless:apply

# CI build (formatting check + tests)
mvn -B spotless:check clean verify

# Run Docker stack (Redis + app)
cd docker && docker compose up -d

# Build Docker image via Jib
mvn spring-boot:build-image
```

## Architecture

Multi-module Maven project with hexagonal (ports & adapters) architecture:

- **`openweather-core`** — Domain interfaces (`WeatherProvider`) and models (`DayTemperature`, `Temperature`, `TemperatureUnit`). No Spring dependency; pure library.
- **`openweather-adapter`** — Implements `WeatherProvider` by calling the OpenWeather REST API. Handles Redis cache-aside (TTL: 10 min) and delegates daily 3rd-party API call enforcement to `RateLimitService`.
- **`rate-limit-starter`** — Spring Boot auto-configuration starter. Provides a servlet `RateLimitFilter` (Bucket4J token-bucket, per-IP) and `RateLimitService` (daily 3rd-party API call cap via Redis counter). Activates when `ratelimit.enabled=true`.
- **`weather-spring-boot-starter`** — Auto-configuration that wires `WeatherProvider` into the app context. Selects real (`openweather`) or fake provider based on `weather.provider` property.
- **`weather-web-api`** — Runnable Spring Boot app. Exposes `WeatherController` with reactive endpoints (WebFlux).

### Request Flow

```
HTTP Request
  → RateLimitFilter (Bucket4J, per-IP, 10 req/min)
  → WeatherController
  → WeatherProvider (OpenWeatherProvider)
      → Redis cache check
      → If miss: RateLimitService.canCallAPI() (daily limit, default 3/day)
      → WebClient → OpenWeather API
      → Cache result (10 min TTL)
```

### Key Configuration Properties (`application.properties`)

| Property | Default | Description |
|---|---|---|
| `weather.provider` | `openweather` | Use `fake` for testing |
| `weather.api-key` | — | OpenWeather API key |
| `weather.cache-ttl-minutes` | `10` | Redis cache TTL |
| `ratelimit.enabled` | `true` | Toggle rate limiting |
| `ratelimit.number-of-tokens` | `10` | Tokens per refill window |
| `ratelimit.duration` | `1` | Refill window (minutes) |
| `ratelimit.daily-limit` | `3` | Max 3rd-party API calls/day |
| `ratelimit.cache-type` | `REDIS` | `REDIS` or `CONCURRENTMAP` |

### REST Endpoints

```
GET /weather/hello                                          # Health check
GET /weather/summary?unit=CELSIUS&temperature=20&locations=1,2,3  # Locations above threshold tomorrow
GET /weather/locations/{locationId}                         # 5-day forecast for one location
```

Both `/summary` and `/locations/{id}` return reactive types (`Flux<DayTemperature>` / `Mono<List<DayTemperature>>`).

## Testing

Tests use `WebTestClient` (integration), `MockWebServer` (OkHttp3, for simulating OpenWeather API), and Mockito. The `fake` weather provider (activated via `weather.provider=fake` in `application-test.properties`) bypasses external calls.

Rate-limit tests use `CONCURRENTMAP` cache type to avoid requiring a real Redis instance.
