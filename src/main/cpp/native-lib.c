#include <string.h>

#include <jni.h>
#include <getopt.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>

#include "byedpi/error.h"
#include "main.h"

extern int server_fd;
static _Atomic int g_proxy_running = 0;

struct params default_params = {
        .await_int = 10,
        .ipv6 = 1,
        .resolve = 1,
        .udp = 1,
        .max_open = 512,
        .bfsize = 16384,
        .baddr = {
            .in6 = { .sin6_family = AF_INET6 }
        },
        .laddr = {
            .in = { .sin_family = AF_INET }
        },
        .debug = 0
};

void reset_params(void) {
    clear_params(NULL, NULL);
    params = default_params;
}

JNIEXPORT jint JNICALL
Java_io_github_oviron_libbyedpi_ByeDpi_nativeStart(JNIEnv *env, __attribute__((unused)) jclass clazz, jobjectArray args) {
    int expected = 0;
    if (!atomic_compare_exchange_strong(&g_proxy_running, &expected, 1)) {
        LOG(LOG_S, "proxy already running");
        return -1;
    }

    server_fd = -1;

    int argc = (*env)->GetArrayLength(env, args);
    char **argv = calloc(argc, sizeof(char *));

    if (!argv) {
        LOG(LOG_S, "failed to allocate memory for argv");
        atomic_store(&g_proxy_running, 0);
        return -1;
    }

    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);

        if (!arg) {
            argv[i] = NULL;
            continue;
        }

        const char *arg_str = (*env)->GetStringUTFChars(env, arg, 0);
        argv[i] = arg_str ? strdup(arg_str) : NULL;

        if (arg_str) (*env)->ReleaseStringUTFChars(env, arg, arg_str);

        (*env)->DeleteLocalRef(env, arg);

        if (arg_str && !argv[i]) {
            LOG(LOG_S, "strdup OOM at argv[%d]; aborting start", i);
            for (int j = 0; j < i; j++) free(argv[j]);
            free(argv);
            atomic_store(&g_proxy_running, 0);
            return -1;
        }
    }

    LOG(LOG_S, "starting proxy with %d args", argc);
    reset_params();
    optind = 1;
    opterr = 0;

    int result = main(argc, argv);

    LOG(LOG_S, "proxy return code %d", result);
    atomic_store(&g_proxy_running, 0);

    for (int i = 0; i < argc; i++) free(argv[i]);
    free(argv);

    return result;
}

JNIEXPORT jint JNICALL
Java_io_github_oviron_libbyedpi_ByeDpi_nativeStop(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jclass clazz) {
    LOG(LOG_S, "send shutdown to proxy");

    if (!atomic_load(&g_proxy_running)) {
        LOG(LOG_S, "proxy is not running");
        return -1;
    }

    int fd = server_fd;
    if (fd < 0) {
        LOG(LOG_S, "proxy not yet ready, server_fd uninitialized");
        return -1;
    }
    shutdown(fd, SHUT_RDWR);
    return 0;
}

JNIEXPORT jint JNICALL
Java_io_github_oviron_libbyedpi_ByeDpi_nativeForceClose(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jclass clazz) {
    LOG(LOG_S, "force-closing proxy server socket");

    int fd = server_fd;
    if (fd < 0) {
        LOG(LOG_S, "proxy not ready, nothing to close");
        return -1;
    }
    server_fd = -1;
    if (close(fd) == -1) {
        LOG(LOG_S, "close(server_fd=%d) failed: errno=%d", fd, errno);
        return -1;
    }
    return 0;
}

JNIEXPORT jboolean JNICALL
Java_io_github_oviron_libbyedpi_ByeDpi_nativeIsListening(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jclass clazz) {
    return (atomic_load(&g_proxy_running) && server_fd >= 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_io_github_oviron_libbyedpi_ByeDpi_nativeBridgeABI(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jclass clazz) {
    return 1;
}
