// Copyright 2020 Goldman Sachs
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

package org.finos.legend.sdlc.server.project;

import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.sdlc.domain.model.version.VersionId;

import java.util.Map;
import java.util.Objects;

class CachingProjectFileAccessProvider implements ProjectFileAccessProvider
{
    private final ProjectFileAccessProvider delegate;
    private final Map<CacheKey, CachingFileAccessContext> cache = Maps.mutable.empty();

    private CachingProjectFileAccessProvider(ProjectFileAccessProvider delegate)
    {
        this.delegate = delegate;
    }

    // File Access Context

    @Override
    public FileAccessContext getFileAccessContext(String projectId, String workspaceId, WorkspaceAccessType workspaceAccessType, String revisionId)
    {
        if (revisionId == null)
        {
            return this.delegate.getFileAccessContext(projectId, workspaceId, workspaceAccessType, null);
        }

        CacheKey cacheKey = getCacheKey(projectId, workspaceId, revisionId);
        synchronized (this.cache)
        {
            CachingFileAccessContext fileAccessContext = this.cache.get(cacheKey);
            if (fileAccessContext == null)
            {
                fileAccessContext = CachingFileAccessContext.wrap(this.delegate.getFileAccessContext(projectId, workspaceId, workspaceAccessType, revisionId));
                if (fileAccessContext != null)
                {
                    this.cache.put(cacheKey, fileAccessContext);
                }
            }
            return fileAccessContext;
        }
    }

    @Override
    public FileAccessContext getFileAccessContext(String projectId, VersionId versionId)
    {
        CacheKey cacheKey = getCacheKey(projectId, versionId);
        synchronized (this.cache)
        {
            CachingFileAccessContext fileAccessContext = this.cache.get(cacheKey);
            if (fileAccessContext == null)
            {
                fileAccessContext = CachingFileAccessContext.wrap(this.delegate.getFileAccessContext(projectId, versionId));
                if (fileAccessContext != null)
                {
                    this.cache.put(cacheKey, fileAccessContext);
                }
            }
            return fileAccessContext;
        }
    }

    // Revision Access Context

    @Override
    public RevisionAccessContext getRevisionAccessContext(String projectId, String workspaceId, WorkspaceAccessType workspaceAccessType, String path)
    {
        return this.delegate.getRevisionAccessContext(projectId, workspaceId, workspaceAccessType, path);
    }

    @Override
    public RevisionAccessContext getRevisionAccessContext(String projectId, String workspaceId, WorkspaceAccessType workspaceAccessType, Iterable<? extends String> paths)
    {
        return this.delegate.getRevisionAccessContext(projectId, workspaceId, workspaceAccessType, paths);
    }

    @Override
    public RevisionAccessContext getRevisionAccessContext(String projectId, VersionId versionId, String path)
    {
        return this.delegate.getRevisionAccessContext(projectId, versionId, path);
    }

    @Override
    public RevisionAccessContext getRevisionAccessContext(String projectId, VersionId versionId, Iterable<? extends String> paths)
    {
        return this.delegate.getRevisionAccessContext(projectId, versionId, paths);
    }

    // File Modification Access Context

    @Override
    public FileModificationContext getFileModificationContext(String projectId, String workspaceId, WorkspaceAccessType workspaceAccessType, String revisionId)
    {
        return this.delegate.getFileModificationContext(projectId, workspaceId, workspaceAccessType, revisionId);
    }

    public void clearCache()
    {
        synchronized (this.cache)
        {
            this.cache.clear();
        }
    }

    public void clearCache(String projectId, String workspaceId, String revisionId)
    {
        if (revisionId != null)
        {
            CacheKey cacheKey = getCacheKey(projectId, workspaceId, revisionId);
            synchronized (this.cache)
            {
                this.cache.remove(cacheKey);
            }
        }
    }

    public void clearCache(String projectId, VersionId versionId)
    {
        CacheKey cacheKey = getCacheKey(projectId, versionId);
        synchronized (this.cache)
        {
            this.cache.remove(cacheKey);
        }
    }

    private CacheKey getCacheKey(String projectId, String workspaceId, String revisionId)
    {
        return (workspaceId == null) ? new ProjectRevisionCacheKey(projectId, revisionId) : new ProjectWorkspaceRevisionCacheKey(projectId, workspaceId, revisionId);
    }

    private CacheKey getCacheKey(String projectId, VersionId versionId)
    {
        return new ProjectVersionCacheKey(projectId, versionId);
    }

    static CachingProjectFileAccessProvider wrap(ProjectFileAccessProvider projectFileAccessProvider)
    {
        if (projectFileAccessProvider == null)
        {
            return null;
        }
        if (projectFileAccessProvider instanceof CachingProjectFileAccessProvider)
        {
            return (CachingProjectFileAccessProvider)projectFileAccessProvider;
        }
        return new CachingProjectFileAccessProvider(projectFileAccessProvider);
    }

    private interface CacheKey
    {
    }

    private static class ProjectRevisionCacheKey implements CacheKey
    {
        private final String projectId;
        private final String revisionId;

        private ProjectRevisionCacheKey(String projectId, String revisionId)
        {
            this.projectId = projectId;
            this.revisionId = revisionId;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }

            if ((other == null) || (this.getClass() != other.getClass()))
            {
                return false;
            }

            ProjectRevisionCacheKey that = (ProjectRevisionCacheKey)other;
            return Objects.equals(this.projectId, that.projectId) && Objects.equals(this.revisionId, that.revisionId);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(this.projectId) + 43 * Objects.hashCode(this.revisionId);
        }
    }

    private static class ProjectWorkspaceRevisionCacheKey implements CacheKey
    {
        private final String projectId;
        private final String workspaceId;
        private final String revisionId;

        private ProjectWorkspaceRevisionCacheKey(String projectId, String workspaceId, String revisionId)
        {
            this.projectId = projectId;
            this.workspaceId = workspaceId;
            this.revisionId = revisionId;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }

            if ((other == null) || (this.getClass() != other.getClass()))
            {
                return false;
            }

            ProjectWorkspaceRevisionCacheKey that = (ProjectWorkspaceRevisionCacheKey)other;
            return Objects.equals(this.projectId, that.projectId) &&
                    Objects.equals(this.workspaceId, that.workspaceId) &&
                    Objects.equals(this.revisionId, that.revisionId);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(this.projectId) + 17 * (Objects.hashCode(this.workspaceId) + 17 * Objects.hashCode(this.revisionId));
        }
    }

    private static class ProjectVersionCacheKey implements CacheKey
    {
        private final String projectId;
        private final VersionId versionId;

        private ProjectVersionCacheKey(String projectId, VersionId versionId)
        {
            this.projectId = projectId;
            this.versionId = versionId;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }

            if ((other == null) || (this.getClass() != other.getClass()))
            {
                return false;
            }

            ProjectVersionCacheKey that = (ProjectVersionCacheKey)other;
            return Objects.equals(this.projectId, that.projectId) && Objects.equals(this.versionId, that.versionId);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(this.projectId) + 41 * Objects.hashCode(this.versionId);
        }
    }
}
