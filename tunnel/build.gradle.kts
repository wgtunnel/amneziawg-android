@file:Suppress("UnstableApiUsage")

import org.gradle.api.tasks.testing.logging.TestLogEvent

val pkg: String = providers.gradleProperty("amneziawgPackageName").get()
val cmakeAndroidPackageName: String = providers.environmentVariable("ANDROID_PACKAGE_NAME").getOrElse(pkg)

plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
    signing
}

android {

    ndkVersion = "28.2.13676358"  // Pins the NDK to r28c for consistent builds and 16KB support

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    namespace = "${pkg}.tunnel"
    externalNativeBuild {
        cmake {
            path("tools/CMakeLists.txt")
        }
    }
    testOptions.unitTests.all {
        it.testLogging { events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED) }
    }
    buildTypes {
        all {
            externalNativeBuild {
                cmake {
                    targets("libam-go.so", "libam.so", "libam-quick.so")
                    arguments("-DGRADLE_USER_HOME=${project.gradle.gradleUserHomeDir}")
                    arguments("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
                }
            }
        }
        release {
            externalNativeBuild {
                cmake {
                    arguments("-DANDROID_PACKAGE_NAME=${cmakeAndroidPackageName}")
                }
            }
        }
        debug {
            externalNativeBuild {
                cmake {
                    arguments("-DANDROID_PACKAGE_NAME=${cmakeAndroidPackageName}.debug")
                }
            }
        }
    }
    lint {
        disable += "LongLogTag"
        disable += "NewApi"
    }
    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
    }
}

dependencies {
    api(project(":hevtunnel"))

    implementation(libs.androidx.annotation)
    runtimeOnly(libs.androidx.collection)
    compileOnly(libs.jsr305)
    testImplementation(libs.junit)

    implementation(libs.relinker)

    //dns
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.okhttp)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.zaneschepke"
            artifactId = "amneziawg-android"
            version = providers.gradleProperty("amneziawgVersionName").get()
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


signing {
    useInMemoryPgpKeys(
        getLocalProperty("SECRET_KEY") ?: System.getenv("SECRET_KEY"),
        getLocalProperty("PASSWORD") ?: System.getenv("PASSWORD")
    )
    sign(publishing.publications)
}
