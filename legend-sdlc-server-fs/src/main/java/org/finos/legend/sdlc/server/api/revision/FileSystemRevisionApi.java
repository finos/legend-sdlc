// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server.api.revision;

import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.revision.RevisionStatus;
import org.finos.legend.sdlc.server.api.entity.FileSystemApiWithFileAccess;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionAccessContext;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.exception.FSException;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.startup.FSConfiguration;

import javax.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FileSystemRevisionApi extends FileSystemApiWithFileAccess implements RevisionApi
{
    @Inject
    public FileSystemRevisionApi(FSConfiguration fsConfiguration)
    {
        super(fsConfiguration);
    }

    @Override
    public RevisionAccessContext getRevisionContext(String projectId, SourceSpecification sourceSpec)
    {
        return new ProjectFileRevisionAccessContextWrapper(getProjectFileAccessProvider().getRevisionAccessContext(projectId, sourceSpec));
    }

    @Override
    public RevisionAccessContext getPackageRevisionContext(String projectId, SourceSpecification sourceSpec, String packagePath)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public RevisionAccessContext getEntityRevisionContext(String projectId, SourceSpecification sourceSpec, String entityPath)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public RevisionStatus getRevisionStatus(String projectId, String revisionId)
    {
        throw FSException.unavailableFeature();
    }

    private static class ProjectFileRevisionAccessContextWrapper implements RevisionAccessContext
    {
        private final ProjectFileAccessProvider.RevisionAccessContext revisionAccessContext;
        private final Function<? super LegendSDLCServerException, ? extends LegendSDLCServerException> exceptionProcessor;

        private ProjectFileRevisionAccessContextWrapper(ProjectFileAccessProvider.RevisionAccessContext revisionAccessContext, Function<? super LegendSDLCServerException, ? extends LegendSDLCServerException> exceptionProcessor)
        {
            this.revisionAccessContext = revisionAccessContext;
            this.exceptionProcessor = exceptionProcessor;
        }

        private ProjectFileRevisionAccessContextWrapper(ProjectFileAccessProvider.RevisionAccessContext revisionAccessContext)
        {
            this(revisionAccessContext, null);
        }

        @Override
        public Revision getRevision(String revisionId)
        {
            try
            {
                return this.revisionAccessContext.getRevision(revisionId);
            }
            catch (LegendSDLCServerException e)
            {
                if (this.exceptionProcessor != null)
                {
                    LegendSDLCServerException processedException = this.exceptionProcessor.apply(e);
                    if (processedException != null)
                    {
                        throw processedException;
                    }
                }
                throw e;
            }
        }

        @Override
        public Revision getBaseRevision()
        {
            try
            {
                return this.revisionAccessContext.getBaseRevision();
            }
            catch (LegendSDLCServerException e)
            {
                if (this.exceptionProcessor != null)
                {
                    LegendSDLCServerException processedException = this.exceptionProcessor.apply(e);
                    if (processedException != null)
                    {
                        throw processedException;
                    }
                }
                throw e;
            }
        }

        @Override
        public Revision getCurrentRevision()
        {
            try
            {
                return this.revisionAccessContext.getCurrentRevision();
            }
            catch (LegendSDLCServerException e)
            {
                if (this.exceptionProcessor != null)
                {
                    LegendSDLCServerException processedException = this.exceptionProcessor.apply(e);
                    if (processedException != null)
                    {
                        throw processedException;
                    }
                }
                throw e;
            }
        }

        @Override
        public List<Revision> getRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
        {
            try
            {
                return this.revisionAccessContext.getAllRevisions(predicate, since, until, limit).collect(Collectors.toList());
            }
            catch (LegendSDLCServerException e)
            {
                if (this.exceptionProcessor != null)
                {
                    LegendSDLCServerException processedException = this.exceptionProcessor.apply(e);
                    if (processedException != null)
                    {
                        throw processedException;
                    }
                }
                throw e;
            }
        }
    }
}
