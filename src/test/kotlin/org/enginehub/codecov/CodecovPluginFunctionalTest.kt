/*
 * gradle-codecov-plugin, a Gradle plugin to upload Codecov reports.
 * Copyright (C) EngineHub <https://www.enginehub.org>
 * Copyright (C) gradle-codecov-plugin contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.enginehub.codecov

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.writeText

class CodecovPluginFunctionalTest {

    @TempDir
    lateinit var projectDir: Path

    companion object {
        // Shared so the codecov executable is only downloaded once across tests.
        @JvmStatic
        @TempDir
        lateinit var gradleHome: Path

        private const val CODECOV_VERSION = "1.13.0"

        private val executableName =
            if (System.getProperty("os.name").lowercase().contains("win")) "codecov.exe" else "codecov"
    }

    private fun writeFile(path: String, content: String) {
        val file = projectDir.resolve(path)
        Files.createDirectories(file.parent)
        file.writeText(content)
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(projectDir.toFile())
            .withArguments(*args, "-g", gradleHome.absolutePathString(), "--stacktrace")

    @Test
    fun `extractCodecov downloads and unpacks the codecov executable`() {
        writeFile("settings.gradle.kts", """rootProject.name = "extract-test"""")
        writeFile(
            "build.gradle.kts",
            """
            plugins {
                id("org.enginehub.codecov")
            }

            codecov {
                version = "$CODECOV_VERSION"
            }
            """.trimIndent(),
        )

        val result = runner("extractCodecov").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":extractCodecov")?.outcome)
        val executable = gradleHome.resolve("codecov/$CODECOV_VERSION/extracted/$executableName")
        assertTrue(executable.exists(), "expected extracted codecov executable at $executable")
    }

    @Test
    fun `uploadCodecov fails when the report cannot be uploaded`() {
        writeFile("settings.gradle.kts", """rootProject.name = "upload-test"""")
        writeFile(
            "build.gradle.kts",
            """
            plugins {
                java
                jacoco
                id("org.enginehub.codecov")
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation(platform("org.junit:junit-bom:6.1.0"))
                testImplementation("org.junit.jupiter:junit-jupiter")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }

            tasks.named<Test>("test") {
                useJUnitPlatform()
            }

            tasks.jacocoTestReport {
                dependsOn(tasks.test)
                reports.xml.required = true
            }

            codecov {
                version = "$CODECOV_VERSION"
                token = "fake-codecov-token"
                reportTask = tasks.jacocoTestReport
            }
            """.trimIndent(),
        )
        writeFile(
            "src/main/java/example/Example.java",
            """
            package example;

            public class Example {
                public int add(int a, int b) {
                    return a + b;
                }
            }
            """.trimIndent(),
        )
        writeFile(
            "src/test/java/example/ExampleTest.java",
            """
            package example;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;

            public class ExampleTest {
                @Test
                void adds() {
                    assertEquals(3, new Example().add(1, 2));
                }
            }
            """.trimIndent(),
        )

        val result = runner("uploadCodecov").buildAndFail()

        // The report and the executable both resolve; only the upload itself fails.
        assertEquals(TaskOutcome.SUCCESS, result.task(":jacocoTestReport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":extractCodecov")?.outcome)
        assertEquals(TaskOutcome.FAILED, result.task(":uploadCodecov")?.outcome)
        assertTrue(
            result.output.contains("finished with non-zero exit value"),
            "uploadCodecov should fail because the codecov executable ran but could not upload; output was:\n${result.output}",
        )
    }
}
