/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright © 2017-2021 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>

struct go_string { const char *str; long n; };
extern int awgTurnOn(struct go_string ifname, int tun_fd, struct go_string settings, struct go_string pkgname);
extern void awgTurnOff(int handle);
extern int awgGetSocketV4(int handle);
extern int awgGetSocketV6(int handle);
extern char *awgGetConfig(int handle);
extern char *awgVersion();

extern void awgStartWireproxy(struct go_string config);
extern void awgStopWireproxy();

JNIEXPORT jint JNICALL Java_org_amnezia_awg_GoBackend_awgTurnOn(JNIEnv *env, jclass c, jstring ifname, jint tun_fd, jstring settings, jstring pkgname)
{
	const char *ifname_str = (*env)->GetStringUTFChars(env, ifname, 0);
	size_t ifname_len = (*env)->GetStringUTFLength(env, ifname);
	const char *settings_str = (*env)->GetStringUTFChars(env, settings, 0);
	size_t settings_len = (*env)->GetStringUTFLength(env, settings);
	const char *pkgname_str = (*env)->GetStringUTFChars(env, pkgname, 0);
    	size_t pkgname_len = (*env)->GetStringUTFLength(env, pkgname);
	int ret = awgTurnOn((struct go_string){
		.str = ifname_str,
		.n = ifname_len
	}, tun_fd, (struct go_string){
		.str = settings_str,
		.n = settings_len
	}, (struct go_string){
       		.str = pkgname_str,
       		.n = pkgname_len
       	});
	(*env)->ReleaseStringUTFChars(env, ifname, ifname_str);
	(*env)->ReleaseStringUTFChars(env, settings, settings_str);
	(*env)->ReleaseStringUTFChars(env, pkgname, pkgname_str);
	return ret;
}

JNIEXPORT void JNICALL Java_org_amnezia_awg_GoBackend_awgTurnOff(JNIEnv *env, jclass c, jint handle)
{
	awgTurnOff(handle);
}

JNIEXPORT jint JNICALL Java_org_amnezia_awg_GoBackend_awgGetSocketV4(JNIEnv *env, jclass c, jint handle)
{
	return awgGetSocketV4(handle);
}

JNIEXPORT jint JNICALL Java_org_amnezia_awg_GoBackend_awgGetSocketV6(JNIEnv *env, jclass c, jint handle)
{
	return awgGetSocketV6(handle);
}

JNIEXPORT jstring JNICALL Java_org_amnezia_awg_GoBackend_awgGetConfig(JNIEnv *env, jclass c, jint handle)
{
	jstring ret;
	char *config = awgGetConfig(handle);
	if (!config)
		return NULL;
	ret = (*env)->NewStringUTF(env, config);
	free(config);
	return ret;
}

JNIEXPORT jstring JNICALL Java_org_amnezia_awg_GoBackend_awgVersion(JNIEnv *env, jclass c)
{
	jstring ret;
	char *version = awgVersion();
	if (!version)
		return NULL;
	ret = (*env)->NewStringUTF(env, version);
	free(version);
	return ret;
}

JNIEXPORT void JNICALL Java_org_amnezia_awg_ProxyGoBackend_awgStartWireproxy(JNIEnv *env, jclass c, jstring config)
{
	const char *config_str = (*env)->GetStringUTFChars(env, config, 0);
	size_t config_len = (*env)->GetStringUTFLength(env, config);
	awgStartWireproxy((struct go_string){
		.str = config_str,
		.n = config_len
	});
	(*env)->ReleaseStringUTFChars(env, config, config_str);
}

JNIEXPORT void JNICALL Java_org_amnezia_awg_ProxyGoBackend_awgStopWireproxy(JNIEnv *env, jclass c)
{
	awgStopWireproxy();
}