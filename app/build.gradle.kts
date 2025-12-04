plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

import java.io.File
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateDexHashTask : DefaultTask() {
  @get:InputDirectory
  abstract val dexDir: DirectoryProperty

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:Input
  abstract val variantName: Property<String>

  @TaskAction
  fun generate() {
    val digest = MessageDigest.getInstance("SHA-256")
    val root = dexDir.get().asFile
    val dexFiles =
      if (root.exists()) root.walkTopDown().filter { it.isFile && it.extension == "dex" }.toList()
      else emptyList()

    val perFileHashes =
      dexFiles.map { dex ->
        val perDigest = MessageDigest.getInstance("SHA-256")
        dex.inputStream().use { input ->
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            perDigest.update(buffer, 0, read)
          }
        }
        perDigest.digest().joinToString("") { b -> "%02x".format(b) }
      }

    val hash =
      if (perFileHashes.isNotEmpty()) {
        val combined = MessageDigest.getInstance("SHA-256")
        perFileHashes.sorted().forEach { hex -> combined.update(hex.toByteArray()) }
        combined.digest().joinToString("") { b -> "%02x".format(b) }
      } else {
        ""
      }

    val assetsRoot = outputDir.get().asFile
    assetsRoot.mkdirs()
    val assetFile = assetsRoot.resolve("dex_integrity_hash.txt")
    assetFile.writeText(hash)
  }
}

android {
  namespace = "com.protosdk.demo"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.protosdk.demo"
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions {
    jvmTarget = "1.8"
  }

  buildFeatures {
    compose = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

androidComponents {
  onVariants { variant ->
    val generatedAssetsDir = layout.buildDirectory.dir("generated/dexHash/${variant.name}/assets")
    val capName = variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    val generateDexHashTask =
      tasks.register("generate${capName}DexHash", GenerateDexHashTask::class.java) {
        // Use merged dex outputs (covers mergeDex and mergeProjectDex variants)
        dexDir.set(layout.buildDirectory.dir("intermediates/dex/${variant.name}"))
        outputDir.set(generatedAssetsDir)
        variantName.set(variant.name)

        // Depend on whichever merge dex tasks exist for this variant to avoid implicit access warnings.
        listOf(
          "mergeDex$capName",
          "mergeProjectDex$capName",
          "mergeExtDex$capName",
          "mergeLibDex$capName",
        ).forEach { taskName ->
          runCatching { tasks.named(taskName) }.getOrNull()?.let { dependsOn(it) }
        }
      }

    variant.sources.assets?.addGeneratedSourceDirectory(
      generateDexHashTask,
      GenerateDexHashTask::outputDir,
    )
  }
}

dependencies {
  implementation(project(":protosdk"))

  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation(platform("androidx.compose:compose-bom:2025.08.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("com.google.code.gson:gson:2.11.0")

  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}
