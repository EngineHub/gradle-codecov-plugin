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
import org.gradle.kotlin.dsl.KotlinClosure0
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions

val Project.codecov get() = the<CodecovExtension>()

class CodecovPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            apply(plugin = "com.google.osdetector")
            apply(plugin = "de.undercouch.download")
            extensions.create<CodecovExtension>("codecov", project)
            setupTasks()
        }
    }

    private fun Project.setupTasks() {
        val downloadCodecov = tasks.register<Download>("downloadCodecov") {
            val codecovCache = gradle.gradleUserHomeDir.resolve("codecov")
            // no support for provider yet
            // https://github.com/michel-kraemer/gradle-download-task/issues/142
            src(provider {
                "https://github.com/codecov/codecov-exe/releases/download/${codecov.version.get()}/${codecovPackageName()}"
            })
            dest(provider {
                codecovCache.resolve("${codecov.version.get()}/codecov-executable")
            })
            downloadTaskDir(codecovCache)
            onlyIfModified(true)
            useETag(true)
            tempAndMove(true)
        }
        tasks.register("uploadCodecov") {
            val reportFile = codecov.reportTask.map { it.reports.xml.outputLocation }
            dependsOn(codecov.reportTask, downloadCodecov)
            inputs.files(reportFile, downloadCodecov)
            inputs.property("token", codecov.token)
            doFirst {
                // chmod executable
                val executableFile = downloadCodecov.get().dest.toPath()
                Files.getFileAttributeView(executableFile, PosixFileAttributeView::class.java)?.let { posix ->
                    val perms = posix.readAttributes().permissions() + PosixFilePermissions.fromString("--x--x--x")
                    posix.setPermissions(perms)
                }
                exec {
                    executable(executableFile)
                    args("-f", reportFile.get(), "-t", codecov.token.get())
                }
            }
        }
    }

    private fun Project.codecovPackageName(): String {
        return when (the<OsDetector>().os) {
            "windows" -> "codecov-windows-x64.exe"
            "osx" -> "codecov-osx-x64"
            else -> "codecov-linux-x64"
        }
    }

}
