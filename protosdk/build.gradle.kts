import com.android.build.api.variant.BuildConfigField
import org.gradle.api.Project
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.text.Charsets
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
}

abstract class ComputeDexHashTask : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  val sourceFiles: ConfigurableFileCollection = project.objects.fileCollection()

  @get:OutputFile val outputFile: RegularFileProperty = project.objects.fileProperty()

  val hashValue: Provider<String>
    get() =
        outputFile.map { file ->
          val target = file.asFile
          if (target.exists()) target.readText().trim() else ""
        }

  @TaskAction
  fun run() {
    val digest = MessageDigest.getInstance("SHA-256")
    val files =
        sourceFiles.files
            .filter { it.isFile }
            .sortedBy { it.relativeToOrNull(project.projectDir)?.path ?: it.absolutePath }
    files.forEach { input ->
      input.inputStream().use { stream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
          val read = stream.read(buffer)
          if (read <= 0) break
          digest.update(buffer, 0, read)
        }
      }
    }
    val hash = digest.digest().joinToString("") { "%02x".format(it) }
    val output = outputFile.get().asFile
    output.parentFile.mkdirs()
    output.writeText(hash)
  }
}

abstract class GenerateObfuscatedStringsTask : DefaultTask() {
  @get:InputFile val inputFile: RegularFileProperty = project.objects.fileProperty()

  @get:Input val xorKey: org.gradle.api.provider.Property<String> =
      project.objects.property(String::class.java)

  @get:OutputDirectory val outputDir: DirectoryProperty = project.objects.directoryProperty()

  @TaskAction
  fun run() {
    val keyBytes = xorKey.get().toByteArray(Charsets.UTF_8)
    val entries =
        inputFile
            .get()
            .asFile
            .readLines()
            .mapNotNull { line ->
              val trimmed = line.trim()
              if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
              val parts = trimmed.split("|", limit = 2)
              if (parts.size != 2) return@mapNotNull null
              parts[0].uppercase(Locale.ROOT) to parts[1]
            }

    val builder = StringBuilder()
    builder.appendLine("package com.protosdk.sdk.fingerprint.internal")
    builder.appendLine()
    builder.appendLine("internal object RootStringTable {")
    builder.appendLine("  private data class Entry(val type: String, val payload: IntArray)")
    builder.appendLine("  private val entries = listOf(")
    entries.forEachIndexed { index, (type, value) ->
      val encoded = encode(value, keyBytes)
      builder.append("    Entry(\"$type\", intArrayOf($encoded))")
      if (index < entries.lastIndex) builder.appendLine(",") else builder.appendLine()
    }
    builder.appendLine("  )")
    builder.appendLine()
    builder.appendLine("  fun decode(type: String, key: String): List<String> {")
    builder.appendLine("    if (key.isEmpty()) return emptyList()")
    builder.appendLine("    val keyBytes = key.encodeToByteArray()")
    builder.appendLine("    return entries.filter { it.type == type }.map { entry ->")
    builder.appendLine("      val decoded = ByteArray(entry.payload.size)")
    builder.appendLine("      entry.payload.forEachIndexed { index, value ->")
    builder.appendLine(
        "        val xorByte = keyBytes[index % keyBytes.size].toInt() and 0xFF")
    builder.appendLine("        decoded[index] = (value xor xorByte).toByte()")
    builder.appendLine("      }")
    builder.appendLine("      decoded.toString(Charsets.UTF_8)")
    builder.appendLine("    }")
    builder.appendLine("  }")
    builder.appendLine("}")

    val outputFile = outputDir.get().file("RootStringTable.kt").asFile
    outputFile.parentFile.mkdirs()
    outputFile.writeText(builder.toString())
  }

  private fun encode(value: String, key: ByteArray): String {
    val source = value.toByteArray(Charsets.UTF_8)
    return source
        .mapIndexed { index, byte ->
          (byte.toInt() xor (key[index % key.size].toInt())) and 0xFF
        }
        .joinToString(", ")
  }
}

android {
  namespace = "com.protosdk.sdk"
  compileSdk = 35
  ndkVersion = "25.1.8937393"

  defaultConfig {
    minSdk = 24

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")

    aarMetadata {
      minCompileSdk = 35
    }

    externalNativeBuild {
      cmake {
        arguments += listOf("-DANDROID_STL=c++_shared")
      }
    }
  }

  buildFeatures {
    buildConfig = true
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

  publishing {
    singleVariant("release") {
      withSourcesJar()
      withJavadocJar()
    }
  }

  lint {
    baseline = file("lint-baseline.xml")
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
    }
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
  implementation("com.google.code.gson:gson:2.11.0")
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
  }
}

androidComponents {
  onVariants { variant ->
    val variantName =
        variant.name.replaceFirstChar { ch ->
          if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
        }
    val xorKeyValue =
        UUID.nameUUIDFromBytes("${project.path}:${variant.name}".toByteArray())
            .toString()
            .replace("-", "")
            .take(16)

    tasks.register<ComputeDexHashTask>("compute${variantName}DexHash") {
      description = "Computes DEX integrity hash for ${variant.name}"
      group = "verification"
      sourceFiles.from(
          project.fileTree("src/main/java") { include("**/*.kt", "**/*.java") },
          project.fileTree("src/main/kotlin") { include("**/*.kt", "**/*.java") },
          project.fileTree("src/main/cpp") { include("**/*.cpp", "**/*.hpp", "**/*.h") },
      )
      outputFile.set(layout.buildDirectory.file("intermediates/dexHash/${variant.name}/hash.txt"))
    }

    val stringsTask =
        tasks.register<GenerateObfuscatedStringsTask>("generate${variantName}RootStrings") {
          description = "Generates obfuscated root strings for ${variant.name}"
          group = "build"
          inputFile.set(layout.projectDirectory.file("root_strings.txt"))
          xorKey.set(xorKeyValue)
          outputDir.set(layout.buildDirectory.dir("generated/rootStrings/${variant.name}"))
        }

    variant.sources.kotlin?.addGeneratedSourceDirectory(
        stringsTask,
        GenerateObfuscatedStringsTask::outputDir,
    )
    variant.sources.java?.addGeneratedSourceDirectory(
        stringsTask,
        GenerateObfuscatedStringsTask::outputDir,
    )

    val dexHashValue = project.calculateSourceHash()
    variant.buildConfigFields?.put(
        "ROOT_STRING_KEY",
        BuildConfigField("String", "\"$xorKeyValue\"", "Variant-specific XOR key"),
    )
    variant.buildConfigFields?.put(
        "DEX_INTEGRITY_HASH",
        BuildConfigField("String", "\"$dexHashValue\"", "Runtime DEX integrity hash"),
    )
  }
}

fun Project.calculateSourceHash(): String {
  val digest = MessageDigest.getInstance("SHA-256")
  val roots =
      listOf("src/main/java", "src/main/kotlin", "src/main/cpp")
          .map { projectDir.resolve(it) }
          .filter { it.exists() }
  val files =
      roots
          .flatMap { root -> root.walkTopDown().filter { it.isFile }.toList() }
          .sortedBy { it.relativeTo(projectDir).path }
  files.forEach { file ->
    file.inputStream().use { input ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
      }
    }
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
}
