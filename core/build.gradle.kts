plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("maven-publish")
}

android {
    namespace = "com.agentx.core"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }

        externalNativeBuild {
            cmake {
                val candidateDirs = listOf(
                    file(".cxx/Debug/6o2c554k/arm64-v8a/_deps/llama_cpp-src"),
                    file(".cxx/Debug/6o2c554k/x86_64/_deps/llama_cpp-src")
                )
                val found = candidateDirs.firstOrNull { File(it, "CMakeLists.txt").exists() }
                if (found != null) {
                    arguments("-DFETCHCONTENT_SOURCE_DIR_LLAMA_CPP=${found.absolutePath.replace('\\', '/')}")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.agentx"
                artifactId = "core"
                version = "1.1.1"
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/Sharmapank-j/agentX-suite")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: System.getenv("USERNAME")
                    password = System.getenv("GITHUB_TOKEN") ?: System.getenv("TOKEN")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
