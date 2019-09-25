import net.minecrell.gradle.licenser.LicenseExtension
import org.jfrog.gradle.plugin.artifactory.dsl.ArtifactoryPluginConvention
import org.jfrog.gradle.plugin.artifactory.dsl.DoubleDelegateWrapper
import org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig
import org.jfrog.gradle.plugin.artifactory.task.ArtifactoryTask

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    kotlin("jvm") version embeddedKotlinVersion
    id("com.jfrog.artifactory") version "4.9.10"
    id("net.minecrell.licenser") version "0.4.1"
}

repositories {
    jcenter()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation("com.google.gradle:osdetector-gradle-plugin:1.6.2")
    implementation("de.undercouch:gradle-download-task:4.0.0")
}

gradlePlugin {
    plugins {
        create("greetingsPlugin") {
            id = "org.enginehub.codecov"
            implementationClass = "org.enginehub.codecov.CodecovPlugin"
        }
    }
}

configure<LicenseExtension> {
    header = rootProject.file("HEADER.txt")
    exclude("**/META-INF/**")
    exclude("**/*.properties")
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
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
    publish(delegateClosureOf<PublisherConfig> {
        setContextUrl(project.property("artifactory_contextUrl"))
        setPublishIvy(false)
        setPublishPom(true)
        repository(delegateClosureOf<DoubleDelegateWrapper> {
            invokeMethod("setRepoKey", when {
                "SNAPSHOT" in project.version.toString() -> "libs-snapshot-local"
                else -> "libs-release-local"
            })
            invokeMethod("setUsername", project.property("artifactory_user"))
            invokeMethod("setPassword", project.property("artifactory_password"))
        })
        defaults(delegateClosureOf<ArtifactoryTask> {
            publications("pluginMaven")
            setPublishArtifacts(true)
        })
    })
}
tasks.named<ArtifactoryTask>("artifactoryPublish") {
    skip = true
}
