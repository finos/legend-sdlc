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

package org.finos.legend.sdlc.backend.fs;

import org.finos.legend.sdlc.backend.api.project.ProjectApi;
import org.finos.legend.sdlc.backend.api.spi.AbstractBackend;
import org.finos.legend.sdlc.backend.api.spi.BackendCapability;
import org.finos.legend.sdlc.backend.api.spi.BackendEnvironment;
import org.finos.legend.sdlc.backend.api.spi.BackendSession;
import org.finos.legend.sdlc.backend.api.spi.BackendSessionContext;
import org.finos.legend.sdlc.backend.api.user.UserApi;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;

import java.io.File;
import java.util.EnumSet;
import java.util.Objects;

/**
 * The file-system backend: local git storage under a configured root directory, refit from the pre-SPI
 * file-system server ({@code legend-sdlc-server-fs}) onto the backend SPI. Every project is a git repository
 * under the root; the project source is its {@code master} branch, workspaces are branches named
 * {@code workspace/{user}/{id}}. It supplies the storage provider and native project/workspace/user lifecycle,
 * declares only {@link BackendCapability#USER_WORKSPACES}, and inherits every other behavior from the L4
 * defaults over the storage SPI.
 */
public class FileSystemBackend extends AbstractBackend
{
    public static final String TYPE = "fileSystem";

    private final String rootDirectory;

    public FileSystemBackend(String rootDirectory, BackendEnvironment environment)
    {
        super(TYPE, EnumSet.of(BackendCapability.USER_WORKSPACES), environment);
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory may not be null");
        File rootDirectoryFile = new File(rootDirectory);
        if (!rootDirectoryFile.exists() && !rootDirectoryFile.mkdirs())
        {
            throw new LegendSDLCException("Failed to create root directory: " + rootDirectory, 500);
        }
    }

    @Override
    public BackendSession newSession(BackendSessionContext context)
    {
        return new Session(context);
    }

    public class Session extends AbstractBackend.Session
    {
        private final FileSystemProjectFileAccessProvider fileAccessProvider;

        protected Session(BackendSessionContext context)
        {
            super(context);
            // the provider is session-scoped: workspace specifications with a null user id resolve against
            // the session user
            this.fileAccessProvider = new FileSystemProjectFileAccessProvider(FileSystemBackend.this.rootDirectory, getUserId());
        }

        @Override
        protected ProjectFileAccessProvider getProjectFileAccessProvider()
        {
            return this.fileAccessProvider;
        }

        @Override
        public ProjectApi getProjectApi()
        {
            return new FileSystemProjectApi(FileSystemBackend.this, this.fileAccessProvider);
        }

        @Override
        public WorkspaceApi getWorkspaceApi()
        {
            return new FileSystemWorkspaceApi(FileSystemBackend.this, this.fileAccessProvider);
        }

        @Override
        public UserApi getUserApi()
        {
            return new FileSystemUserApi(getUserId());
        }
    }

    BackendEnvironment environment()
    {
        return getEnvironment();
    }
}
