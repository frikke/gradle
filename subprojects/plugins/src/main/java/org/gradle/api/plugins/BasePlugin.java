/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.plugins.BuildConfigurationRule;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.internal.DefaultBasePluginExtension;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * <p>A {@link org.gradle.api.Plugin} which defines a basic project lifecycle and some common convention properties.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/base_plugin.html">Base plugin reference</a>
 */
public class BasePlugin implements Plugin<Project> {
    public static final String CLEAN_TASK_NAME = LifecycleBasePlugin.CLEAN_TASK_NAME;
    public static final String ASSEMBLE_TASK_NAME = LifecycleBasePlugin.ASSEMBLE_TASK_NAME;
    public static final String BUILD_GROUP = LifecycleBasePlugin.BUILD_GROUP;

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);

        BasePluginExtension baseExtension = project.getExtensions().create(BasePluginExtension.class, "base", DefaultBasePluginExtension.class, project.getObjects());

        configureExtension(project, baseExtension);
        configureBuildConfigurationRule(project);
        configureArchiveDefaults(project, baseExtension);
        configureConfigurations(project);
        configureAssemble((ProjectInternal) project);
    }

    private void configureExtension(Project project, BasePluginExtension extension) {
        extension.getArchivesName().convention(project.getName());
        extension.getLibsDirectory().convention(project.getLayout().getBuildDirectory().dir("libs"));
        extension.getDistsDirectory().convention(project.getLayout().getBuildDirectory().dir("distributions"));
    }

    private void configureArchiveDefaults(final Project project, final BasePluginExtension extension) {
        project.getTasks().withType(AbstractArchiveTask.class).configureEach(task -> {
            if (task instanceof Jar) {
                task.getDestinationDirectory().convention(extension.getLibsDirectory());
            } else {
                task.getDestinationDirectory().convention(extension.getDistsDirectory());
            }

            task.getArchiveVersion().convention(
                project.provider(() -> project.getVersion() == Project.DEFAULT_VERSION ? null : project.getVersion().toString())
            );

            task.getArchiveBaseName().convention(extension.getArchivesName());
        });
    }

    private void configureBuildConfigurationRule(Project project) {
        project.getTasks().addRule(new BuildConfigurationRule(project.getConfigurations(), project.getTasks()));
    }

    private void configureConfigurations(final Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        ((ProjectInternal)project).getInternalStatus().convention("integration");

        final DeprecatableConfiguration archivesConfiguration = (DeprecatableConfiguration) configurations.maybeCreate(Dependency.ARCHIVES_CONFIGURATION).
            setDescription("Configuration for archive artifacts.");

        final DeprecatableConfiguration defaultConfiguration = (DeprecatableConfiguration) configurations.maybeCreate(Dependency.DEFAULT_CONFIGURATION).
            setDescription("Configuration for default artifacts.");

        final DefaultArtifactPublicationSet defaultArtifacts = project.getExtensions().create(
            "defaultArtifacts", DefaultArtifactPublicationSet.class, archivesConfiguration.getArtifacts()
        );

        archivesConfiguration.deprecateForResolution(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        defaultConfiguration.deprecateForResolution(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        archivesConfiguration.deprecateForDeclaration(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, JavaPlugin.API_CONFIGURATION_NAME);
        defaultConfiguration.deprecateForDeclaration(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, JavaPlugin.API_CONFIGURATION_NAME);

        configurations.all(configuration -> {
            if (!configuration.equals(archivesConfiguration)) {
                configuration.getArtifacts().configureEach(artifact -> {
                    if (configuration.isVisible()) {
                        defaultArtifacts.addCandidate(artifact);
                    }
                });
            }
        });
    }

    private void configureAssemble(final ProjectInternal project) {
        project.getTasks().named(ASSEMBLE_TASK_NAME, task -> {
            task.dependsOn(task.getProject().getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION).getAllArtifacts().getBuildDependencies());
        });
    }
}
