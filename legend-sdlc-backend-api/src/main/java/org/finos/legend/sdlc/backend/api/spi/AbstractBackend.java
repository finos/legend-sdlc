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

package org.finos.legend.sdlc.backend.api.spi;

import org.finos.legend.sdlc.backend.api.backup.BackupApi;
import org.finos.legend.sdlc.backend.api.build.BuildApi;
import org.finos.legend.sdlc.backend.api.comparison.ComparisonApi;
import org.finos.legend.sdlc.backend.api.comparison.DefaultComparisonApi;
import org.finos.legend.sdlc.backend.api.conflictresolution.ConflictResolutionApi;
import org.finos.legend.sdlc.backend.api.dependency.DefaultDependenciesApi;
import org.finos.legend.sdlc.backend.api.dependency.DependenciesApi;
import org.finos.legend.sdlc.backend.api.issue.IssueApi;
import org.finos.legend.sdlc.backend.api.patch.PatchApi;
import org.finos.legend.sdlc.backend.api.review.ReviewApi;
import org.finos.legend.sdlc.backend.api.version.VersionApi;
import org.finos.legend.sdlc.backend.api.workflow.WorkflowApi;
import org.finos.legend.sdlc.backend.api.workflow.WorkflowJobApi;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Base class wiring the L3-backed default implementations to the SPI: a backend extends this, supplies its
 * {@link ProjectFileAccessProvider} and the core lifecycle APIs on its {@link Session}, declares its
 * capabilities, and inherits generic behavior for everything the defaults cover. Accessors for undeclared
 * optional capabilities throw {@link UnsupportedCapabilityException}; a backend that declares a capability
 * overrides the corresponding accessor.
 */
public abstract class AbstractBackend implements Backend
{
    private final String type;
    private final Set<BackendCapability> capabilities;
    private final BackendEnvironment environment;

    protected AbstractBackend(String type, Set<BackendCapability> capabilities, BackendEnvironment environment)
    {
        this.type = Objects.requireNonNull(type, "type may not be null");
        Objects.requireNonNull(capabilities, "capabilities may not be null");
        this.capabilities = capabilities.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
        this.environment = Objects.requireNonNull(environment, "environment may not be null");
    }

    @Override
    public String getType()
    {
        return this.type;
    }

    @Override
    public Set<BackendCapability> getCapabilities()
    {
        return this.capabilities;
    }

    protected BackendEnvironment getEnvironment()
    {
        return this.environment;
    }

    /**
     * Base session: defaults for the generic APIs, capability-gated throws for the optional ones. Subclasses
     * implement the core APIs (projects, workspaces, revisions, entities, configuration, users) and the storage
     * provider, and override the optional accessors for capabilities they declare.
     */
    public abstract class Session implements BackendSession
    {
        private final BackendSessionContext context;

        protected Session(BackendSessionContext context)
        {
            this.context = Objects.requireNonNull(context, "context may not be null");
        }

        protected BackendSessionContext getContext()
        {
            return this.context;
        }

        @Override
        public String getUserId()
        {
            return this.context.getUserId();
        }

        /**
         * The backend's storage provider for this session — the L1 contract the generic defaults are built on.
         *
         * @return project file access provider
         */
        protected abstract ProjectFileAccessProvider getProjectFileAccessProvider();

        @Override
        public DependenciesApi getDependenciesApi()
        {
            return new DefaultDependenciesApi(getProjectApi(), getProjectConfigurationApi(), getRevisionApi());
        }

        @Override
        public ComparisonApi getComparisonApi()
        {
            return new DefaultComparisonApi(getProjectFileAccessProvider(), this::getReviewApi);
        }

        @Override
        public ReviewApi getReviewApi()
        {
            throw unsupported(BackendCapability.REVIEWS);
        }

        @Override
        public VersionApi getVersionApi()
        {
            throw unsupported(BackendCapability.VERSIONS);
        }

        @Override
        public PatchApi getPatchApi()
        {
            throw unsupported(BackendCapability.PATCHES);
        }

        @Override
        public WorkflowApi getWorkflowApi()
        {
            throw unsupported(BackendCapability.WORKFLOWS);
        }

        @Override
        public WorkflowJobApi getWorkflowJobApi()
        {
            throw unsupported(BackendCapability.WORKFLOWS);
        }

        @Override
        public BuildApi getBuildApi()
        {
            throw unsupported(BackendCapability.BUILDS);
        }

        @Override
        public BackupApi getBackupApi()
        {
            throw unsupported(BackendCapability.BACKUP);
        }

        @Override
        public ConflictResolutionApi getConflictResolutionApi()
        {
            throw unsupported(BackendCapability.CONFLICT_RESOLUTION);
        }

        @Override
        public IssueApi getIssueApi()
        {
            throw unsupported(BackendCapability.ISSUES);
        }

        protected UnsupportedCapabilityException unsupported(BackendCapability capability)
        {
            return new UnsupportedCapabilityException(capability, getType());
        }
    }
}
