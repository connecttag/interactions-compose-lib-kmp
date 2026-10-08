import java.util.Base64

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
    signing
}

val pGroup = project.findProperty("group")?.toString()?.takeIf { it.isNotBlank() }
    ?: project.findProperty("LIB_GROUP")?.toString()?.takeIf { it.isNotBlank() }
    ?: "com.github.connecttag"
group = pGroup

val pVersion = project.findProperty("version")?.toString()?.takeIf { it.isNotBlank() && it != "unspecified" }
    ?: project.findProperty("LIB_VERSION")?.toString()?.takeIf { it.isNotBlank() }
    ?: "1.0.0"
version = pVersion

kotlin {
    android {
        namespace = "org.connecttag.lib.interactions.compose"
        compileSdk = 37
        minSdk = 24
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        withHostTest {
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ConnectTagInteractionsComposeLib"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate {
        common {
            group("mobile") {
                withAndroidTarget()
                withIos()
            }
        }
    }

    sourceSets {
        val mobileMain = getByName("mobileMain") {
            dependencies {
                implementation(libs.moko.permissions)
                implementation(libs.moko.permissions.camera)
                implementation(libs.moko.permissions.location)
                implementation(libs.moko.permissions.microphone)
                implementation(libs.moko.permissions.notifications)
                implementation(libs.moko.permissions.compose)
            }
        }
        androidMain.get().dependsOn(mobileMain)
        iosMain.get().dependsOn(mobileMain)

        commonMain.dependencies {
            api(libs.connecttag.filestorage)
            api(libs.connecttag.location)
            api(libs.connecttag.permissions)
            implementation(libs.connecttag.logging)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.compose.activity)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.play.services.location)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        val androidHostTest = findByName("androidHostTest")
        androidHostTest?.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.moko.permissions.test)
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("ConnectTag Interactions Compose Library (KMP)")
            description.set("Cross-platform Compose multiplatform UI adapters, document export, file picking, and permission launchers")
            url.set("https://github.com/connecttag/interactions-compose-lib-kmp")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("connecttagye")
                    name.set("Connect Tag")
                    email.set("connecttagye@gmail.com")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/connecttag/interactions-compose-lib-kmp.git")
                developerConnection.set("scm:git:ssh://git@github.com:connecttag/interactions-compose-lib-kmp.git")
                url.set("https://github.com/connecttag/interactions-compose-lib-kmp/tree/main")
            }
        }
    }
    repositories {
        maven {
            name = "Local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

signing {
    val rawSigningKey = System.getenv("GPG_PRIVATE_KEY") ?: project.findProperty("signingKey")?.toString()
    val signingPassword = System.getenv("GPG_PASSPHRASE") ?: project.findProperty("signingPassword")?.toString()
    val signingKeyId = System.getenv("GPG_KEY_ID") ?: project.findProperty("signingKeyId")?.toString()

    if (rawSigningKey != null && signingPassword != null) {
        val signingKey = if (rawSigningKey.contains("BEGIN PGP")) {
            rawSigningKey
        } else {
            try {
                String(Base64.getDecoder().decode(rawSigningKey.trim()), Charsets.UTF_8)
            } catch (e: Exception) {
                rawSigningKey
            }
        }

        if (signingKeyId != null) {
            useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        } else {
            useInMemoryPgpKeys(signingKey, signingPassword)
        }
        sign(publishing.publications)
    } else if (project.hasProperty("signing.keyId")) {
        sign(publishing.publications)
    }
}
