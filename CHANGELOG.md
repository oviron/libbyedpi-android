# Changelog

## v0.1.0 — 2026-05-16

Initial release. First public MIT-licensed standalone Android library for ByeDPI. Low-level argv-style JNI surface; typed Kotlin DSL deferred to v0.2.

- Artifact: single `libbyedpi-android-v0.1.0.aar` with arm64-v8a / armeabi-v7a / x86_64 bundled
- Kotlin stub `io.github.oviron.libbyedpi.ByeDpi` included in the .aar — consumers do not need to declare native methods themselves
- Three JNI methods: `nativeStart(args)`, `nativeStop()`, `nativeForceClose()` (escape hatch for when graceful stop fails)
- Bundled ByeDPI: [hufrea/byedpi@ba53229](https://github.com/hufrea/byedpi/tree/ba532298de7b28cfe854aea83d061369d13ca290) (v0.17.3-38)
- Build: AGP 8.12.2 library project, Kotlin 2.2.10, Android NDK 28.0.13004108, `-Wl,--build-id=sha1` for reproducibility
- Each release artifact ships with SHA-256 checksum and GPG detached signature

### Wrapper hardening over the FlClash-internal predecessor

- Reset the proxy atomic guard if `calloc` fails — previously the guard stuck at 1 forever and every subsequent `nativeStart` returned -1
- Validate every `strdup` return value before invoking byedpi's `main()` — previously a mid-loop OOM passed NULL slots into `getopt_long` and crashed
- Sentinel `server_fd = -1` before `main()` runs; `nativeStop` now refuses to call `shutdown` on the uninitialised fd (which would have targeted stdin of the host process)
- Silence `getopt` stderr noise via `opterr = 0`

## v0.2 (roadmap)

- Typed Kotlin API: `data class Config`, `sealed class Strategy`, `enum class Detector/AutoMode`, builder DSL covering byedpi's full ~40-option surface
- Replaces the argv-style `nativeStart` with a typed entrypoint; `nativeStart` may be demoted to `internal` or kept as a power-user escape hatch
