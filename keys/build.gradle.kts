import java.io.DataOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The commit this build came from (e.g. 0.1.0+712d74c, "-dirty" with uncommitted changes), so versions.ps1 can spot drift.
val gitRev: String = runCatching {
    fun git(vararg a: String) = ProcessBuilder("git", *a).directory(rootDir).start().inputStream.bufferedReader().readText().trim()
    val h = git("rev-parse", "--short", "HEAD")
    if (h.isEmpty()) "nogit" else h + if (git("status", "--porcelain").isNotEmpty()) "-dirty" else ""
}.getOrDefault("nogit")

android {
    namespace = "com.thescarletcoder.keys"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thescarletcoder.keys"
        minSdk = 34
        targetSdk = 36
        versionCode = 4
        versionName = "0.2.1"
        buildConfigField("boolean", "TUNING", "false")
    }

    buildTypes {
        debug {
            versionNameSuffix = "+$gitRev"
            buildConfigField("boolean", "TUNING", "true")
        }
        release {
            // Public releases only: signed with the release key by release.ps1.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // The owner's own devices: release-optimized (R8, not debuggable) but signed with this laptop's debug key,
        // so it installs over debug installs and keeps learned words. Never install `release` on those devices.
        create("personal") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            versionNameSuffix = "+$gitRev"
            buildConfigField("boolean", "TUNING", "true")
        }
        // The smoke copy ("Keys Smoke"): release-optimized exactly like the public build, but with its own app id
        // and the debug key, so it installs next to the installed Keys without touching it. Driven over adb by smoke.ps1
        // during /finalize, then uninstalled.
        create("smoke") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".smoke"
            versionNameSuffix = "-smoke+$gitRev"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources { noCompress += "bin" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    // Installs Compose's bundled baseline profiles for sideloaded builds (no Play cloud profiles).
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    testImplementation("junit:junit:4.13.2")
}

// Same rule as Scarlet Command: a keyboard sees everything typed, so it must never have network access.
tasks.matching { it.name.matches(Regex("process[A-Za-z]+MainManifest")) }.configureEach {
    doLast {
        outputs.files.asFileTree.matching { include("**/AndroidManifest.xml") }.forEach { f ->
            val banned = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")
                .filter { f.readText().contains("\"$it\"") }
            if (banned.isNotEmpty()) {
                throw GradleException("Merged manifest ${f.path} contains $banned. A dependency pulled in network access. Stop and flag it to the owner.")
            }
        }
    }
}

// The word list ships as en.bin, built here from tools/dict/en.dict (the source, kept as text) plus tools/dict/extra.dict
// (names the AOSP list predates).
// Format (tools/dict/README.md): "SKD1", entry count, then entries sorted by lowercase word (ties: most frequent first).
abstract class BuildDict : DefaultTask() {
    @get:InputFile abstract val source: RegularFileProperty
    @get:InputFile abstract val extra: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction fun run() {
        class E(val lower: String, val display: String, val freq: Int, val offensive: Boolean)
        val entries = (source.get().asFile.readLines() + extra.get().asFile.readLines()).mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 2) null else E(p[0].lowercase(), p[0], (p[1].toIntOrNull() ?: 0).coerceIn(0, 255), p.getOrNull(2) == "o")
        }.sortedWith(compareBy<E>({ it.lower }, { -it.freq }))
        val out = outputDir.get().file("en.bin").asFile
        out.parentFile.mkdirs()
        DataOutputStream(out.outputStream().buffered()).use { o ->
            o.writeBytes("SKD1")
            o.writeInt(entries.size)
            entries.forEach { e ->
                val l = e.lower.toByteArray(Charsets.UTF_8)
                require(l.size in 1..255) { "word too long: ${e.lower}" }
                o.writeByte(l.size); o.write(l)
                if (e.display == e.lower) o.writeByte(0)
                else { val d = e.display.toByteArray(Charsets.UTF_8); o.writeByte(d.size); o.write(d) }
                o.writeByte(e.freq)
                o.writeByte(if (e.offensive) 1 else 0)
            }
        }
    }
}

val buildDict = tasks.register<BuildDict>("buildDict") {
    source.set(rootProject.file("tools/dict/en.dict"))
    extra.set(rootProject.file("tools/dict/extra.dict"))
    outputDir.set(layout.buildDirectory.dir("generated/dict"))
}
androidComponents {
    onVariants { v -> v.sources.assets?.addGeneratedSourceDirectory(buildDict, BuildDict::outputDir) }
}
tasks.withType<Test>().configureEach { dependsOn(buildDict) }
