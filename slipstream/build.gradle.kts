plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    // JDOM is our XML parser
    api("org.jdom:jdom2:2.0.6.1")

    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("org.slf4j:slf4j-jdk14:1.7.25")
    implementation("net.sf.saxon:Saxon-HE:11.4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.7.1")
}

java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17
