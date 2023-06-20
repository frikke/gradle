/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.file.DefaultFileSystemLocation;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.execution.model.InputNormalizer;
import org.gradle.internal.execution.workspace.WorkspaceProvider;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.UncategorizedBuildOperations;

import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.io.File;
import java.time.Duration;
import java.util.Optional;

import static org.gradle.internal.file.TreeType.DIRECTORY;
import static org.gradle.internal.file.TreeType.FILE;
import static org.gradle.internal.properties.InputBehavior.INCREMENTAL;
import static org.gradle.internal.properties.InputBehavior.NON_INCREMENTAL;

abstract class AbstractTransformExecution implements UnitOfWork {
    private static final CachingDisabledReason NOT_CACHEABLE = new CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Caching not enabled.");
    private static final String INPUT_ARTIFACT_PROPERTY_NAME = "inputArtifact";
    private static final String OUTPUT_DIRECTORY_PROPERTY_NAME = "outputDirectory";
    private static final String RESULTS_FILE_PROPERTY_NAME = "resultsFile";
    protected static final String INPUT_ARTIFACT_PATH_PROPERTY_NAME = "inputArtifactPath";
    protected static final String DEPENDENCIES_PROPERTY_NAME = "inputArtifactDependencies";
    protected static final String SECONDARY_INPUTS_HASH_PROPERTY_NAME = "inputPropertiesHash";
    protected final Transform transform;
    protected final File inputArtifact;
    private final TransformDependencies dependencies;
    private final TransformStepSubject subject;

    private final TransformExecutionListener transformExecutionListener;
    private final BuildOperationExecutor buildOperationExecutor;
    private final FileCollectionFactory fileCollectionFactory;

    private final Provider<FileSystemLocation> inputArtifactProvider;
    protected final InputFingerprinter inputFingerprinter;
    private final TransformWorkspaceServices workspaceServices;

    public AbstractTransformExecution(
        Transform transform,
        File inputArtifact,
        TransformDependencies dependencies,
        TransformStepSubject subject,

        TransformExecutionListener transformExecutionListener,
        BuildOperationExecutor buildOperationExecutor,
        FileCollectionFactory fileCollectionFactory,
        InputFingerprinter inputFingerprinter,
        TransformWorkspaceServices workspaceServices
    ) {
        this.transform = transform;
        this.inputArtifact = inputArtifact;
        this.dependencies = dependencies;
        this.inputArtifactProvider = Providers.of(new DefaultFileSystemLocation(inputArtifact));
        this.subject = subject;
        this.transformExecutionListener = transformExecutionListener;

        this.buildOperationExecutor = buildOperationExecutor;
        this.fileCollectionFactory = fileCollectionFactory;
        this.inputFingerprinter = inputFingerprinter;
        this.workspaceServices = workspaceServices;
    }

    @Override
    public Optional<String> getBuildOperationWorkType() {
        return Optional.of("TRANSFORM");
    }

    @Override
    public WorkOutput execute(ExecutionRequest executionRequest) {
        transformExecutionListener.beforeTransformExecution(transform, subject);
        try {
            return executeWithinTransformerListener(executionRequest);
        } finally {
            transformExecutionListener.afterTransformExecution(transform, subject);
        }
    }

    private WorkOutput executeWithinTransformerListener(ExecutionRequest executionRequest) {
        TransformExecutionResult result = buildOperationExecutor.call(new CallableBuildOperation<TransformExecutionResult>() {
            @Override
            public TransformExecutionResult call(BuildOperationContext context) {
                File workspace = executionRequest.getWorkspace();
                InputChangesInternal inputChanges = executionRequest.getInputChanges().orElse(null);
                TransformExecutionResult result = transform.transform(inputArtifactProvider, getOutputDir(workspace), dependencies, inputChanges);
                TransformExecutionResultSerializer resultSerializer = new TransformExecutionResultSerializer(getOutputDir(workspace));
                resultSerializer.writeToFile(getResultsFile(workspace), result);
                return result;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String displayName = transform.getDisplayName() + " " + inputArtifact.getName();
                return BuildOperationDescriptor.displayName(displayName)
                    .metadata(UncategorizedBuildOperations.TRANSFORM_ACTION)
                    .progressDisplayName(displayName);
            }
        });

        return new WorkOutput() {
            @Override
            public WorkResult getDidWork() {
                return WorkResult.DID_WORK;
            }

            @Override
            public Object getOutput() {
                return result;
            }
        };
    }

    @Override
    public Object loadAlreadyProducedOutput(File workspace) {
        TransformExecutionResultSerializer resultSerializer = new TransformExecutionResultSerializer(getOutputDir(workspace));
        return resultSerializer.readResultsFile(getResultsFile(workspace));
    }

    @Override
    public WorkspaceProvider getWorkspaceProvider() {
        return workspaceServices.getWorkspaceProvider();
    }

    @Override
    public InputFingerprinter getInputFingerprinter() {
        return inputFingerprinter;
    }

    private static File getOutputDir(File workspace) {
        return new File(workspace, "transformed");
    }

    private static File getResultsFile(File workspace) {
        return new File(workspace, "results.bin");
    }

    @Override
    public Optional<Duration> getTimeout() {
        return Optional.empty();
    }

    @Override
    public ExecutionBehavior getExecutionBehavior() {
        return transform.requiresInputChanges()
            ? ExecutionBehavior.INCREMENTAL
            : ExecutionBehavior.NON_INCREMENTAL;
    }

    @Override
    public void visitImplementations(ImplementationVisitor visitor) {
        visitor.visitImplementation(transform.getImplementationClass());
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    public void visitIdentityInputs(InputVisitor visitor) {
        // Emulate secondary inputs as a single property for now
        visitor.visitInputProperty(SECONDARY_INPUTS_HASH_PROPERTY_NAME, transform::getSecondaryInputHash);
        visitor.visitInputProperty(INPUT_ARTIFACT_PATH_PROPERTY_NAME, () ->
            // We always need the name as an input to the artifact transform,
            // since it is part of the ComponentArtifactIdentifier returned by the transform.
            // For absolute paths, the name is already part of the normalized path,
            // and for all the other normalization strategies we use the name directly.
            transform.getInputArtifactNormalizer() == InputNormalizer.ABSOLUTE_PATH
                ? inputArtifact.getAbsolutePath()
                : inputArtifact.getName());
        visitor.visitInputFileProperty(DEPENDENCIES_PROPERTY_NAME, NON_INCREMENTAL,
            new InputFileValueSupplier(
                dependencies,
                transform.getInputArtifactDependenciesNormalizer(),
                transform.getInputArtifactDependenciesDirectorySensitivity(),
                transform.getInputArtifactDependenciesLineEndingNormalization(),
                () -> dependencies.getFiles()
                    .orElse(FileCollectionFactory.empty())));
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    public void visitRegularInputs(InputVisitor visitor) {
        visitor.visitInputFileProperty(INPUT_ARTIFACT_PROPERTY_NAME, INCREMENTAL,
            new InputFileValueSupplier(
                inputArtifactProvider,
                transform.getInputArtifactNormalizer(),
                transform.getInputArtifactDirectorySensitivity(),
                transform.getInputArtifactLineEndingNormalization(),
                () -> fileCollectionFactory.fixed(inputArtifact)));
    }

    @Override
    public void visitOutputs(File workspace, OutputVisitor visitor) {
        File outputDir = getOutputDir(workspace);
        File resultsFile = getResultsFile(workspace);
        visitor.visitOutputProperty(OUTPUT_DIRECTORY_PROPERTY_NAME, DIRECTORY,
            OutputFileValueSupplier.fromStatic(outputDir, fileCollectionFactory.fixed(outputDir)));
        visitor.visitOutputProperty(RESULTS_FILE_PROPERTY_NAME, FILE,
            OutputFileValueSupplier.fromStatic(resultsFile, fileCollectionFactory.fixed(resultsFile)));
    }

    @Override
    public Optional<CachingDisabledReason> shouldDisableCaching(@Nullable OverlappingOutputs detectedOverlappingOutputs) {
        return transform.isCacheable()
            ? Optional.empty()
            : Optional.of(NOT_CACHEABLE);
    }

    @Override
    public String getDisplayName() {
        return transform.getDisplayName() + ": " + inputArtifact;
    }
}
