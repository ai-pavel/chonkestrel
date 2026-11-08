import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    // Apply the java plugin to add support for Java
    java

    // Apply the application plugin to add support for building an application
    application

    id("org.jetbrains.kotlin.jvm") version "1.9.10"

    // Make JMH available for performance experiments
    id("me.champeau.jmh") version "0.7.2"
}

repositories {
    mavenCentral()
}

val lwjglNatives = listOf(
    // No 32-bit support, as nothing uses it.
    "natives-windows-arm64",
    "natives-windows",

    "natives-macos-arm64",
    "natives-macos",

    "natives-linux-arm64",
    "natives-linux",
)

dependencies {
    // We use Slipstream as a library, to patch in mods on-the-fly
    implementation(project(":slipstream"))

    implementation("org.slick2d:slick2d-core:1.0.2") {
        // We're only using a few things from Slick, like its image and audio
        // decoders, so we don't need all it's native libraries. In fact, we
        // really shouldn't: we don't want both LWJGL 2 and 3!
        isTransitive = false
    }

    // There's a few libraries that Slick uses that we do need, though.
    implementation("org.jcraft:jorbis:0.0.17")

    // JDOM is our XML parser
    implementation("org.jdom:jdom2:2.0.6.1")

    // Use JUnit test framework
    testImplementation("junit:junit:4.13.1")

    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // 3.3.4 and later have GLFW 3.4, which can create windows with some
    // Windows OpenGL drivers 3.3 can't (such as in VMs, and on ARM)
    implementation(platform("org.lwjgl:lwjgl-bom:3.3.6"))

    implementation("org.lwjgl", "lwjgl")
    implementation("org.lwjgl", "lwjgl-glfw")
    implementation("org.lwjgl", "lwjgl-openal")
    implementation("org.lwjgl", "lwjgl-opengl")
    // Native file dialogs, for picking ftl.dat
    implementation("org.lwjgl", "lwjgl-tinyfd")
    // Rendering TrueType fonts, for the ftl.dat selection screen's title
    implementation("org.lwjgl", "lwjgl-stb")

    // Include all the platform-specific libraries, so our fatJar can run
    // on any supported platform.
    for (platform in lwjglNatives) {
        runtimeOnly("org.lwjgl", "lwjgl", classifier = platform)
        runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = platform)
        runtimeOnly("org.lwjgl", "lwjgl-openal", classifier = platform)
        runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = platform)
        runtimeOnly("org.lwjgl", "lwjgl-tinyfd", classifier = platform)
        runtimeOnly("org.lwjgl", "lwjgl-stb", classifier = platform)
    }
}

// Define the main class for the application
application {
    mainClass.set("xyz.znix.xftl.App")

    // On Mac, all UI stuff must happen on the first thread, including
    // our GLFW stuff.
    // This option doesn't exist on other platforms.
    if (System.getProperty("os.name").contains("Mac OS")) {
        applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
    }
}

// OpenAL Soft logs its own errors (on macOS, one about the audio device's
// type that doesn't matter) straight to the console: keep it quiet
tasks.named<JavaExec>("run") {
    environment("ALSOFT_LOGLEVEL", "0")
}

java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Write the version and the build date into the jar, for the game to show
// in the corner of the screen (see BuildInfo). The date changes every time,
// so this always runs, but it's only resources that get rebuilt.
val buildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildInfo")
    val version = project.property("xftlVersion").toString()
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val file = outputDir.get().file("xftl-build.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$version\ndate=$date\n")
    }
}
sourceSets.main.get().resources.srcDir(buildInfo)

val jar: Jar by tasks
val classes: Task by tasks

val fatJar by tasks.registering(Jar::class) {
    dependsOn(classes)

    // This is already properly added to runtimeClasspath, but it's not set
    // to automatically build - compileJava will run, but not the jar task.
    dependsOn(":slipstream:jar")

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    archiveBaseName.set("XFTL-complete")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Use our sources first, so we can replace Slick classes
    from(sourceSets.main.get().output)

    from(configurations.runtimeClasspath.get().map { file ->
        // Exclude signature files, since if the jar is signed
        // we have to do the whole thing at once.
        zipTree(file).matching {
            exclude("META-INF/*.SF")
            exclude("META-INF/*.RSA")
        }
    })
}

tasks.test {
    // Don't let the tests' games save over the one being played (see
    // ContinueSave)
    systemProperty("xftl.tests", "true")
}
