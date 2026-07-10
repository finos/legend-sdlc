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

package org.finos.legend.sdlc.backend.tck;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.legend.sdlc.backend.api.entity.EntityApi;
import org.finos.legend.sdlc.backend.api.project.ProjectApi;
import org.finos.legend.sdlc.backend.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.backend.api.revision.RevisionApi;
import org.finos.legend.sdlc.backend.api.spi.AbstractBackend;
import org.finos.legend.sdlc.backend.api.spi.Backend;
import org.finos.legend.sdlc.backend.api.spi.BackendCapability;
import org.finos.legend.sdlc.backend.api.spi.BackendEnvironment;
import org.finos.legend.sdlc.backend.api.spi.BackendSession;
import org.finos.legend.sdlc.backend.api.spi.BackendSessionContext;
import org.finos.legend.sdlc.backend.api.tools.BackgroundTaskProcessor;
import org.finos.legend.sdlc.backend.api.user.UserApi;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.project.files.InMemoryProjectFileAccessProvider;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.structure.ProjectStructurePlatformExtensions;
import org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtensionProvider;

import java.util.Collections;
import java.util.EnumSet;

/**
 * Runs the capability contract over a minimal {@link AbstractBackend} fixture (user workspaces only, no optional
 * capabilities declared) — certifying that the base class's gating satisfies the contract the suite states. The
 * first real in-repo runner is the in-memory backend of the extraction phase.
 */
public class TestMinimalBackendContract extends BackendContractTestSuite
{
    @Override
    protected Backend newBackend()
    {
        return new MinimalBackend();
    }

    private static class MinimalBackend extends AbstractBackend
    {
        MinimalBackend()
        {
            super("tck-minimal", EnumSet.of(BackendCapability.USER_WORKSPACES), new BackendEnvironment()
            {
                @Override
                public ObjectMapper getObjectMapper()
                {
                    return new ObjectMapper();
                }

                @Override
                public BackgroundTaskProcessor getTaskProcessor()
                {
                    return new BackgroundTaskProcessor(1);
                }

                @Override
                public ProjectStructureExtensionProvider getProjectStructureExtensionProvider()
                {
                    return new ProjectStructureExtensionProvider()
                    {
                        @Override
                        public Integer getLatestVersionForProjectStructureVersion(int projectStructureVersion)
                        {
                            return null;
                        }

                        @Override
                        public org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtension getProjectStructureExtension(int projectStructureVersion, int projectStructureExtensionVersion)
                        {
                            return null;
                        }
                    };
                }

                @Override
                public ProjectStructurePlatformExtensions getProjectStructurePlatformExtensions()
                {
                    return ProjectStructurePlatformExtensions.newPlatformExtensions(Collections.emptyList(), Collections.emptyList());
                }
            });
        }

        @Override
        public BackendSession newSession(BackendSessionContext context)
        {
            return new MinimalSession(context);
        }

        private class MinimalSession extends AbstractBackend.Session
        {
            private final ProjectFileAccessProvider provider = new InMemoryProjectFileAccessProvider("tck-user", "TCK User");

            MinimalSession(BackendSessionContext context)
            {
                super(context);
            }

            @Override
            protected ProjectFileAccessProvider getProjectFileAccessProvider()
            {
                return this.provider;
            }

            @Override
            public ProjectApi getProjectApi()
            {
                throw new UnsupportedOperationException("not exercised by the capability contract");
            }

            @Override
            public ProjectConfigurationApi getProjectConfigurationApi()
            {
                throw new UnsupportedOperationException("not exercised by the capability contract");
            }

            @Override
            public WorkspaceApi getWorkspaceApi()
            {
                throw new UnsupportedOperationException("not exercised by the capability contract");
            }

            @Override
            public RevisionApi getRevisionApi()
            {
                throw new UnsupportedOperationException("not exercised by the capability contract");
            }

            @Override
            public EntityApi getEntityApi()
            {
                throw new UnsupportedOperationException("not exercised by the capability contract");
            }

            @Override
            public UserApi getUserApi()
            {
                throw new UnsupportedOperationException("not exercised by the capability contract");
            }
        }
    }
}
