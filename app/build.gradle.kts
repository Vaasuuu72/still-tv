plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

// `-Pstill.preview=true` builds a side-by-side preview: different application ID, its own label,
// and no HOME intent filter, so it can be installed next to the production launcher without
// changing which app the TV uses as its home screen.
val preview = providers.gradleProperty("still.preview").map { it.toBoolean() }.getOrElse(false)

android {
    namespace = "tv.still.launcher"
    compileSdk = 36
    defaultConfig {
        applicationId = if (preview) "tv.still.launcher.preview" else "tv.still.launcher"
        minSdk = 21
        targetSdk = 35
        versionCode = 3
        versionName = if (preview) "2.1.0-preview" else "2.1.0"
        testInstrumentationRunner = "tv.still.launcher.SmokeInstrumentation"
        manifestPlaceholders["appLabel"] = if (preview) "Still TV Preview" else "Still TV"
        manifestPlaceholders["homeCategory"] =
            if (preview) "tv.still.launcher.category.PREVIEW_NOT_HOME" else "android.intent.category.HOME"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true }
}
if (preview) base { archivesName.set("still-tv-preview") }
dependencies {
    testImplementation("junit:junit:4.13.2")
    // The Android stub jar's org.json throws in JVM tests; the real implementation runs the codec tests.
    testImplementation("org.json:json:20240303")
}
