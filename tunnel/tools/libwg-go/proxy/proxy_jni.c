#include <jni.h>
#include <stdlib.h>
#include <string.h>

struct go_string { const char *str; long n; };
extern int awgStartProxy(struct go_string ifname, struct go_string settings, struct go_string pkgname, int bypass);
extern void awgStopProxy();
extern char *awgGetProxyConfig(int handle);

static JavaVM *g_jvm = NULL;
static jobject g_protector = NULL;
static jmethodID g_protectMethod = NULL;

JNIEXPORT jint JNICALL Java_org_amnezia_awg_ProxyGoBackend_awgStartProxy(JNIEnv *env, jclass c, jstring ifname, jstring settings, jstring pkgname, jint bypass) {
    const char *ifname_str = (*env)->GetStringUTFChars(env, ifname, 0);
    	size_t ifname_len = (*env)->GetStringUTFLength(env, ifname);
    	const char *settings_str = (*env)->GetStringUTFChars(env, settings, 0);
    	size_t settings_len = (*env)->GetStringUTFLength(env, settings);
    	const char *pkgname_str = (*env)->GetStringUTFChars(env, pkgname, 0);
        	size_t pkgname_len = (*env)->GetStringUTFLength(env, pkgname);
    	int ret = awgStartProxy((struct go_string){
    		.str = ifname_str,
    		.n = ifname_len
    	}, (struct go_string){
    		.str = settings_str,
    		.n = settings_len
    	}, (struct go_string){
           		.str = pkgname_str,
           		.n = pkgname_len
           	},bypass);
    	(*env)->ReleaseStringUTFChars(env, ifname, ifname_str);
    	(*env)->ReleaseStringUTFChars(env, settings, settings_str);
    	(*env)->ReleaseStringUTFChars(env, pkgname, pkgname_str);
    	return ret;
}

JNIEXPORT void JNICALL Java_org_amnezia_awg_ProxyGoBackend_awgStopProxy(JNIEnv *env, jclass c) {
    awgStopProxy();
}

JNIEXPORT jstring JNICALL Java_org_amnezia_awg_ProxyGoBackend_awgGetProxyConfig(JNIEnv *env, jclass c, jint handle) {
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
    if (g_protector != NULL) {
        (*env)->DeleteGlobalRef(env, g_protector);
    }
    g_protector = (*env)->NewGlobalRef(env, protector);
    jclass protectorClass = (*env)->GetObjectClass(env, protector);
    g_protectMethod = (*env)->GetMethodID(env, protectorClass, "bypass", "(I)I");
}

int bypass_socket(int fd) {
    JNIEnv *env;
    jboolean attached = JNI_FALSE;
    jint rs = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6);
    if (rs == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            return 0;
        }
        attached = JNI_TRUE;
    } else if (rs != JNI_OK) {
        return 0;  // Other error, fail safely.
    }
    int result = (*env)->CallIntMethod(env, g_protector, g_protectMethod, fd);
    if (attached == JNI_TRUE) {
        (*g_jvm)->DetachCurrentThread(g_jvm);
    }
    return result;
}

