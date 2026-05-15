package io.github.oviron.libbyedpi

object ByeDpi {
    init { System.loadLibrary("byedpi") }

    @JvmStatic
    external fun nativeStart(args: Array<String>): Int

    @JvmStatic
    external fun nativeStop(): Int

    @JvmStatic
    external fun nativeForceClose(): Int
}
