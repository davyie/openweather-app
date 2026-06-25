# Design Pattern Review — Readability & Maintainability

Date: 2026-06-25
Scope: all modules (`openweather-core`, `openweather-adapter`, `rate-limit-starter`, `weather-spring-boot-starter`, `weather-web-api`)

## High-impact

### 1. `RateLimitService.resolveBucket()` — Strategy pattern

`rate-limit-starter/src/main/java/com/davyie/filters/RateLimitService.java:52-64` switches on `cacheType` at runtime and throws `RuntimeException("CacheType was not specified")` in the default branch.

**Fix:** introduce a `BucketResolver` interface with two implementations, `RedisBucketResolver` and `ConcurrentMapBucketResolver`. Select the implementation once in `RateLimitAutoConfiguration` based on the `ratelimit.cache-type` property, and inject it into `RateLimitService`. This removes the switch and turns an invalid configuration into a startup-time wiring failure instead of a runtime exception. Each resolver also becomes independently unit-testable.

### 2. Duplicated Redis template wiring — extract a factory

- `weather-spring-boot-starter/src/main/java/com/davyie/starter/WeatherAutoConfiguration.java:56-73`
- `rate-limit-starter/src/main/java/com/davyie/config/RateLimitAutoConfiguration.java:74-91`

Both build a `ReactiveRedisTemplate` with the same `JacksonJsonRedisSerializer` + `RedisSerializationContext` boilerplate, differing only by value type (`ForecastResponse` vs `Integer`).

**Fix:** extract a generic helper, e.g. `RedisTemplateFactory.create(ReactiveRedisConnectionFactory factory, Class<T> type)`, used by both modules. Removes ~15 duplicated lines per module.

### 3. Constructors exceeding 5 params — Parameter Object / Builder

- `OpenWeatherProvider` constructor: 6 positional args (`openweather-adapter/src/main/java/com/davyie/openweather_adapter/providers/OpenWeatherProvider.java:37-43`)
- `RateLimitService` constructor: 7 positional args (`rate-limit-starter/src/main/java/com/davyie/filters/RateLimitService.java:35-42`)

Both violate the project's own 5-param limit and are easy to mis-order — several adjacent `String`/`int`/`long` args with no compiler protection against transposition.

**Fix:** wrap each in a small config record built from the corresponding `*Properties` class (e.g. `OpenWeatherProviderConfig`, `RateLimitConfig`) and pass one object instead of N primitives.

### 4. Duplicated forecast → `DayTemperature` mapping

`OpenWeatherProvider.java:54-70` and `:77-98` both parse `dtTxt` with the same `DateTimeFormatter` pattern and build a `DayTemperature` the same way.

**Fix:** extract a private `toDayTemperature(ForecastItem item, TemperatureUnit unit, String cityName)` method and call it from both `getFiveDaysWeatherForLocation` and `getOneDayWeatherForLocations`. Removes the duplicated format string and mapping logic.

## Smaller cleanups

These aren't patterns on their own, but they fall out of the fixes above and are worth doing in the same pass:

| Location | Issue |
|---|---|
| `OpenWeatherProvider.java:104,109` | Comments `// Plan A` / `// Plan B` narrate control flow instead of naming it; disappears once cache/rate-limit/fetch are split into named methods. |
| `WeatherAutoConfiguration.java:81-123` | Fake provider is a 40-line anonymous inner class with dead code at `:117-118` (`list` is built but never used — `Flux.just(...)` ignores it). Promote to a top-level `FakeWeatherProvider implements WeatherProvider` and delete the dead list. |
| `RateLimitFilter.java:19` | `requestCounts` field is declared, never read or written. Dead code — delete. |
| `RateLimitService.java:22-29` | Mixed naming: `numberOfTokens`, `duration` (camelCase) vs `DAILY_LIMIT`, `CACHE_TTL` (instance fields styled as constants). Same issue at `OpenWeatherProvider.java:30` (`CACHE_TTL`). Rename to camelCase since these are not static finals. |
| `RateLimitService.java:113` | `.log("AfterFetch")` looks like leftover debug instrumentation. |
| `WeatherController.java:21-24` | `/hello` test endpoint left in the production controller. |
| `WeatherProperties.java:8-10`, `RateLimitProperties.java:9-11` | `@Data @Getter @Setter` is redundant — `@Data` already includes both. |

## Recommendation / sequencing

Do #1 (Strategy) and #3 (parameter objects) first — they fix actual rule violations (positional-arg limit, runtime-throw-as-control-flow) rather than just style. #2 and #4 are pure DRY wins and can follow. The smaller cleanups can be folded into whichever of the above touches the same file.
