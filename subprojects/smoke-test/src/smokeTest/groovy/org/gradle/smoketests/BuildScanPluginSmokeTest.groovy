/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber
import org.junit.Assume

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

// https://plugins.gradle.org/plugin/com.gradle.enterprise
class BuildScanPluginSmokeTest extends AbstractSmokeTest {

    enum CI {
        TEAM_CITY(
            "v0.33",
            "https://raw.githubusercontent.com/etiennestuder/teamcity-build-scan-plugin/%s/agent/src/main/resources/build-scan-init.gradle",
            "teamCityBuildScanPlugin"
        ),
        JENKINS(
            "gradle-2.8",
            "https://raw.githubusercontent.com/jenkinsci/gradle-plugin/%s/src/main/resources/hudson/plugins/gradle/injection/init-script.gradle",
            "jenkinsGradlePlugin"
        ),
        BAMBOO(
            "gradle-enterprise-bamboo-plugin-1.1.0",
            "https://raw.githubusercontent.com/gradle/gradle-enterprise-bamboo-plugin/%s/src/main/resources/gradle-enterprise/gradle/gradle-enterprise-init-script.gradle",
            "ge-plugin"
        );

        String gitRef
        String urlTemplate
        String propPrefix

        CI(String gitRef, String urlTemplate, String propPrefix) {
            this.gitRef = gitRef
            this.urlTemplate = urlTemplate
            this.propPrefix = propPrefix
        }

        String getUrl() {
            return String.format(urlTemplate, gitRef)
        }
    }

    private static final Map<CI, String> CI_INJECTION_SCRIPT_CONTENTS = new ConcurrentHashMap<>()

    private static String getCiInjectionScriptContent(CI ci) {
        return CI_INJECTION_SCRIPT_CONTENTS.computeIfAbsent(ci) { new URL(it.getUrl()).getText(StandardCharsets.UTF_8.name()) }
    }

    private static final List<String> UNSUPPORTED = [
        "2.4.2",
        "2.4.1",
        "2.4",
        "2.3",
        "2.2.1",
        "2.2",
        "2.1",
        "2.0.2",
        "2.0.1",
        "2.0",
        "1.16",
        "1.15",
        "1.14"
    ]

    private static final List<String> SUPPORTED = [
        "3.0",
        "3.1",
        "3.1.1",
        "3.2",
        "3.2.1",
        "3.3",
        "3.3.1",
        "3.3.2",
        "3.3.3",
        "3.3.4",
        "3.4",
        "3.4.1",
        "3.5",
        "3.5.1",
        "3.5.2",
        "3.6",
        "3.6.1",
        "3.6.2",
        "3.6.3",
        "3.6.4",
        "3.7",
        "3.7.1",
        "3.7.2",
        "3.8",
        "3.8.1",
        "3.9",
        "3.10",
        "3.10.1",
        "3.10.2",
        "3.10.3",
        // "3.11", This doesn't work on Java 8, so let's not test it.
        "3.11.1",
        "3.11.2",
        "3.11.3",
        "3.11.4",
        "3.12",
        "3.12.1",
        "3.12.2",
        "3.12.3",
        "3.12.4",
        "3.12.5",
        "3.12.6",
        "3.13",
        "3.13.1",
        "3.13.2",
        "3.13.3",
        "3.13.4"
    ]

    // Current injection scripts support Gradle Enterprise plugin 3.3 and above
    private static final List<String> SUPPORTED_BY_CI_INJECTION = SUPPORTED
        .findAll { VersionNumber.parse("3.3") <= VersionNumber.parse(it) }

    private static final VersionNumber FIRST_VERSION_SUPPORTING_CONFIGURATION_CACHE = VersionNumber.parse("3.4")
    private static final VersionNumber FIRST_VERSION_SUPPORTING_GRADLE_8_CONFIGURATION_CACHE = VersionNumber.parse("3.12")
    private static final VersionNumber FIRST_VERSION_CALLING_BUILD_PATH = VersionNumber.parse("3.13.1")

    @Requires(IntegTestPreconditions.IsConfigCached)
    def "can use plugin #version with Gradle 8 configuration cache"() {
        given:
        def versionNumber = VersionNumber.parse(version)
        Assume.assumeFalse(versionNumber < FIRST_VERSION_SUPPORTING_GRADLE_8_CONFIGURATION_CACHE)

        when:
        usePluginVersion version

        then:
        scanRunner()
            .expectLegacyDeprecationWarningIf(versionNumber < FIRST_VERSION_CALLING_BUILD_PATH,
                "The BuildIdentifier.getName() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Use getBuildPath() to get a unique identifier for the build. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation"
            ).build().output.contains("Build scan written to")

        where:
        version << SUPPORTED
    }

    @Requires(IntegTestPreconditions.NotConfigCached)
    def "can use plugin #version"() {
        given:
        def versionNumber = VersionNumber.parse(version)
        Assume.assumeFalse(versionNumber < FIRST_VERSION_SUPPORTING_CONFIGURATION_CACHE)

        when:
        usePluginVersion version

        then:
        scanRunner()
            .expectLegacyDeprecationWarningIf(versionNumber < FIRST_VERSION_CALLING_BUILD_PATH,
                "The BuildIdentifier.getName() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Use getBuildPath() to get a unique identifier for the build. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation"
            )
            .build().output.contains("Build scan written to")

        where:
        version << SUPPORTED
    }

    def "cannot use plugin #version"() {
        when:
        usePluginVersion version

        and:
        def output = runner("--stacktrace")
            .buildAndFail().output

        then:
        output.contains(GradleEnterprisePluginManager.OLD_SCAN_PLUGIN_VERSION_MESSAGE)

        where:
        version << UNSUPPORTED
    }

    @Requires(IntegTestPreconditions.NotConfigCached)
    def "can inject plugin #pluginVersion in #ci using '#ciScriptVersion' script version"() {
        def versionNumber = VersionNumber.parse(pluginVersion)
        def initScript = "init-script.gradle"
        file(initScript) << getCiInjectionScriptContent(ci)

        // URL is not relevant as long as it's valid due to the `-Dscan.dump` parameter
        file("gradle.properties") << """
            systemProp.${ci.propPrefix}.gradle-enterprise.plugin.version=$pluginVersion
            systemProp.${ci.propPrefix}.init-script.name=$initScript
            systemProp.${ci.propPrefix}.gradle-enterprise.url=http://localhost:5086
        """.stripIndent()

        setupJavaProject()

        expect:
        scanRunner("--init-script", initScript)
            .expectLegacyDeprecationWarningIf(versionNumber < FIRST_VERSION_CALLING_BUILD_PATH,
                "The BuildIdentifier.getName() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Use getBuildPath() to get a unique identifier for the build. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation"
            )
            .build().output.contains("Build scan written to")

        where:
        [ci, pluginVersion] << [CI.values(), SUPPORTED_BY_CI_INJECTION].combinations()
        ciScriptVersion = ci.gitRef
    }

    BuildResult build(String... args) {
        scanRunner(args).build()
    }

    SmokeTestGradleRunner scanRunner(String... args) {
        runner("build", "-Dscan.dump", *args).forwardOutput()
    }

    void usePluginVersion(String version) {
        def gradleEnterprisePlugin = VersionNumber.parse(version) >= VersionNumber.parse("3.0")
        if (gradleEnterprisePlugin) {
            settingsFile << """
                plugins {
                    id "com.gradle.enterprise" version "$version"
                }

                gradleEnterprise {
                    buildScan {
                        termsOfServiceUrl = 'https://gradle.com/terms-of-service'
                        termsOfServiceAgree = 'yes'
                    }
                }
            """
        } else {
            buildFile << """
                plugins {
                    id "com.gradle.build-scan" version "$version"
                }

                buildScan {
                    termsOfServiceUrl = 'https://gradle.com/terms-of-service'
                    termsOfServiceAgree = 'yes'
                }
            """
        }

        setupJavaProject()
    }

    private setupJavaProject() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
            }
        """

        file("src/main/java/MySource.java") << """
            public class MySource {
                public static boolean isTrue() { return true; }
            }
        """

        file("src/test/java/MyTest.java") << """
            import org.junit.*;

            public class MyTest {
               @Test
               public void test() {
                  Assert.assertTrue(MySource.isTrue());
               }
            }
        """
    }
}
