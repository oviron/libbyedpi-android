# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Until v1.0 the public API is considered unstable.

## [Unreleased]

## [0.1.1] — 2026-06-02

### Added
- `metadata.json` release asset: machine-readable manifest declaring the
  bundled core (`byedpi` as a `git describe` of the pinned submodule),
  `bridgeABI`, ABIs, and AAR SHA-256. The bundled core version is now also in
  the release title and the README version matrix, so consumers (and the
  planned in-app version picker) can see which core a wrapper release ships
  before downloading.

### Unchanged
- byedpi core stays at submodule `v0.17.3-38-gba53229` (upstream master HEAD);
  `bridgeABI` and the Kotlin facade are unchanged. This release adds only the
  versioning/manifest contract.

## [0.1.0] — 2026-05-17

Initial public release. First public MIT-licensed standalone Android
library for ByeDPI.

### Added
- JNI bridge to [hufrea/byedpi](https://github.com/hufrea/byedpi),
  bundled at commit
  [`ba53229`](https://github.com/hufrea/byedpi/tree/ba532298de7b28cfe854aea83d061369d13ca290)
  (v0.17.3-38).
- Native shared object `libbyedpi.so` per ABI (arm64-v8a, armeabi-v7a,
  x86_64), built with `-Wl,--build-id=sha1` for reproducibility.
- Kotlin lifecycle facade `io.github.oviron.libbyedpi.ByeDpi`:
  `load(nativeLibDir)` explicit load step via `System.load`;
  `suspend fun start(ByeDpiConfig)` blocks until the listener socket
  is bound or fails (5 s startup timeout); `suspend fun stop()`
  bounded by 3 s `workerJob.join`, with `nativeForceClose` fallback;
  `suspend fun restart(newConfig)` atomic stop+start;
  `val state: StateFlow<State>` with sealed `State` hierarchy
  (`Idle`, `Starting`, `Running`, `Stopping`, `Failed`);
  `forceClose()` escape hatch.
- `ByeDpiConfig(val args: List<String>)` opaque argv wrapper. The
  library does not interpret CLI flags or bake in strategy presets;
  the consumer composes whatever byedpi argv it needs.
- `ByeDpi.bridgeABI(): Int` + `EXPECTED_BRIDGE_ABI` constant. `load()`
  runs the native check and fails fast on mismatch.
- `ByeDpi.isLoaded()` / `ByeDpi.assertReady()` for load-state checks;
  every facade method calls `assertReady()`.
- `consumer-rules.pro` keeps the `ByeDpi` facade, the nested `State`
  hierarchy, `ByeDpiConfig`, and a native-method wildcard. R8 in the
  consumer APK cannot strip JNI-referenced symbols.
- `scripts/validate-jni-keep.sh` diffs JNI lookups in C sources
  against `-keep` rules. Wired into Gradle `preBuild`.
- Release artifacts: `.aar` + `.aar.sha256` + `.aar.asc` (detached
  GPG signature, key fingerprint
  `1139 C91B 6525 883E 6783 DCF0 4A94 DA48 8A4C 5033`).

### Notes
- Pinned toolchain: AGP `8.12.2`, Kotlin `2.2.10`, NDK `28.0.13004108`.
- `nativeStart` calls upstream byedpi's `main()` and is blocking; the
  facade runs it on `Dispatchers.IO` internally.
- `nativeForceClose` clears `server_fd` after `close()` so
  `nativeIsListening()` does not report a closed socket as bound.
