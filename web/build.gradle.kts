// The browser version of the game (a prototype): TeaVM compiles the game's
// JVM bytecode to JavaScript, with web versions of its platform layer in
// place of the desktop's: WebGL 2 for LWJGL's OpenGL (org.lwjgl.opengl
// here), no sound yet (org.lwjgl.openal), the page's input and canvas
// (xyz.znix.xftl.web), and the browser's own saves folder and cursors
// (xyz.znix.xftl.sys.PlatformSpecific and xyz.znix.xftl.rendering.Cursor).
plugins {
    java
    id("org.jetbrains.kotlin.jvm")
    id("org.teavm") version "0.17.0"
}

repositories {
    mavenCentral()
}

// The game's own classes and resources, as its jar has them, and JDOM's
val game: Configuration by configurations.creating {
    isTransitive = false
}

// The classes the web module has its own versions of, left out of the
// game's
val replacedClasses = listOf(
    "xyz/znix/xftl/sys/PlatformSpecific*.class",
    "xyz/znix/xftl/sys/WindowsPlatform*.class",
    "xyz/znix/xftl/sys/MacPlatform*.class",
    "xyz/znix/xftl/sys/LinuxPlatform*.class",
    "xyz/znix/xftl/rendering/Cursor.class",
    "org/jdom2/input/SAXBuilder*.class",
)

val gameForWeb by tasks.registering(Jar::class) {
    archiveFileName.set("game-for-web.jar")
    destinationDirectory.set(layout.buildDirectory.dir("game"))
    // (building the game's jar first)
    dependsOn(game)
    from({ game.map { zipTree(it) } }) {
        exclude(replacedClasses)
    }
}

dependencies {
    game(project(":"))
    game("org.jdom:jdom2:2.0.6.1")
    implementation(files(gameForWeb))

    // The game's libraries, but LWJGL (see org.lwjgl in this module)
    // (and JDOM only as the game's copy has it, without its SAXBuilder)
    implementation(project(":slipstream")) {
        exclude(group = "org.jdom")
    }
    implementation("org.slick2d:slick2d-core:1.0.2") {
        isTransitive = false
    }
    implementation("org.jcraft:jorbis:0.0.17")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    implementation(teavm.libs.jsoApis)

    // For the compiler plugin that empties the desktop-only methods (see
    // DesktopOnlyStubs), which runs in TeaVM
    compileOnly("org.teavm:teavm-core:0.17.0")
}

// The names of the game's resources, for TeaVM to include (see GameResources)
val webResourceList by tasks.registering {
    val resources = rootProject.file("src/main/resources")
    val output = layout.buildDirectory.file("web-resources/web-resources.txt")
    inputs.dir(resources)
    outputs.file(output)
    doLast {
        val names = resources.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(resources).invariantSeparatorsPath }.sorted()
        output.get().asFile.writeText(names.joinToString("\n") + "\n")
    }
}

sourceSets.main {
    resources.srcDir(webResourceList.map { it.outputs.files.singleFile.parentFile })
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

teavm {
    js {
        mainClass.set("xyz.znix.xftl.web.WebMain")
        addedToWebApp.set(true)
        // (readable names and stack traces, while it's a prototype)
        obfuscated.set(false)
        sourceMap.set(true)
        debugInformation.set(true)
    }
}

// The Chonkestrel mod, zipped (not compressed: its pictures are PNGs, and
// it's unzipped in the browser), for the page to load (see WebMain)
val modZip by tasks.registering(Zip::class) {
    archiveFileName.set("chonkestrel.zip")
    destinationDirectory.set(layout.buildDirectory.dir("mod"))
    entryCompression = ZipEntryCompression.STORED
    from(rootProject.file("mods/chonkestrel")) {
        exclude("**/.DS_Store")
    }
}

// The page to open: index.html, the compiled game and the mod, in
// build/site (`make web` builds it and serves it on http://localhost:8000)
val site by tasks.registering(Sync::class) {
    from("src/main/webapp")
    from(modZip)
    // (the desktop version's font for the screens before the game)
    from(rootProject.file("src/main/resources/roboto-font/Roboto-Regular.ttf")) {
        rename { "roboto.ttf" }
    }
    from(rootProject.file("src/main/resources/roboto-font/LICENSE.txt")) {
        rename { "roboto-LICENSE.txt" }
    }
    from(tasks.named("generateJavaScript")) {
        into("js")
    }
    into(layout.buildDirectory.dir("site"))
}
