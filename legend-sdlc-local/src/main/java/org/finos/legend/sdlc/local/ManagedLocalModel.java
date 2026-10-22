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

import org.finos.legend.sdlc.core.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.core.project.ProjectStructureUpdater;
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.project.structure.ProjectStructurePlatformExtensions;
import org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtensionProvider;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The <b>structure-aware</b> tier of the local surface (re-architecture section 4.4's Form 1,
 * {@code ProjectType.MANAGED} — a fully managed project edited via the IDE instead of Studio): everything
 * {@link LocalModel} offers, plus project configuration updates — dependencies, structure version, group and
 * artifact ids — realized by the SDLC core's structure updater writing to the working tree.
 *
 * <p><b>The extension provider is an explicit input, and its absence is a supported mode.</b> A managed project
 * belongs to a deployment, and that deployment's {@link ProjectStructureExtensionProvider} is the one piece of
 * environment this tier can be told about (how a plugin obtains it — bundled jars or user configuration — is
 * plugin packaging, not an SDLC contract; see re-architecture section 4.6). When the provider is supplied,
 * configuration updates maintain the deployment's extension-managed files exactly as the server would. When it is
 * absent (<i>degraded mode</i>, decided in the Phase 4 design review and confirmed here): entity editing is
 * unaffected; configuration and structure updates proceed — {@code project.json}, and the structure-managed files
 * the version factories own, are written as usual, and any extension version already recorded in
 * {@code project.json} is preserved — but <b>extension-managed files are left untouched</b>, to be reconciled by
 * the deployment's extension when the change returns through the server. Setting a <i>new</i> extension version,
 * however, requires the provider: without it there is no way to compute what that version manages.
 */
public class ManagedLocalModel extends LocalModel
{
    private final ProjectStructureExtensionProvider extensionProvider;
    private final ProjectStructurePlatformExtensions platformExtensions;

    private ManagedLocalModel(Path root, ProjectStructureExtensionProvider extensionProvider, ProjectStructurePlatformExtensions platformExtensions)
    {
        super(root, platformExtensions);
        this.extensionProvider = extensionProvider;
        this.platformExtensions = platformExtensions;
    }

    /**
     * Open the model rooted at a directory with its deployment's extension provider. Pass null to operate in
     * degraded mode (see the class documentation).
     *
     * @param root              model root directory
     * @param extensionProvider the deployment's extension provider, or null for degraded mode
     * @return structure-aware handle on the model
     */
    public static ManagedLocalModel open(Path root, ProjectStructureExtensionProvider extensionProvider)
    {
        return open(root, extensionProvider, null);
    }

    /**
     * As {@link #open(Path, ProjectStructureExtensionProvider)}, additionally supplying the deployment's platform
     * extensions (platform version pins consulted by some structure versions when generating build files).
     *
     * @param root               model root directory
     * @param extensionProvider  the deployment's extension provider, or null for degraded mode
     * @param platformExtensions the deployment's platform extensions, or null
     * @return structure-aware handle on the model
     */
    public static ManagedLocalModel open(Path root, ProjectStructureExtensionProvider extensionProvider, ProjectStructurePlatformExtensions platformExtensions)
    {
        return new ManagedLocalModel(Objects.requireNonNull(root, "root may not be null"), extensionProvider, platformExtensions);
    }

    /**
     * The extension provider the handle was opened with; null in degraded mode.
     */
    public ProjectStructureExtensionProvider getExtensionProvider()
    {
        return this.extensionProvider;
    }

    /**
     * Start a configuration update. Set the changes on the returned update, then {@link ConfigurationUpdate#apply()
     * apply()} it.
     *
     * @return a new configuration update
     */
    public ConfigurationUpdate newConfigurationUpdate()
    {
        checkOpen();
        return new ConfigurationUpdate();
    }

    /**
     * A pending configuration update: accumulate changes, then {@link #apply()}. Unset aspects of the configuration
     * are preserved. Instances are single-use; apply() writes the update to the working tree and refreshes the
     * handle.
     */
    public class ConfigurationUpdate
    {
        private final ProjectConfigurationUpdater updater = ProjectConfigurationUpdater.newUpdater();
        private String message;
        private boolean applied;

        private ConfigurationUpdate()
        {
        }

        public ConfigurationUpdate withProjectStructureVersion(int version)
        {
            this.updater.setProjectStructureVersion(version);
            return this;
        }

        /**
         * Set the extension version. Requires the handle to have an extension provider: in degraded mode there is
         * no way to compute the files the extension version manages, and apply() will fail.
         */
        public ConfigurationUpdate withProjectStructureExtensionVersion(int extensionVersion)
        {
            this.updater.setProjectStructureExtensionVersion(extensionVersion);
            return this;
        }

        public ConfigurationUpdate withGroupId(String groupId)
        {
            this.updater.setGroupId(groupId);
            return this;
        }

        public ConfigurationUpdate withArtifactId(String artifactId)
        {
            this.updater.setArtifactId(artifactId);
            return this;
        }

        public ConfigurationUpdate withProjectDependencyToAdd(ProjectDependency dependency)
        {
            this.updater.addProjectDependencyToAdd(dependency);
            return this;
        }

        public ConfigurationUpdate withProjectDependencyToRemove(ProjectDependency dependency)
        {
            this.updater.addProjectDependencyToRemove(dependency);
            return this;
        }

        public ConfigurationUpdate withMetamodelDependencyToAdd(MetamodelDependency dependency)
        {
            this.updater.addMetamodelDependencyToAdd(dependency);
            return this;
        }

        public ConfigurationUpdate withMetamodelDependencyToRemove(MetamodelDependency dependency)
        {
            this.updater.addMetamodelDependencyToRemove(dependency);
            return this;
        }

        /**
         * Description of the change (the local counterpart of a commit message).
         */
        public ConfigurationUpdate withMessage(String message)
        {
            this.message = message;
            return this;
        }

        /**
         * Write the update to the working tree: {@code project.json}, entity files whose serialization or location
         * the new structure prescribes differently, structure-managed build files, and — when the handle has an
         * extension provider — extension-managed files. The handle is refreshed afterwards, so its configuration
         * and structure reflect the update.
         */
        public void apply()
        {
            if (this.applied)
            {
                throw new IllegalStateException("This update has already been applied");
            }
            ManagedLocalModel model = ManagedLocalModel.this;
            model.usableStructure();
            ProjectStructureUpdater.newUpdateBuilder(model.fileAccessProvider, model.projectId, this.updater.withProjectId(model.projectId))
                    .withMessage((this.message == null) ? "Update project configuration" : this.message)
                    .withProjectStructureExtensionProvider(model.extensionProvider)
                    .withProjectStructurePlatformExtensions(model.platformExtensions)
                    .update();
            this.applied = true;
            model.refresh();
        }
    }
}
