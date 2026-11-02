plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.hourlysteps.watchface"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hourlysteps.watchface"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
        }
        release {
            isMinifyEnabled = true
            // A Watch Face Format face is only resources, so shrinking them could remove the face.
            isShrinkResources = false
            // Debug signing is enough to sideload. Replace this before a Play upload.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

// Where google/watchface is cloned. Override with -PwffToolsDir=... if it lives elsewhere.
val wffToolsDir = providers.gradleProperty("wffToolsDir")
    .orElse(rootProject.layout.projectDirectory.dir("../watchface-tools/watchface").asFile.path)
    .get()

val validateWatchFace by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks watchface.xml against the WFF v4 schema."
    val face = layout.projectDirectory.file("src/main/res/raw/watchface.xml").asFile
    val spec = File(wffToolsDir, "third_party/wff/specification")
    val officialJar = File(spec, "validator/build/libs/wff-validator.jar")
    val java = File(System.getProperty("java.home"), "bin/java").path
    inputs.file(face)
    if (officialJar.exists()) {
        commandLine(java, "-jar", officialJar.path, "4", face.path)
    } else {
        // Reads the same schema files with the JDK until the official jar has been built.
        commandLine(
            java,
            rootProject.layout.projectDirectory.file("tools/WffSchemaCheck.java").asFile.path,
            File(spec, "documents/4/watchface.xsd").path,
            face.path,
        )
    }
}

tasks.named("check") {
    dependsOn(validateWatchFace)
}
