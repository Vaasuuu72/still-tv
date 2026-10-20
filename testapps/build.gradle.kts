plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

// Emulator-only fixture apps for exercising the launcher grid (six columns, scrolling, folders, long names,
// banner vs icon-only artwork). Never install these on the TV; they are not part of Still TV.
android {
    namespace = "tv.still.fixture"
    compileSdk = 36
    defaultConfig { minSdk = 21; targetSdk = 35; versionCode = 1; versionName = "1" }
    flavorDimensions += "app"
    val fixtures = listOf(
        Triple("alpha", "Fixture Aurora", "#C2410C"), Triple("bravo", "Fixture Boreal", "#0F766E"),
        Triple("charlie", "Fixture Cinema", "#1D4ED8"), Triple("delta", "Fixture Docs", "#7C3AED"),
        Triple("echo", "Fixture Echo Radio", "#B91C1C"), Triple("foxtrot", "Fixture Fitness", "#15803D"),
        Triple("golf", "Fixture Golf Live", "#F5F5F4"), Triple("hotel", "Fixture with an Extremely Long Application Name", "#0E7490"),
        Triple("india", "Fixture Iris", "#111827"), Triple("juliet", "Fixture Jukebox", "#DB2777"),
        Triple("kilo", "Fixture Kids", "#CA8A04"), Triple("lima", "Fixture Lumen", "#4338CA"))
    fixtures.forEachIndexed { index, (name, label, colour) ->
        productFlavors.create(name) {
            dimension = "app"
            applicationId = "tv.still.fixture.$name"
            resValue("string", "fixture_label", label)
            resValue("color", "fixture_colour", colour)
            // Every third fixture is icon-only (no TV banner), like phone apps on the Kodak.
            manifestPlaceholders["category"] = if (index % 3 == 2) "android.intent.category.LAUNCHER" else "android.intent.category.LEANBACK_LAUNCHER"
            manifestPlaceholders["banner"] = if (index % 3 == 2) "@drawable/fixture_icon" else "@drawable/fixture_banner"
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { checkReleaseBuilds = false }
}
