import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    // kotlin("kapt")  // removed — Hilt migrated to KSP to avoid kapt stub duplication bug
}

android {
    namespace = "com.medpull.kiosk"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.medpull.kiosk"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // AWS Configuration
        buildConfigField("String", "AWS_REGION", "\"us-east-1\"")
        buildConfigField("String", "AWS_USER_POOL_ID", "\"us-east-1_j8Y6JrLF7\"")
        buildConfigField("String", "AWS_CLIENT_ID", "\"12jt58o6hmamb7hsadcrljgo1j\"")
        buildConfigField("String", "AWS_IDENTITY_POOL_ID", "\"us-east-1:cb9498d1-1713-4e00-a4eb-2c8b10885d32\"")
        buildConfigField("String", "AWS_API_ENDPOINT", "\"https://d40uuum7hj.execute-api.us-east-1.amazonaws.com/prod\"")
        buildConfigField("String", "AWS_S3_BUCKET", "\"medpull-hipaa-files-1759818639\"")

        // AI API keys — loaded from local.properties (not checked into git)
        val localProperties = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { localProperties.load(it) }
        }
        buildConfigField("String", "CLAUDE_API_KEY", "\"${localProperties.getProperty("CLAUDE_API_KEY", "")}\"")
        buildConfigField("String", "GROK_API_KEY", "\"${localProperties.getProperty("GROK_API_KEY", "")}\"")
        buildConfigField("String", "GROQ_API_KEY", "\"${localProperties.getProperty("GROQ_API_KEY", "")}\"")

        // Local Ollama co-pilot endpoint (tailnet host:port, cleartext HTTP on :11434).
        // Set OLLAMA_BASE_URL in local.properties; keep the host in sync with the
        // matching <domain> in res/xml/network_security_config.xml.
        buildConfigField(
            "String",
            "OLLAMA_BASE_URL",
            "\"${localProperties.getProperty("OLLAMA_BASE_URL", "http://ollama-box.tailnet.ts.net:11434")}\""
        )

        // Web-lookup endpoint for the co-pilot's needs_search fallback. Defaults to
        // public DuckDuckGo's no-JS HTML endpoint (plain HTTP GET, headless-safe).
        // Override via SEARCH_BASE_URL in local.properties to point at a dedicated
        // instance; a cleartext (http://) host must also be allowed in
        // res/xml/network_security_config.xml.
        buildConfigField(
            "String",
            "SEARCH_BASE_URL",
            "\"${localProperties.getProperty("SEARCH_BASE_URL", "https://html.duckduckgo.com")}\""
        )

        // Google Sheets Inventory
        buildConfigField("String", "GOOGLE_SHEETS_API_KEY", "\"AIzaSyDh1tQGyxM9zEYO56sKVtu9AYYnf_eM8Yw\"")
        buildConfigField("String", "INVENTORY_SPREADSHEET_ID", "\"1WdjCwgKv1e7Znj4ThT9jKDCFVwVfT9sn_j8CjJDE6oM\"")

        // Session timeout in milliseconds (15 minutes)
        buildConfigField("long", "SESSION_TIMEOUT_MS", "900000L")

        // Supported languages (must mirror Constants.Languages.ALL)
        buildConfigField("String[]", "SUPPORTED_LANGUAGES", "{\"en\", \"es\", \"zh\", \"fr\", \"ja\", \"pt\", \"ar\", \"ru\"}")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // TODO: Replace with release signing config
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Back-port java.time and other Java 8+ APIs to API 24 (Android 7). Needed
        // because minSdk dropped below 26 — our code and HAPI FHIR use java.time.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/MANIFEST.MF"
            excludes += "META-INF/*.kotlin_module"
        }
    }
}

dependencies {
    // Back-ports java.time / java.util.stream etc. to API 24 (see compileOptions).
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Hilt Dependency Injection — using KSP to avoid kapt stub duplication bug with Java 21
    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-android-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // AWS SDK
    implementation("com.amazonaws:aws-android-sdk-core:2.77.0")
    implementation("com.amazonaws:aws-android-sdk-cognitoidentityprovider:2.77.0")
    implementation("com.amazonaws:aws-android-sdk-s3:2.77.0")
    implementation("com.amazonaws:aws-android-sdk-textract:2.77.0")
    implementation("com.amazonaws:aws-android-sdk-translate:2.77.0")

    // PDF Libraries
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Using Android's built-in PdfRenderer instead of external library

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // DataStore (for preferences)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // WorkManager (for background sync)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ML Kit - Handwriting recognition
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // HAPI FHIR R4
    implementation("ca.uhn.hapi.fhir:hapi-fhir-base:7.4.0")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-structures-r4:7.4.0")

    // AppAuth for SMART on FHIR OAuth2
    implementation("net.openid:appauth:0.11.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// kapt block removed — Hilt migrated to KSP

// ─── Localization ───────────────────────────────────────────────────────────
// `./gradlew translateStrings` regenerates `values-{locale}/strings.xml` for
// every supported locale by feeding the canonical English `values/strings.xml`
// through AWS Translate. The Python helper is idempotent and incremental — it
// only translates keys that are missing from each target locale, so any
// hand-edited translations stay intact.
//
// Requirements:
//   * Python 3 on PATH
//   * boto3 installed (`pip3 install --user --break-system-packages boto3`)
//   * AWS credentials with translate:TranslateText permission
//
// Useful flags forwarded via `-Pargs="…"`:
//   ./gradlew translateStrings -Pargs="--locales es,fr"
//   ./gradlew translateStrings -Pargs="--force"
//   ./gradlew translateStrings -Pargs="--dry-run"
tasks.register<Exec>("translateStrings") {
    group = "localization"
    description = "Bulk-translate values/strings.xml into every supported locale via AWS Translate."
    // Script lives at <module-root>/scripts/translate_strings.py, one level
    // above the :app project directory. Use the root project's directory so
    // running this from any subproject still resolves correctly.
    workingDir = rootProject.projectDir
    val python = System.getenv("PYTHON3")
        ?: listOf("/opt/homebrew/bin/python3", "/usr/local/bin/python3")
            .firstOrNull { File(it).canExecute() }
        ?: "python3"
    val extraArgs = (project.findProperty("args") as? String)
        ?.split(" ")
        ?.filter { it.isNotBlank() }
        ?: emptyList()
    commandLine(listOf(python, "scripts/translate_strings.py") + extraArgs)
}
