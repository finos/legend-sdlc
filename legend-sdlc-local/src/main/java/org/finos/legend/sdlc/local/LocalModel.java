// Copyright 2026 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.sdlc.local;

import org.finos.legend.sdlc.core.entity.EntityAccessOperations;
import org.finos.legend.sdlc.core.entity.EntityModificationOperations;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.FileAccessContext;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.ProjectFile;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.structure.EntitySourceDirectory;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.finos.legend.sdlc.project.structure.ProjectStructurePlatformExtensions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A handle on a Legend model in a local directory — the working-copy façade of re-architecture section 4:
 * {@code open(root) → handle → close}, with entity reading and editing served by the SDLC core over local
 * working-copy storage. This class is the <b>entities-only</b> tier of the published surface (section 4.4's Form 2,
 * {@code ProjectType.EMBEDDED} — a model embedded in a larger project, where the host owns everything but the
 * entities); {@link ManagedLocalModel} layers the structure-aware tier (Form 1) on top. Opening a model requires no
 * container, no server, and no configuration beyond the directory itself.
 *
 * <p><b>Lifecycle and caching.</b> The handle resolves the project configuration and structure at open and caches
 * that resolution; everything else is read from the working tree live. Concretely: entity reads and listings always
 * reflect the files on disk at the moment of the call, but <i>where</i> entities live (the structure, from
 * {@code project.json}) is the cached resolution. If {@code project.json} changes underneath the handle — the user
 * edits it, the IDE's version control switches branches — call {@link #refresh()} to re-resolve; edits made through
 * the handle refresh it automatically. {@link #close()} releases the handle: it holds no system resources, but a
 * closed handle refuses further operations, which keeps lifecycle bugs loud.
 *
 * <p><b>Threading.</b> A handle is not thread-safe: callers serialize access to it (or confine it to one thread).
 * Distinct handles are independent, including handles on the same directory.
 *
 * <p><b>Validation.</b> {@link #validate()} reports configuration and layout findings as data; see
 * {@link LocalModelDiagnostic} for the boundary with the Engine's semantic validation. A model with configuration
 * errors can still be opened and validated — but entity and configuration operations fail until the underlying
 * problem is fixed, since their behavior would otherwise be defined by a configuration that could not be read.
 */
public class LocalModel implements AutoCloseable
{
    final Path root;
    final String projectId;
    final LocalProjectFileAccessProvider fileAccessProvider;
    final FileAccessContext fileAccessContext;
    private final ProjectStructurePlatformExtensions platformExtensions;

    private ProjectConfiguration configuration;
    private ProjectStructure structure;
    private Exception resolutionProblem;
    private boolean closed;

    LocalModel(Path root, ProjectStructurePlatformExtensions platformExtensions)
    {
        this.root = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.root))
        {
            throw new IllegalArgumentException("Not a directory: " + root);
        }
        this.projectId = String.valueOf(this.root.getFileName());
        this.platformExtensions = platformExtensions;
        this.fileAccessProvider = new LocalProjectFileAccessProvider(this.root, this.projectId);
        this.fileAccessContext = this.fileAccessProvider.getFileAccessContext(this.projectId, SourceSpecification.projectSourceSpecification(), null);
        resolve();
    }

    /**
     * Open the model rooted at a directory. The directory must exist; it need not contain a
     * {@code project.json} — a bare model is treated as project structure version 0.
     *
     * @param root model root directory
     * @return entities-only handle on the model
     */
    public static LocalModel open(Path root)
    {
        return new LocalModel(Objects.requireNonNull(root, "root may not be null"), null);
    }

    /**
     * The model root directory (absolute).
     */
    public Path getRoot()
    {
        return this.root;
    }

    /**
     * The project configuration as of the last resolution (open, {@link #refresh()}, or an edit through the
     * handle). Null if the model has no {@code project.json}.
     *
     * @return project configuration or null
     */
    public ProjectConfiguration getConfiguration()
    {
        checkOpen();
        return this.configuration;
    }

    // Entities

    /**
     * All entities of the model, deserialized from the working tree as it is now.
     */
    public List<Entity> getEntities()
    {
        return EntityAccessOperations.getEntities(usableStructure(), this.fileAccessContext, null, null, null, false);
    }

    /**
     * All entity paths of the model, from the working tree as it is now.
     */
    public List<String> getEntityPaths()
    {
        return EntityAccessOperations.getEntityPaths(usableStructure(), this.fileAccessContext, null, null, null);
    }

    /**
     * Get an entity by path.
     *
     * @throws org.finos.legend.sdlc.error.LegendSDLCException if there is no such entity or its file cannot be read
     */
    public Entity getEntity(String path)
    {
        return EntityAccessOperations.getEntity(usableStructure(), this.fileAccessContext, path, "local model at " + this.root);
    }

    public void createEntity(String path, String classifierPath, Map<String, ?> content)
    {
        applyEntityChanges("Create entity " + path, Collections.singletonList(EntityChange.newCreateEntity(path, classifierPath, content)));
    }

    public void updateEntity(String path, String classifierPath, Map<String, ?> content)
    {
        applyEntityChanges("Update entity " + path, Collections.singletonList(EntityChange.newModifyEntity(path, classifierPath, content)));
    }

    public void deleteEntity(String path)
    {
        applyEntityChanges("Delete entity " + path, Collections.singletonList(EntityChange.newDeleteEntity(path)));
    }

    /**
     * Apply a list of entity changes to the working tree. The changes are validated together, then written to the
     * source files the project structure prescribes. The message describes the change (it is the local counterpart
     * of a commit message; local storage records nothing, but the description travels through the storage SPI).
     *
     * <p>Note on {@code RENAME} changes: per the storage-SPI-wide semantics, a rename moves the entity file without
     * rewriting its content — the content still declares the old package and name, and serializers derive the
     * entity path from the content, so the model does not read cleanly until the content is updated too. There is
     * deliberately no rename convenience method on this class: a rename that keeps the model consistent is a
     * content rewrite ({@link #deleteEntity} plus {@link #createEntity} with updated package/name), and rewriting
     * references to the renamed entity is an Engine/IDE refactoring concern.
     *
     * @param message description of the change
     * @param changes entity changes
     */
    public void applyEntityChanges(String message, List<? extends EntityChange> changes)
    {
        ProjectStructure projectStructure = usableStructure();
        EntityModificationOperations.validateEntityChanges(changes);
        EntityModificationOperations.performChanges(projectStructure, this.fileAccessProvider, this.projectId, SourceSpecification.projectSourceSpecification(), null, message, changes);
        refresh();
    }

    // Lifecycle

    /**
     * Re-resolve the configuration and structure from the working tree, discarding the cached resolution. Call this
     * when {@code project.json} may have changed outside the handle; an IDE integration typically wires its file
     * watcher to this.
     */
    public void refresh()
    {
        checkOpen();
        resolve();
    }

    @Override
    public void close()
    {
        this.closed = true;
    }

    // Validation

    /**
     * Validate the model's configuration and layout, returning findings as data — an empty list means no findings.
     * This never throws for problems <i>in</i> the model; see {@link LocalModelDiagnostic} for what is (and is not)
     * covered.
     *
     * @return diagnostics, most severe findings not guaranteed first
     */
    public List<LocalModelDiagnostic> validate()
    {
        checkOpen();
        List<LocalModelDiagnostic> diagnostics = new ArrayList<>();
        if (this.resolutionProblem != null)
        {
            String message = (this.resolutionProblem.getMessage() == null) ? this.resolutionProblem.toString() : this.resolutionProblem.getMessage();
            diagnostics.add(new LocalModelDiagnostic(LocalModelDiagnostic.Severity.ERROR, LocalModelDiagnostic.Category.CONFIGURATION, message, ProjectStructure.PROJECT_CONFIG_PATH));
            return diagnostics;
        }
        validateConfiguration(diagnostics);
        if (this.structure != null)
        {
            validateEntityFiles(diagnostics);
        }
        return diagnostics;
    }

    private void validateConfiguration(List<LocalModelDiagnostic> diagnostics)
    {
        if (this.configuration == null)
        {
            // a bare model (no project.json) is legal: treated as structure version 0
            return;
        }
        if (!ProjectStructure.isValidProjectType(this.configuration.getProjectType()))
        {
            diagnostics.add(configDiagnostic(LocalModelDiagnostic.Severity.WARNING, "No valid project type recorded (found: " + this.configuration.getProjectType() + "); configuration updates will record MANAGED"));
        }
        // absent ids are legal on an embedded model (nothing is built from it); a managed model needs them, and a
        // present-but-invalid id is wrong for any type
        boolean managed = this.configuration.getProjectType() == ProjectType.MANAGED;
        if (this.configuration.getGroupId() == null)
        {
            if (managed)
            {
                diagnostics.add(configDiagnostic(LocalModelDiagnostic.Severity.WARNING, "No groupId recorded; configuration updates will fail until one is set"));
            }
        }
        else if (!ProjectStructure.isValidGroupId(this.configuration.getGroupId()))
        {
            diagnostics.add(configDiagnostic(LocalModelDiagnostic.Severity.ERROR, "Invalid groupId: " + this.configuration.getGroupId()));
        }
        if (this.configuration.getArtifactId() == null)
        {
            if (managed)
            {
                diagnostics.add(configDiagnostic(LocalModelDiagnostic.Severity.WARNING, "No artifactId recorded; configuration updates will fail until one is set"));
            }
        }
        else if (!ProjectStructure.isValidArtifactId(this.configuration.getArtifactId()))
        {
            diagnostics.add(configDiagnostic(LocalModelDiagnostic.Severity.ERROR, "Invalid artifactId: " + this.configuration.getArtifactId()));
        }
        if (this.configuration.getProjectDependencies() != null)
        {
            this.configuration.getProjectDependencies().forEach(dependency ->
            {
                if (ProjectStructure.isLegacyProjectDependency(dependency))
                {
                    diagnostics.add(configDiagnostic(LocalModelDiagnostic.Severity.WARNING, "Legacy project dependency (no group:artifact coordinates): " + dependency));
                }
                else if (!ProjectStructure.isProperProjectDependency(dependency))
                {
                    diagnostics.add(configDiagnostic(LocalModelDiagnostic.Severity.ERROR, "Improper project dependency: " + dependency));
                }
            });
        }
        if ((this.structure != null) && (this.configuration.getArtifactGenerations() != null))
        {
            this.configuration.getArtifactGenerations().forEach(generation ->
            {
                if (!this.structure.isSupportedArtifactType(generation.getType()))
                {
                    diagnostics.add(configDiagnostic(LocalModelDiagnostic.Severity.ERROR, "Artifact generation \"" + generation.getName() + "\" has type " + generation.getType() + ", which project structure version " + this.structure.getVersion() + " does not support"));
                }
            });
        }
    }

    private void validateEntityFiles(List<LocalModelDiagnostic> diagnostics)
    {
        for (EntitySourceDirectory sourceDirectory : this.structure.getEntitySourceDirectories())
        {
            try (Stream<ProjectFile> files = this.fileAccessContext.getFilesInDirectory(sourceDirectory.getDirectory()))
            {
                files.filter(file -> sourceDirectory.isPossiblyEntityFilePath(file.getPath()))
                        .forEach(file -> validateEntityFile(sourceDirectory, file, diagnostics));
            }
        }
    }

    private void validateEntityFile(EntitySourceDirectory sourceDirectory, ProjectFile file, List<LocalModelDiagnostic> diagnostics)
    {
        Entity entity;
        try
        {
            entity = sourceDirectory.deserialize(file);
        }
        catch (Exception e)
        {
            String message = (e.getMessage() == null) ? e.toString() : e.getMessage();
            diagnostics.add(new LocalModelDiagnostic(LocalModelDiagnostic.Severity.ERROR, LocalModelDiagnostic.Category.ENTITY, "Error deserializing entity file: " + message, file.getPath()));
            return;
        }
        String expectedPath = sourceDirectory.filePathToEntityPath(file.getPath());
        if (!expectedPath.equals(entity.getPath()))
        {
            diagnostics.add(new LocalModelDiagnostic(LocalModelDiagnostic.Severity.ERROR, LocalModelDiagnostic.Category.ENTITY, "Entity path \"" + entity.getPath() + "\" does not match the path implied by the file location (\"" + expectedPath + "\")", file.getPath()));
        }
    }

    private LocalModelDiagnostic configDiagnostic(LocalModelDiagnostic.Severity severity, String message)
    {
        return new LocalModelDiagnostic(severity, LocalModelDiagnostic.Category.CONFIGURATION, message, ProjectStructure.PROJECT_CONFIG_PATH);
    }

    // Internal state

    private void resolve()
    {
        this.configuration = null;
        this.structure = null;
        this.resolutionProblem = null;
        try
        {
            this.configuration = ProjectStructure.getProjectConfiguration(this.fileAccessContext);
            this.structure = ProjectStructure.getProjectStructure(this.configuration, this.platformExtensions);
        }
        catch (Exception e)
        {
            this.resolutionProblem = e;
        }
    }

    ProjectStructure usableStructure()
    {
        checkOpen();
        if (this.structure == null)
        {
            throw new IllegalStateException("The model at " + this.root + " cannot be operated on: its configuration could not be resolved (see validate()): " + this.resolutionProblem);
        }
        return this.structure;
    }

    void checkOpen()
    {
        if (this.closed)
        {
            throw new IllegalStateException("The handle on " + this.root + " is closed");
        }
    }

    @Override
    public String toString()
    {
        return "<" + getClass().getSimpleName() + " root=" + this.root + ">";
    }
}
