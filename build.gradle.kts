plugins {
    id("com.android.library") version "8.12.2"
    id("org.jetbrains.kotlin.android") version "2.2.10"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}

android {
    namespace = "io.github.oviron.libbyedpi"
    compileSdk = 36
    ndkVersion = "28.0.13004108"

    defaultConfig {
        minSdk = 21
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                // Reproducible-build flags: -ffile-prefix-map strips build host
                // absolute paths from DWARF, -fdebug-prefix-map normalises
                // .debug_str. Combined with deterministic ordering in linker
                // (-Wl,--build-id=none) this yields byte-identical .so output
                // from any host checkout directory.
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DCMAKE_C_FLAGS_RELEASE=-O3 -DNDEBUG -ffile-prefix-map=${rootProject.projectDir}=. -fdebug-prefix-map=${rootProject.projectDir}=.",
                    "-DCMAKE_SHARED_LINKER_FLAGS_RELEASE=-Wl,--build-id=none",
                )
            }
        }
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs.useLegacyPackaging = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

val validateJniKeep by tasks.registering(Exec::class) {
    workingDir = projectDir
    commandLine("sh", "scripts/validate-jni-keep.sh")
}

afterEvaluate {
    tasks.named("preBuild") { dependsOn(validateJniKeep) }
}
