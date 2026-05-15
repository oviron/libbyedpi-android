# libbyedpi-android

Standalone Android library that embeds **ByeDPI** ([hufrea/byedpi](https://github.com/hufrea/byedpi)) as a `.so` per ABI. Suitable for any Android app that needs DPI-bypass functionality without bundling the full ByeByeDPI APK.

**First public MIT-licensed standalone Android byedpi binding.** The upstream byedpi project itself ships only Linux/Windows/macOS binaries — no Android library. Existing Android consumers ([ByeByeDPI](https://github.com/dovecoteescapee/byedpiandroid), [ByeByeDPI fork](https://github.com/romanvht/ByeByeDPI)) are full apps with their own JNI shim, not reusable libraries. This repo fills that gap.

## What's in each release

Each tag attaches three files to its GitHub Release:

| File | Description |
|---|---|
| `libbyedpi-android-v0.1.0.aar` | Android library, all three ABIs bundled (arm64-v8a + armeabi-v7a + x86_64) plus the Kotlin stub |
| `libbyedpi-android-v0.1.0.aar.sha256` | SHA-256 checksum |
| `libbyedpi-android-v0.1.0.aar.asc` | GPG detached signature |

Verify before consuming:

```sh
sha256sum -c libbyedpi-android-v0.1.0.aar.sha256

# One-time: import maintainer public key from this repo
gpg --import oviron-signing.pub.asc
gpg --verify libbyedpi-android-v0.1.0.aar.asc libbyedpi-android-v0.1.0.aar
# Expected: Good signature from "oviron <awdonkin@gmail.com>"
```

Public key fingerprint: `1139 C91B 6525 883E 6783 DCF0 4A94 DA48 8A4C 5033`. Cross-check this fingerprint against the maintainer's GitHub profile (https://github.com/oviron) or keys.openpgp.org before trusting the key.

## Requirements

- **minSdk 21** (Android 5.0+) — the `.so` files are built with `-DANDROID_PLATFORM=android-21`. Older devices will fail at `System.loadLibrary` time.
- **AGP 8.5.1+** — needed so the host APK is itself 16 KB page-aligned on Android 15+ devices. The `.so` files in this library are already built 16 KB-aligned by NDK 28.

## Integration

### Gradle (file dependency)

Download the `.aar` once during your build and reference it as a file dependency. Example:

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
}
```

The `.aar` already includes the Kotlin stub `io.github.oviron.libbyedpi.ByeDpi` with `System.loadLibrary("byedpi")` wired up — no additional source code on your side is needed.

### Usage from Kotlin

```kotlin
import io.github.oviron.libbyedpi.ByeDpi

// On a background thread (the call blocks until the proxy stops):
val exit = ByeDpi.nativeStart(arrayOf(
    "byedpi", "-i", "127.0.0.1", "-p", "1080", "--auto=torst",
))

// From any thread, to signal graceful shutdown:
ByeDpi.nativeStop()

// Escape hatch — when the proxy is stuck and graceful stop did not
// unblock the running nativeStart, force-close the listening socket:
ByeDpi.nativeForceClose()
```

The argv-style API mirrors the byedpi CLI 1:1. A typed Kotlin `Config` + `sealed class Strategy` DSL is on the v0.2 roadmap; see the "API maturity" section below.

### End-to-end usage

A minimal lifecycle for a foreground service that hosts the proxy:

```kotlin
class ByeDpiService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        job = scope.launch {
            val exit = ByeDpi.nativeStart(arrayOf(
                "byedpi", "-i", "127.0.0.1", "-p", "1080", "--auto=torst",
            ))
            if (exit != 0) Log.w(TAG, "byedpi exited with code $exit")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ByeDpi.nativeStop()
        runBlocking { job?.join() }
        scope.cancel()
        super.onDestroy()
    }
}
```

Other components in your app then route traffic through the SOCKS5 endpoint at `127.0.0.1:1080` (for example by configuring a `Proxy` on `OkHttpClient.Builder` or by feeding it into a VPN service `tun` socket protector).

## CLI arguments

`nativeStart(args)` accepts the same CLI as upstream byedpi binary. First element is conventionally `"byedpi"` (argv[0] / program name). See [hufrea/byedpi README](https://github.com/hufrea/byedpi#readme) for the full option list.

Common arguments:

| Flag | Effect |
|---|---|
| `-i 127.0.0.1` | Bind address |
| `-p 1080` | SOCKS5 listen port |
| `--auto=torst` | Auto-detect best DPI-bypass strategy (default) |
| `--no-domain` | Disable hostname check |
| `--hosts FILE` | Whitelist mode: only bypass DPI for these hosts |
| `-d` | Increase debug verbosity |

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `nativeStart` returns immediately with non-zero | Bind port already in use (another proxy app, or previous instance still listening) | Pick a different `-p` value, or call `nativeStop` and wait for the previous `nativeStart` to return before re-starting |
| `nativeStart` returns -1 instantly | Another call to `nativeStart` is already in flight on the same process | Wait for the running call to return; only one proxy per process is supported |
| ANR on main thread | `nativeStart` called from main thread | Always call from `Dispatchers.IO` or another background thread — `nativeStart` blocks until `nativeStop` is signalled |
| Crash on `System.loadLibrary` | Missing ABI for device, or `minSdk < 21` | Verify the consumer app ships all three ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`) and sets `minSdk = 21` |
| Traffic does not bypass DPI | Wrong byedpi strategy for the operator | Try `--auto=torst` (default), then experiment with explicit flags from the [byedpi README](https://github.com/hufrea/byedpi#readme) |
| App lacks network access while proxy runs | App is missing `<uses-permission android:name="android.permission.INTERNET"/>` | Add it to your manifest |

## License

This wrapper code (`src/cpp/native-lib.c`, `src/cpp/main.h`, `src/cpp/CMakeLists.txt`) is MIT-licensed — see `LICENSE`.

The bundled ByeDPI source at `src/cpp/byedpi/` is also MIT-licensed by hufrea — see `src/cpp/byedpi/LICENSE`.

Downstream apps can be **any** license, including proprietary. There is no copyleft obligation propagated through this library.

## API maturity

v0.1.0 is a **low-level JNI binding**. The `nativeStart` method takes a raw argv-style `Array<String>` matching byedpi's CLI surface 1:1. This is intentional — it keeps the wrapper thin, predictable, and trivially in sync with upstream byedpi. The cost is consumer-side boilerplate: you build the argv yourself from typed values.

A higher-level typed Kotlin API (data-class `Config`, `sealed class Strategy`, builder DSL, `.aar` packaging) is planned for **v0.2**. v0.1.0 is shipped first so that FlClash and other early adopters can integrate today and inform what the typed API should look like based on real usage.

## Compatibility note

While the library version is below 1.0, treat the **whole library as API-unstable**: bundled byedpi version, CLI flag semantics, and library packaging may change between minor releases. The two JNI method signatures (`nativeStart(Array<String>): Int`, `nativeStop(): Int`) are not expected to change during 0.x, but no guarantee is made. **Pin an exact version in your build** (`v0.1.0`, not `v0.1.+`) and re-read the CHANGELOG before bumping.

Semver guarantees begin at v1.0.

## Building from source

```sh
git clone https://github.com/oviron/libbyedpi-android.git
cd libbyedpi-android
git submodule update --init --recursive

# Build one ABI (requires Android NDK 28+):
cmake -B build-arm64 \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-21 \
  -DCMAKE_BUILD_TYPE=Release \
  -S src/cpp
cmake --build build-arm64
# Output: build-arm64/libbyedpi.so
```

See `MAINTENANCE.md` for the full release procedure.

## Reporting issues

GitHub Issues for bugs and feature requests. For security disclosures see `SECURITY.md`.
