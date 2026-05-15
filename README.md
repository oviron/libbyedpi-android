# libbyedpi-android

Standalone Android library that embeds **ByeDPI** ([hufrea/byedpi](https://github.com/hufrea/byedpi)) as a `.so` per ABI, plus a Kotlin lifecycle facade for hosting the proxy in any Android app.

**First public MIT-licensed standalone Android byedpi binding.** The upstream byedpi project itself ships only Linux/Windows/macOS binaries — no Android library. Existing Android consumers ([ByeByeDPI](https://github.com/dovecoteescapee/byedpiandroid), [ByeByeDPI fork](https://github.com/romanvht/ByeByeDPI)) are full apps with their own JNI shim, not reusable libraries. This repo fills that gap.

## What's in each release

Each tag attaches three files to its GitHub Release:

| File | Description |
|---|---|
| `libbyedpi-android-vX.Y.Z.aar` | Android library, all three ABIs bundled (arm64-v8a + armeabi-v7a + x86_64) plus the Kotlin facade |
| `libbyedpi-android-vX.Y.Z.aar.sha256` | SHA-256 checksum |
| `libbyedpi-android-vX.Y.Z.aar.asc` | GPG detached signature |

Verify before consuming:

```sh
sha256sum -c libbyedpi-android-vX.Y.Z.aar.sha256

# One-time: import maintainer public key from this repo
gpg --import oviron-signing.pub.asc
gpg --verify libbyedpi-android-vX.Y.Z.aar.asc libbyedpi-android-vX.Y.Z.aar
# Expected: Good signature from "oviron <awdonkin@gmail.com>"
```

Public key fingerprint: `1139 C91B 6525 883E 6783 DCF0 4A94 DA48 8A4C 5033`. Cross-check this fingerprint against the maintainer's GitHub profile (https://github.com/oviron) or keys.openpgp.org before trusting the key.

## What's inside the `.aar`

- `libbyedpi.so` per ABI: byedpi compiled with `ANDROID_APP` shim. Exports four JNI entry points (`Java_io_github_oviron_libbyedpi_ByeDpi_native*`).
- `classes.jar`: `ByeDpi` (lifecycle facade with `StateFlow`), `ByeDpiConfig` (opaque argv vector).

The Kotlin facade depends on `kotlinx-coroutines-android` (the consumer's classpath must include it; it is declared as `implementation` here, so AGP will pick it up transitively).

## Requirements

- **minSdk 21** (Android 5.0+). The `.so` files are built with `-DANDROID_PLATFORM=android-21`. Older devices fail at load time.
- **AGP 8.5.1+**. The host APK needs to be 16 KB page-aligned on Android 15+; this library's `.so` files are already 16 KB-aligned by NDK 28.

## Integration

### Gradle (file dependency)

Download the `.aar` once during your build and reference it as a file dependency:

```kotlin
// app/build.gradle.kts
val byedpiVersion = "0.1.0"
val byedpiAar = layout.buildDirectory.file("libs/libbyedpi-android-v$byedpiVersion.aar")

val downloadByedpi = tasks.register("downloadByedpi") {
    inputs.property("byedpiVersion", byedpiVersion)
    outputs.file(byedpiAar)
    doLast {
        val target = byedpiAar.get().asFile
        target.parentFile.mkdirs()
        val url = "https://github.com/oviron/libbyedpi-android/releases/download/v$byedpiVersion/libbyedpi-android-v$byedpiVersion.aar"
        target.outputStream().use { out ->
            uri(url).toURL().openStream().use { it.copyTo(out) }
        }
    }
}

dependencies {
    implementation(files(byedpiAar).builtBy(downloadByedpi))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
```

A safer variant verifies SHA-256 + GPG before trusting the download; see `MAINTENANCE.md` § "Verifying releases at build time".

### Usage from Kotlin

`ByeDpi` does not load its native library automatically. The consumer chooses *when* and *from where* to load, which is the seam used for runtime-pluggable versions (download a newer `.aar`, extract its `jni/<abi>/` dir, and pass that path).

```kotlin
import io.github.oviron.libbyedpi.ByeDpi
import io.github.oviron.libbyedpi.ByeDpiConfig

// 1. Load once per process. Default: the directory Android put the .aar's
//    jniLibs into when it built your APK.
ByeDpi.load(context.applicationInfo.nativeLibraryDir)

// 2. Start the proxy. start() suspends until the listener is bound, or
//    throws if startup fails.
lifecycleScope.launch {
    ByeDpi.start(ByeDpiConfig(listOf(
        "-i", "127.0.0.1",
        "-p", "1080",
        "--auto=torst",
    )))

    // ByeDpi.state.value is now State.Running(config).

    // 3. Run your app; the SOCKS5 listener is on 127.0.0.1:1080.

    // 4. Swap config without manual stop/start:
    ByeDpi.restart(ByeDpiConfig(listOf("-i", "127.0.0.1", "-p", "1080", "--auto=other")))

    // 5. Shutdown.
    ByeDpi.stop()
}
```

### Observing state

```kotlin
ByeDpi.state
    .onEach { state ->
        when (state) {
            is ByeDpi.State.Idle -> updateUi("stopped")
            is ByeDpi.State.Starting -> updateUi("starting...")
            is ByeDpi.State.Running -> updateUi("listening on 127.0.0.1:1080")
            is ByeDpi.State.Stopping -> updateUi("stopping...")
            is ByeDpi.State.Failed -> updateUi("crashed: exit=${state.exitCode}")
        }
    }
    .launchIn(lifecycleScope)
```

### Threading

- `start` / `stop` / `restart` are `suspend` and safe to call from any coroutine context. The blocking byedpi `main()` is launched on `Dispatchers.IO` internally; consumers do not manage the thread.
- `state` is a `StateFlow` and can be collected on any dispatcher.
- `ByeDpi.load` is the only call that must run before any other facade method. Every facade method invokes `assertReady()` internally and throws `IllegalStateException` if `load` failed or was never called.

### Force-close escape hatch

If `stop` cannot make progress (the proxy is stuck on a system call), `ByeDpi.forceClose()` shuts the listening socket directly. Use sparingly; pair with a subsequent `stop` to await the worker.

## CLI arguments

`ByeDpiConfig.args` accepts the upstream byedpi CLI 1:1. The library prepends a synthetic argv[0], so your list should start with the first real flag. See [hufrea/byedpi README](https://github.com/hufrea/byedpi#readme) for the full option list.

Common flags:

| Flag | Effect |
|---|---|
| `-i 127.0.0.1` | Bind address |
| `-p 1080` | SOCKS5 listen port |
| `--auto=torst` | Auto-detect best DPI-bypass strategy |
| `--no-domain` | Disable hostname check |
| `--hosts FILE` | Whitelist mode: only bypass DPI for these hosts |
| `-d` | Increase debug verbosity |

The library does not interpret flag semantics, validate combinations, or fill in defaults. Consumer composes whatever byedpi needs.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `start()` throws "did not bind within 5000ms" | Bind port already in use, or wrong flag combo | Pick a different `-p`, check byedpi stderr in logcat |
| `start()` throws "invalid state Running/Starting" | A previous start is still active | Call `stop()` first, or use `restart(newConfig)` |
| `IllegalStateException: ByeDpi not loaded` | `load(nativeLibDir)` not called | Call it once at app startup |
| Traffic does not bypass DPI | Wrong byedpi strategy for the operator | Try `--auto=torst` (default), then experiment with explicit flags from the [byedpi README](https://github.com/hufrea/byedpi#readme) |
| App lacks network access while proxy runs | App is missing `<uses-permission android:name="android.permission.INTERNET"/>` | Add it to your manifest |

## License

This wrapper code (`src/main/cpp/native-lib.c`, `src/main/cpp/main.h`, `src/main/cpp/CMakeLists.txt`, Kotlin facade) is MIT-licensed; see `LICENSE`.

The bundled ByeDPI source at `src/main/cpp/byedpi/` is also MIT-licensed by hufrea; see `src/main/cpp/byedpi/LICENSE`.

Downstream apps can be **any** license, including proprietary. There is no copyleft obligation propagated through this library.

## Compatibility note

While the library version is below 1.0, treat the **whole library as API-unstable**: bundled byedpi version, CLI flag semantics, and Kotlin facade may change between minor releases. **Pin an exact version in your build** (`v0.1.0`, not `v0.+`) and re-read the CHANGELOG before bumping.

Semver guarantees begin at v1.0.

## Building from source

```sh
git clone https://github.com/oviron/libbyedpi-android.git
cd libbyedpi-android
git submodule update --init --recursive

# Build the .aar (NDK 28+ required):
./gradlew :assembleRelease
# Output: build/outputs/aar/libbyedpi-android-release.aar
```

See `MAINTENANCE.md` for the full release procedure.

## Reporting issues

GitHub Issues for bugs and feature requests. For security disclosures see `SECURITY.md`.
