/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Operations on files and directories"

errorprone {
    disabledChecks.addAll(
        "ImmutableEnumChecker", // 2 occurrences
        "InlineMeSuggester", // 1 occurrences
        "InconsistentCapitalization", // 2 occurrences
        "ReferenceEquality", // 2 occurrences
        "OperatorPrecedence", // 5 occurrences
    )
}

strictCompile {
    ignoreRawTypes() // some raw types remain, but must be cleaned up
}

dependencies {
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.fileCollections)
    api(projects.fileTemp)
    api(projects.files)
    api(projects.hashing)
    api(projects.logging)
    api(projects.modelCore)
    api(projects.native)
    api(projects.persistentCache)
    api(projects.resources)
    api(projects.serviceLookup)

    api(libs.ant)
    api(libs.commonsCompress)
    api(libs.groovy)
    api(libs.guava)
    api(libs.inject)
    api(libs.jsr305)

    implementation(libs.commonsIo)
    implementation(libs.groovyTemplates)
    implementation(libs.slf4jApi)
}
