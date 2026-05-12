# PromptHelm Android SDK

Official Kotlin / Android SDK for [PromptHelm](https://prompthelm.app) — versioned prompts, encrypted provider keys, and a multi-provider LLM gateway with first-class cost & latency telemetry.

[![CI](https://github.com/Runivox/prompt-helm-sdk-android/actions/workflows/ci.yml/badge.svg)](https://github.com/Runivox/prompt-helm-sdk-android/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/app.prompthelm/sdk-android.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/app.prompthelm/sdk-android)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

- **Min Android SDK** 24 (Android 7.0)
- **Kotlin** 2.1+ • **Coroutines** 1.9+ • **Ktor** 3.0+
- **No reflection** — `kotlinx.serialization` for everything on the wire
- Cold `Flow<StreamEvent>` for SSE streaming, with proper cancellation
- Exception hierarchy that maps 1:1 to backend HTTP status codes
- Consumer ProGuard rules embedded — works in minified release builds out of the box

---

## Install

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("app.prompthelm:sdk-android:0.1.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'app.prompthelm:sdk-android:0.1.0'
}
```

The SDK pulls in Ktor (`ktor-client-okhttp`), `kotlinx.coroutines`, and
`kotlinx.serialization` transitively. Ensure your project enables AndroidX
(`android.useAndroidX=true`) and targets JVM 17 in `compileOptions`.

---

## Quickstart

```kotlin
import app.prompthelm.sdk.ExecuteRequest
import app.prompthelm.sdk.PromptHelm
import kotlinx.coroutines.runBlocking

val client = PromptHelm(
    apiKey = BuildConfig.PROMPT_HELM_KEY, // never hard-code; use BuildConfig + secrets
    userAgent = "checkout-android/1.4.2", // helpful when one tenant ships many apps
)

runBlocking {
    val response = client.execute(
        ExecuteRequest(
            promptSlug = "support-triage",
            variables = mapOf("ticket" to "My subscription was charged twice."),
        ),
    )
    println(response.output)
    println("cost=${response.cost} tokens=${response.totalTokens}")
}
```

`PromptHelm` is safe to share across coroutines and screens — make it a
singleton in your DI graph and call `client.close()` when the host process
shuts down.

---

## Streaming with `Flow`

```kotlin
client.stream(
    ExecuteRequest(promptSlug = "support-triage", user = "Tell me a joke"),
).collect { event ->
    when (event) {
        is StreamEvent.Chunk -> view.appendText(event.content)
        is StreamEvent.Done  -> view.showCost(event.cost, event.totalTokens)
        is StreamEvent.Err   -> view.showError(event.errorCode, event.message)
    }
}
```

- The `Flow` is **cold** — collection starts when you `.collect`, and cancelling the surrounding coroutine aborts the HTTP stream.
- Streams are **not retried** on failure (a chunk may already be on the wire).
- The flow completes naturally after a `Done` event.

---

## Error handling

Every API failure surfaces as a typed exception so you can `catch` precisely:

```kotlin
try {
    client.execute(ExecuteRequest(promptSlug = "support-triage"))
} catch (e: AuthenticationException) {     // 401 — bad / revoked key
} catch (e: AuthorizationException) {      // 403 — no permission
} catch (e: NotFoundException) {           // 404 — slug not found in env
} catch (e: RateLimitException) {          // 429 — back off
} catch (e: ApiException) {                // other 4xx / 5xx
} catch (e: PromptHelmTimeoutException) {  // local timeout exceeded
}
```

Every `PromptHelmException` carries the `statusCode`, the optional `code`
(stable identifier from `docs/ERROR_CODES.md`), and the `correlationId`
echoed by the backend — log it for on-call.

### Retry policy

| Error                              | Retried? |
| ---------------------------------- | -------- |
| `5xx` from the gateway             | yes      |
| Network / IO failure               | yes      |
| `401`, `403`, `404`, `429`         | no       |
| `PromptHelmTimeoutException`       | no       |
| `CancellationException`            | no (propagates) |

Back-off is exponential with jitter: 250 ms → 500 ms → 1 s → 2 s → 4 s,
capped at 8 s. Configure with `maxRetries` (default `2`).

---

## Configuration

```kotlin
val client = PromptHelm(
    apiKey        = "phk_...",                    // required, 36 chars
    baseUrl       = "https://api.prompthelm.app", // override for staging
    timeoutMillis = 60_000L,
    maxRetries    = 2,
    userAgent     = "checkout-android/1.4.2",
    httpEngine    = null,                         // optional — inject your own Ktor engine
)
```

For pinned-cert deployments, build a Ktor engine yourself and pass it in:

```kotlin
val pinned = OkHttp.create {
    config {
        certificatePinner(
            CertificatePinner.Builder()
                .add("api.prompthelm.app", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .build(),
        )
    }
}
val client = PromptHelm(apiKey = "phk_...", httpEngine = pinned)
```

---

## ProGuard / R8

`consumer-rules.pro` is bundled inside the AAR. App modules that enable
`minifyEnabled true` automatically pick it up — no manual rule copying.
The rules preserve:

- The public class hierarchy under `app.prompthelm.sdk.*`.
- The sealed `StreamEvent` variants (so deserialization works after R8).
- The exception type names (so caller `catch` blocks remain valid).
- `kotlinx.serialization` `$$serializer` companions.

---

## Compatibility

| Component       | Version            |
| --------------- | ------------------ |
| Min Android SDK | 24 (Android 7.0)   |
| Compile SDK     | 35                 |
| Kotlin          | 2.1.0+             |
| AGP             | 8.7.0+             |
| JVM target      | 17                 |
| Ktor            | 3.0.0              |

The artifact is pure Kotlin + Ktor; the only Android-specific dependency
is the AGP namespace declaration, so the same code runs in any Android
project that meets the constraints above.

---

## Maven Central publish runbook

CI publishes to Maven Central whenever a `vX.Y.Z` tag is pushed.
The first-time setup (one engineer, one afternoon):

1. **Create a Sonatype Central account.** <https://central.sonatype.com>
2. **Claim the `app.prompthelm` namespace.** Sonatype will ask you to add
   a `TXT` record at `app.prompthelm` proving DNS ownership; the
   verification value is shown in the Central UI.
3. **Generate a publishing user token.** Account → "Generate User Token".
   Save the username and password — you will paste them into GitHub
   secrets in step 6.
4. **Generate a GPG signing key.**
   ```bash
   gpg --full-generate-key            # RSA 4096, no expiry, your @runivox identity
   gpg --list-secret-keys --keyid-format LONG
   gpg --armor --export-secret-keys <KEYID> > signing-key.asc
   gpg --keyserver keys.openpgp.org --send-keys <KEYID>
   ```
5. **Verify the public key is reachable** at
   `https://keys.openpgp.org/vks/v1/by-fingerprint/<FPR>` — Sonatype
   resolves it from there.
6. **Add four GitHub secrets** to `Runivox/prompt-helm-sdk-android`:

   | Secret             | Value                                                |
   | ------------------ | ---------------------------------------------------- |
   | `SONATYPE_USERNAME`| Token username from step 3                           |
   | `SONATYPE_PASSWORD`| Token password from step 3                           |
   | `GPG_KEY`          | Contents of `signing-key.asc` (full ASCII-armored)   |
   | `GPG_PASSWORD`     | Passphrase for the GPG key                           |

7. **Cut a release.**
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```
   The `release` job runs `./gradlew :sdk:publishAndReleaseToMavenCentral`.
   Vanniktech's plugin uploads the artifact, signs it, and (because the
   build is configured with `automaticRelease = true`) closes and
   releases the staging repository in one step. Expect the artifact to
   appear on `repo1.maven.org` within ~30 minutes.

---

## Contributing

1. `./gradlew :sdk:test` — run unit tests
2. `./gradlew :sdk:assembleRelease` — build the release AAR
3. Open a PR with a [Conventional Commit](https://www.conventionalcommits.org/) title.

The SDK has zero runtime dependencies beyond Ktor + Coroutines + kotlinx.serialization. Adding a new transitive dependency requires team-lead approval — keep the surface tiny.

---

## License

[MIT](LICENSE) © 2026 Runivox
