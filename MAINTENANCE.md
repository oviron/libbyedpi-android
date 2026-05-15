# Maintenance procedure

Bus-factor mitigation document. If the primary maintainer (oviron) goes silent for 60+ days, fork this repo and continue using these instructions.

## Build environment

- **Android Gradle Plugin**: 8.12.2 (pinned in `build.gradle.kts`)
- **Kotlin**: 2.2.10
- **Gradle**: 8.13 (via wrapper, no system install needed)
- **JDK**: 17+ (Temurin recommended)
- **Android NDK**: 28.0.13004108 (pinned via `ndkVersion` in `build.gradle.kts`)
- **CMake**: 3.22.1+
- **Android SDK**: compileSdk 36, minSdk 21
- **OS**: ubuntu-latest in CI; locally Linux/macOS both fine

## Source layout

- `src/main/cpp/native-lib.c` — our JNI wrapper (~100 LOC, MIT-licensed)
- `src/main/cpp/main.h` — declarations from upstream byedpi
- `src/main/cpp/CMakeLists.txt` — native build script
- `src/main/cpp/byedpi/` — git submodule pinned at `hufrea/byedpi` SHA
- `src/main/kotlin/io/github/oviron/libbyedpi/ByeDpi.kt` — Kotlin stub bundled into the .aar
- `src/main/AndroidManifest.xml` — empty library manifest
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` — AGP library project config

## How a release is made

Tags matching `v*` trigger the `release.yml` workflow:

1. Checks out source with `submodules: recursive`
2. Builds the `.aar` via `./gradlew :assembleRelease`; AGP compiles `libbyedpi.so` for all three ABIs and bundles them with the Kotlin stub
3. Renames the artifact to `libbyedpi-android-<tag>.aar` and generates SHA-256
4. GPG-signs the `.aar` with maintainer key (detached, armored) — public key in `oviron-signing.pub.asc`
5. Creates a GitHub Release with 3 files attached (`.aar` + `.aar.sha256` + `.aar.asc`)

Cut a release manually:

```sh
# Verify main is green
gh run list --limit 3
# Update CHANGELOG.md (mandatory)
# Bump version in this MAINTENANCE.md if conventions change
git tag v0.X.Y
git push origin v0.X.Y
gh run watch
```

## How upstream bumps are handled

When `hufrea/byedpi` releases a new tag:

1. Update the submodule:
   ```sh
   cd src/cpp/byedpi
   git fetch origin
   git checkout <new-tag-or-commit>
   cd ../../..
   git add src/cpp/byedpi
   git commit -m "Bump byedpi to <version>"
   ```
2. Verify our wrapper still compiles. If `params`, `clear_params`, or `main` signatures changed upstream → patch `native-lib.c` accordingly. Bump our `0.X.Y` minor.
3. Tag a new release.

ByeDPI changes its public C API rarely (~1-2 times per year). The wrapper is small (~94 LOC) — drift fixes are usually one or two lines.

## GPG signing key

The maintainer's GPG key is RSA 4096 `4A94DA488A4C5033`, fingerprint `1139C91B6525883E6783DCF04A94DA488A4C5033`. Public key committed at repo root as `oviron-signing.pub.asc`. Same key is registered on github.com/oviron for commit verification.

CI uses the same key via GitHub Actions secrets `GPG_PRIVATE_KEY` (armored secret key) and `GPG_PASSPHRASE`. To rotate:

1. Generate new GPG key, e.g. `gpg --full-generate-key`
2. Export armored private: `gpg --armor --export-secret-keys <NEW_KEY_ID> | gh secret set GPG_PRIVATE_KEY --repo oviron/libbyedpi-android`
3. Set new passphrase: `gh secret set GPG_PASSPHRASE --repo oviron/libbyedpi-android`
4. Replace `oviron-signing.pub.asc` with the new public key
5. Announce in next release notes with cross-signature from old key

After cutting any release, also push the maintainer public key to a keyserver so consumers can fetch independently of the repo:

```sh
gpg --keyserver keys.openpgp.org --send-keys 4A94DA488A4C5033
```

## Reproducibility

A given source tree + the same pinned AGP/Kotlin/NDK toolchain + the same byedpi submodule SHA produces byte-identical artifacts across runs. `-Wl,--build-id=sha1` in `src/main/cpp/CMakeLists.txt` makes the BuildID a content hash, AGP applies zip epoch normalization to the `.aar`, and LLD does not embed linker timestamps. Stronger reproducibility (across NDK patch revisions or different host platforms) is not claimed.

## Mirror

GitHub Releases is currently the only distribution channel. Mirror to a non-GitHub host (Codeberg / IPFS / other) is **deferred until concrete need**. If GitHub deletes this repo:

- All consumers downloading via fixed URL lose access
- The git submodule at `hufrea/byedpi` is upstream and unaffected
- Fork: any third party can clone this repo via local checkout, push to alternative host, continue releases — the build is reproducible per the section above

## Contact

- Primary maintainer: oviron (@oviron on GitHub)
- Security disclosures: see `SECURITY.md`
- Issues / questions: GitHub Issues
