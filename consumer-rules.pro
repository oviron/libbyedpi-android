# Native JNI bridge — JNI symbols Java_io_github_oviron_libbyedpi_ByeDpi_*
# bind through the standard mangling, so the host class must survive R8.
# Nested sealed `State` and the `ByeDpiConfig` data class are observed by
# consumers via runtime reflection (`::class.simpleName`) and kept too.
# Verified by scripts/validate-jni-keep.sh on each release.

-keep class io.github.oviron.libbyedpi.ByeDpi { *; }
-keep class io.github.oviron.libbyedpi.ByeDpi$Companion { *; }
-keep class io.github.oviron.libbyedpi.ByeDpi$* { *; }
-keep class io.github.oviron.libbyedpi.ByeDpiConfig { *; }

-keepclasseswithmembernames class io.github.oviron.libbyedpi.** {
    native <methods>;
}
