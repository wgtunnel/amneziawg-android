plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "org.amnezia.awg.hevtunnel"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 24

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            ndkBuild {
                targets("hev-socks5-tunnel")
                arguments.add("APP_CFLAGS+=-DPKGNAME=org/amnezia/awg/hevtunnel -ffile-prefix-map=${rootDir}=.")
                arguments.add("APP_LDFLAGS+=-Wl,--build-id=none")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    kotlin {
        jvmToolchain(17)
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "org.amnezia.awg"
            artifactId = "hevtunnel"
            version = "1.0.0" // Set appropriate version
            afterEvaluate {
                from(components["release"])
            }
            pom {
                name.set("Amnezia WG Tunnel Library")
                description.set("Embeddable tunnel library for WG for Android")
                url.set("https://amnezia.org/")

                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/zaneschepke/amneziawg-android")
                    developerConnection.set("scm:git:https://github.com/zaneschepke/amneziawg-android")
                    url.set("https://github.com/zaneschepke/amneziawg-android")
                }
                developers {
                    organization {
                        name.set("Zane Schepke")
                        url.set("https://zaneschepke.com")
                    }
                    developer {
                        name.set("Zane Schepke")
                        email.set("support@zaneschepke.com")
                    }
                }
            }
        }
    }
}