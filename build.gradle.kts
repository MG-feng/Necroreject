plugins {
    kotlin("jvm") version "1.9.25"
}

version = "0.1.0-dev"

val mindustryVersion = "v160.7"
val sdkRoot: String? = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
val isWindows = System.getProperty("os.name").lowercase().contains("windows")
val modArtifactName = "necroreject"

sourceSets {
    main {
        kotlin.srcDirs("scripts")
        java.srcDirs("scripts")
    }
}

repositories {
    mavenCentral()

    ivy {
        url = uri("https://github.com/")
        patternLayout {
            artifact(
                when (mindustryVersion) {
                    "latest" -> "/[organisation]/[module]/releases/[revision]/download/dependencies.jar"
                    "be" -> "/[organisation]/[module]/releases/download/master/[revision].jar"
                    else -> "/[organisation]/[module]/releases/download/[revision]/dependencies.jar"
                }
            )
        }
        metadataSources { artifact() }

        content {
            if (mindustryVersion == "be") {
                includeVersion("Anuken", "MindustryBuilds", "latest")
            } else {
                includeVersion("Anuken", "Mindustry", mindustryVersion)
            }
        }
    }
}

dependencies {
    compileOnly(if (mindustryVersion == "be") "Anuken:MindustryBuilds:latest" else "Anuken:Mindustry:$mindustryVersion")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
}

val jarAndroid = tasks.register("jarAndroid") {
    dependsOn(tasks.jar)

    doLast {
        if (sdkRoot.isNullOrEmpty() || !File(sdkRoot).exists()) {
            throw GradleException("No valid Android SDK found. Ensure that ANDROID_HOME is set to your Android SDK directory.")
        }

        val platformRoot = File("$sdkRoot/platforms/").listFiles()
            ?.sortedBy { it.name }
            ?.reversed()
            ?.find { f -> File(f, "android.jar").exists() }
            ?: throw GradleException("No android.jar found. Ensure that you have an Android platform installed.")

        val dependencies = (configurations.compileClasspath.get().toList() +
                configurations.runtimeClasspath.get().toList() +
                listOf(File(platformRoot, "android.jar")))
            .joinToString(" ") { "--classpath ${it.path}" }

        val d8 = if (isWindows) "d8.bat" else "d8"
        val libsDir = "${layout.buildDirectory.get().asFile}/libs"

        val process = ProcessBuilder(
            *"$d8 $dependencies --min-api 21 --output $libsDir/${modArtifactName}Android.jar $libsDir/${modArtifactName}Desktop.jar".split(" ").toTypedArray())
            .directory(File(libsDir))
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        process.waitFor()
    }
}

tasks.jar {
    archiveBaseName.set(modArtifactName)
    archiveClassifier.set("Desktop")

    from(sourceSets.main.get().output)

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/*.EC")
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/LICENSE")
        exclude("META-INF/LICENSE.txt")
        exclude("META-INF/NOTICE")
        exclude("META-INF/NOTICE.txt")
        exclude("module-info.class")
    }

    from(projectDir) {
        include("mod.hjson")
        include("content/**")
        include("sprites/**")
        include("bundles/**")
        include("sounds/**")

        exclude("**/.DS_Store")
        exclude("**/Thumbs.db")
        exclude("**/*.kt")
        exclude("**/*.java")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val deploy = tasks.register("deploy", Jar::class) {
    dependsOn(jarAndroid)
    dependsOn(tasks.jar)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set(modArtifactName)
    archiveClassifier.set("")

    from({
        listOf(
            zipTree("${layout.buildDirectory.get().asFile}/libs/${modArtifactName}Desktop.jar"),
            zipTree("${layout.buildDirectory.get().asFile}/libs/${modArtifactName}Android.jar")
        )
    })

    doLast {
        delete(
            "${layout.buildDirectory.get().asFile}/libs/${modArtifactName}Desktop.jar",
            "${layout.buildDirectory.get().asFile}/libs/${modArtifactName}Android.jar"
        )
    }
}
