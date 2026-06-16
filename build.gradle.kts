import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jfrog.gradle.plugin.artifactory.dsl.ArtifactoryPluginConvention
import org.jfrog.gradle.plugin.artifactory.task.ArtifactoryTask

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    id("com.jfrog.artifactory") version "6.0.4"
    id("net.octyl.level-headered") version "0.1.2"
    id("net.researchgate.release") version "3.1.0"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation("com.google.gradle:osdetector-gradle-plugin:1.7.3")
    implementation("de.undercouch:gradle-download-task:5.7.0")

    testImplementation(gradleTestKit())
    testImplementation(platform("org.junit:junit-bom:6.1.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

release {
    git {
        requireBranch = "master"
    }
    tagTemplate = "v\$version"
}

gradlePlugin {
    plugins {
        create("codecov") {
            id = "org.enginehub.codecov"
            implementationClass = "org.enginehub.codecov.CodecovPlugin"
        }
    }
}

plugins.withId("java") {
    the<JavaPluginExtension>().toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

levelHeadered {
    headerTemplate(rootProject.file("HEADER.txt"))
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

if (JavaVersion.current().isJava8Compatible) {
    tasks.withType<Javadoc>().configureEach {
        (options as CoreJavadocOptions).addStringOption("Xdoclint:none", "-quiet")
    }
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    dependsOn("classes")
    archiveClassifier.set("sources")
    from(project.the<SourceSetContainer>().getByName("main").allSource)
}
val javadocJar = tasks.register<Jar>("javadocJar") {
    dependsOn("javadoc")
    archiveClassifier.set("javadoc")
    from(tasks.getByName("javadoc"))
}
tasks.named("build") {
    dependsOn(sourcesJar, javadocJar)
}

configure<PublishingExtension> {
    publications {
        // This is also partially configured by the `java-gradle-plugin` plugin
        register<MavenPublication>("pluginMaven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            artifact(tasks.getByName("sourcesJar"))
            artifact(tasks.getByName("javadocJar"))
        }
    }
}

val ext = extensions.extraProperties
if (!project.hasProperty("artifactory_contextUrl"))
    ext["artifactory_contextUrl"] = "http://localhost"
if (!project.hasProperty("artifactory_user"))
    ext["artifactory_user"] = "guest"
if (!project.hasProperty("artifactory_password"))
    ext["artifactory_password"] = ""
configure<ArtifactoryPluginConvention> {
    setContextUrl(project.property("artifactory_contextUrl").toString())
    clientConfig.publisher.run {
        repoKey = when {
            "SNAPSHOT" in project.version.toString() -> "libs-snapshot-local"
            else -> "libs-release-local"
        }
        username = project.property("artifactory_user").toString()
        password = project.property("artifactory_password").toString()
        isMaven = true
        isIvy = false
    }
}

// Artifactory eagerly evaluates publications, so this must run after all changes to artifacts are done
afterEvaluate {
    tasks.named<ArtifactoryTask>("artifactoryPublish") {
        publications("pluginMaven", "codecovPluginMarkerMaven")
    }
}
