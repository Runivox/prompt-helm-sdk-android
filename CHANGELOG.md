# Changelog

All notable changes to `app.prompthelm:sdk-android` are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-05-13

### Added
- `PromptHelm` client with `execute(ExecuteRequest): ExecuteResponse`.
- `PromptHelm.stream(ExecuteRequest): Flow<StreamEvent>` over Server-Sent Events.
- Sealed `StreamEvent` hierarchy: `Chunk`, `Done`, `Err`.
- Exception hierarchy: `PromptHelmException`, `AuthenticationException`,
  `AuthorizationException`, `NotFoundException`, `RateLimitException`,
  `ApiException`, `PromptHelmTimeoutException`.
- Exponential back-off with jitter (250 ms, 500 ms, 1 s, …, capped at 8 s)
  for 5xx and network errors. 4xx, timeouts, and cancellations are not retried.
- Configurable `baseUrl`, `timeoutMillis`, `maxRetries`, `userAgent`, and
  `httpEngine` (advanced — inject a custom Ktor engine for cert pinning).
- Consumer ProGuard rules so R8 preserves the public API and
  `kotlinx.serialization` metadata in minified release builds.
- Maven Central publication via the Vanniktech Gradle plugin.
