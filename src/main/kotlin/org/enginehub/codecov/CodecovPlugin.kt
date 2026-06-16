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

import com.google.gradle.osdetector.OsDetector
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.process.ExecOperations
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import javax.inject.Inject

val Project.codecov get() = the<CodecovExtension>()

class CodecovPlugin @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val execOperations: ExecOperations,
) : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            apply(plugin = "com.google.osdetector")
            apply(plugin = "de.undercouch.download")
            extensions.create<CodecovExtension>("codecov", project)
            setupTasks()
        }
    }

    private fun Project.setupTasks() {
        val codecovCache = gradle.gradleUserHomeDir.toPath().resolve("codecov")
        val downloadCodecov = tasks.register<Download>("downloadCodecov") {
            src(codecov.version.map { version ->
                "https://github.com/codecov/codecov-exe/releases/download/$version/${codecovPackageName()}"
            })
            dest(codecov.version.map { version ->
                codecovCache.resolve("$version/codecov.zip").toFile()
            })
            downloadTaskDir(codecovCache.toFile())
            onlyIfModified(true)
            useETag(true)
            tempAndMove(true)
        }
        val extractCodecov = tasks.register<Copy>("extractCodecov") {
            dependsOn(downloadCodecov)
            from(archiveOperations.zipTree(downloadCodecov.map { it.dest.toPath() }))
            into(codecov.version.map { version ->
                codecovCache.resolve("$version/extracted")
            })
        }
        tasks.register("uploadCodecov") {
            val reportFile = codecov.reportTask.map { it.reports.xml.outputLocation }
            dependsOn(codecov.reportTask, extractCodecov)
            inputs.files(reportFile, extractCodecov)
            inputs.property("token", codecov.token)
            inputs.property("required", codecov.required)
            doFirst {
                val executableFile = extractCodecov.get().destinationDir.toPath()
                    .resolve(codecovExecutableName())
                Files.getFileAttributeView(executableFile, PosixFileAttributeView::class.java)?.let { posix ->
                    val perms = posix.readAttributes().permissions() + PosixFilePermissions.fromString("--x--x--x")
                    posix.setPermissions(perms)
                }
                execOperations.exec {
                    executable(executableFile)
                    args("-f", reportFile.get(), "-t", codecov.token.get())
                    if (codecov.required.get()) {
                        args("--required")
                    }
                }
            }
        }
    }

    private fun Project.codecovPackageName(): String {
        return when (the<OsDetector>().os) {
            "windows" -> "codecov-win7-x64.zip"
            "osx" -> "codecov-osx-x64.zip"
            else -> "codecov-linux-x64.zip"
        }
    }

    private fun Project.codecovExecutableName(): String {
        return when (the<OsDetector>().os) {
            "windows" -> "codecov.exe"
            else -> "codecov"
        }
    }

}
