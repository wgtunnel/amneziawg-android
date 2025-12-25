#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <unistd.h>

#define LOG_TAG "AmneziaWG/BypassSocket"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct go_string { const char *str; long n; };
extern int awgStartProxy(struct go_string ifname, struct go_string settings, struct go_string uapipath, int bypass);
extern void awgStopProxy();
extern char *awgGetProxyConfig(int handle);
extern int awgUpdateProxyTunnelPeers(int handle, struct go_string settings);

static JavaVM *g_jvm = NULL;
static jobject g_protector = NULL;
static jmethodID g_protectMethod = NULL;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    LOGD("JNI_OnLoad: Cached g_jvm=%p", g_jvm);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    if (g_protector != NULL) {
        JNIEnv *env;
        (*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6);
        (*env)->DeleteGlobalRef(env, g_protector);
        g_protector = NULL;
        g_protectMethod = NULL;
    }
    g_jvm = NULL;
    LOGD("JNI_OnUnload: Cleared globals");
}

JNIEXPORT jint JNICALL Java_org_amnezia_awg_ProxyGoBackend_awgStartProxy(JNIEnv *env, jclass c, jstring ifname, jstring settings, jstring uapipath, jint bypass)
{
    const char *ifname_str = (*env)->GetStringUTFChars(env, ifname, 0);
    size_t ifname_len = (*env)->GetStringUTFLength(env, ifname);
    const char *settings_str = (*env)->GetStringUTFChars(env, settings, 0);
    size_t settings_len = (*env)->GetStringUTFLength(env, settings);
    const char *uapipath_str = (*env)->GetStringUTFChars(env, uapipath, 0);
        size_t uapipath_len = (*env)->GetStringUTFLength(env, uapipath);
    int ret = awgStartProxy((struct go_string){
        .str = ifname_str,
        .n = ifname_len
    }, (struct go_string){
        .str = settings_str,
        .n = settings_len
    }, (struct go_string){
            .str = uapipath_str,
            .n = uapipath_len
        },bypass);
    (*env)->ReleaseStringUTFChars(env, ifname, ifname_str);
    (*env)->ReleaseStringUTFChars(env, settings, settings_str);
    (*env)->ReleaseStringUTFChars(env, uapipath, uapipath_str);
    return ret;
}

JNIEXPORT void JNICALL Java_org_amnezia_awg_ProxyGoBackend_awgStopProxy(JNIEnv *env, jclass c)
{
    awgStopProxy();
}

JNIEXPORT jstring JNICALL Java_org_amnezia_awg_ProxyGoBackend_awgGetProxyConfig(JNIEnv *env, jclass c, jint handle)
{
    jstring ret;
    char *config = awgGetProxyConfig(handle);
    if (!config)
        return NULL;
    ret = (*env)->NewStringUTF(env, config);
    free(config);
    return ret;
}

JNIEXPORT void JNICALL Java_org_amnezia_awg_ProxyGoBackend_awgSetSocketProtector(JNIEnv *env, jclass c, jobject protector) {
    (*env)->GetJavaVM(env, &g_jvm);
    if (g_jvm == NULL) {
        LOGE("awgSetSocketProtector: g_jvm still NULL post-GetJavaVM");
        return;
    }
    if (g_protector != NULL) {
        (*env)->DeleteGlobalRef(env, g_protector);
    }
    g_protector = (*env)->NewGlobalRef(env, protector);
    jclass protectorClass = (*env)->GetObjectClass(env, protector);
    if (protectorClass == NULL) {
        LOGE("Failed to get protectorClass");
        return;
    }
    g_protectMethod = (*env)->GetMethodID(env, protectorClass, "bypass", "(I)I");
    if (g_protectMethod == NULL) {
        LOGE("Failed to get bypass method ID");
    }
    LOGD("awgSetSocketProtector: Refreshed g_protector=%p, method=%p", g_protector, g_protectMethod);
}

JNIEXPORT void JNICALL Java_org_amnezia_awg_ProxyGoBackend_awgResetJNIGlobals(JNIEnv *env, jclass c) {
    if (g_jvm != NULL && g_protector != NULL) {
        JNIEnv *tmp_env;
        (*g_jvm)->GetEnv(g_jvm, (void**)&tmp_env, JNI_VERSION_1_6);
        (*tmp_env)->DeleteGlobalRef(tmp_env, g_protector);
    }
    g_protector = NULL;
    g_protectMethod = NULL;
    LOGD("awgResetJNIGlobals: Cleared protector and method");
}

int bypass_socket(int fd) {
    if (fd < 0) {
        LOGE("Invalid FD passed to bypass_socket: %d", fd);
        return 0;  // Fail early on bad FD
    }

    JNIEnv *env = NULL;
    jboolean attached = JNI_FALSE;
    jint rs = -1;

    LOGD("bypass_socket called with FD: %d", fd);

    if (g_jvm == NULL) {
        LOGE("g_jvm is NULL - not initialized in JNI_OnLoad?");
        return 0;
    }

    rs = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6);
    LOGD("GetEnv returned: %d (env=%p)", rs, env);

    if (rs == JNI_EDETACHED) {
        LOGD("Thread detached, attempting AttachCurrentThread");
        int retries = 3;
        while (retries-- > 0 && (*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            usleep(10000); // 10ms backoff
        }
        if (retries < 0) {
            LOGE("AttachCurrentThread failed after retries");
            return 0;
        }
        attached = JNI_TRUE;
        LOGD("Attached successfully, env=%p", env);
    } else if (rs != JNI_OK) {
        LOGE("GetEnv failed with %d (not OK or detached)", rs);
        return 0;
    } else {
        LOGD("Thread already attached, env=%p", env);
    }

    if (env == NULL) {
        LOGE("Env is NULL after attachment/GetEnv");
        if (attached) {
            (*g_jvm)->DetachCurrentThread(g_jvm);
        }
        return 0;
    }

    if (g_protector == NULL) {
        LOGE("g_protector is NULL - VpnService ref not set?");
        if (attached) {
            (*g_jvm)->DetachCurrentThread(g_jvm);
        }
        return 0;
    }
    LOGD("g_protector ref valid: %p", g_protector);

    if (g_protectMethod == NULL) {
        LOGE("g_protectMethod is NULL - method ID not cached?");
        if (attached) {
            (*g_jvm)->DetachCurrentThread(g_jvm);
        }
        return 0;
    }
    LOGD("g_protectMethod valid");

    // Clear any pending exceptions before call
    if ((*env)->ExceptionCheck(env)) {
        LOGE("Pending exception before CallIntMethod - clearing");
        (*env)->ExceptionClear(env);
    }

    int result = (*env)->CallIntMethod(env, g_protector, g_protectMethod, fd);
    LOGD("CallIntMethod returned: %d", result);

    // Check for exceptions after call
    if ((*env)->ExceptionCheck(env)) {
        LOGE("Exception thrown from CallIntMethod - describing");
        (*env)->ExceptionDescribe(env);  // Logs the exception to logcat
        (*env)->ExceptionClear(env);
        result = 0;  // Fail on exception
    }

    if (attached) {
        (*g_jvm)->DetachCurrentThread(g_jvm);
        LOGD("Detached thread");
    }

    LOGD("bypass_socket returning: %d for FD %d", result, fd);
    return result;
}

JNIEXPORT jint JNICALL Java_org_amnezia_awg_ProxyGoBackend_awgUpdateProxyTunnelPeers(JNIEnv *env, jclass c, jint handle, jstring settings)
{
    const char *settings_str = (*env)->GetStringUTFChars(env, settings, 0);
    size_t settings_len = (*env)->GetStringUTFLength(env, settings);
    int ret = awgUpdateProxyTunnelPeers(handle, (struct go_string){
        .str = settings_str,
        .n = settings_len
    });
    (*env)->ReleaseStringUTFChars(env, settings, settings_str);
    return ret;
}

