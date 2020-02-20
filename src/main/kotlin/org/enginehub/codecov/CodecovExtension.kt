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

import org.gradle.api.Project
import org.gradle.kotlin.dsl.property
import org.gradle.testing.jacoco.tasks.JacocoReport

open class CodecovExtension(
    project: Project
) {
    val version = project.objects.property<String>().convention("1.10.0")
    val token = project.objects.property<String>().convention(System.getenv("CODECOV_TOKEN"))
    val reportTask = project.objects.property<JacocoReport>()
}
