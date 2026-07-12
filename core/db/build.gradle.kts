plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.revela.core.db"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

room {
    // The Room Gradle plugin gives each build variant its own schema output
    // and merges safely — writing one shared dir from the KSP arg races when
    // debug/release KSP tasks run in parallel ("Empty schema file").
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))

    // api: RevelaDatabase extends RoomDatabase and DAOs return Flow, so
    // consumers need these types on their compile classpath.
    api(libs.room.runtime)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    testImplementation(libs.junit)
}
