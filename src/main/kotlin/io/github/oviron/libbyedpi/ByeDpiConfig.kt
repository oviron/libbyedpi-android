package io.github.oviron.libbyedpi

/** Opaque byedpi argv (no argv[0]); see README for CLI surface. */
data class ByeDpiConfig(val args: List<String>) {
    init {
        require(args.isNotEmpty()) { "ByeDpiConfig.args must not be empty" }
    }
}
