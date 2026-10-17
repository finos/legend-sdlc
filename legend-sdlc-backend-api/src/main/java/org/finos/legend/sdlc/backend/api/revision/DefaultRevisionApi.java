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

package org.finos.legend.sdlc.backend.api.revision;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.finos.legend.sdlc.backend.api.spi.Backend;
import org.finos.legend.sdlc.backend.api.spi.BackendCapability;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.revision.RevisionStatus;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.files.ProjectPaths;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.structure.EntitySourceDirectory;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.finos.legend.sdlc.tools.entity.EntityPaths;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generic {@link RevisionApi} over a {@link ProjectFileAccessProvider}: the project, package, and entity revision
 * contexts are the provider's revision access contexts, path-scoped through the project structure where the scope
 * is a package or entity.
 * <p>
 * {@link #getRevisionStatus} has no generic implementation over the storage SPI (it reports which workspaces,
 * versions, and patches contain a revision — an enumeration only the backend can answer natively) and throws
 * status 501; backends implement it natively.
 */
public class DefaultRevisionApi implements RevisionApi
{
    private final Backend backend;
    private final ProjectFileAccessProvider fileAccessProvider;

    /**
     * @param backend            backend whose declared capabilities gate scoped access (version/patch sources)
     * @param fileAccessProvider the backend's file access provider
     */
    public DefaultRevisionApi(Backend backend, ProjectFileAccessProvider fileAccessProvider)
    {
        this.backend = Objects.requireNonNull(backend, "backend may not be null");
        this.fileAccessProvider = Objects.requireNonNull(fileAccessProvider, "fileAccessProvider may not be null");
    }

    @Override
    public RevisionAccessContext getRevisionContext(String projectId, SourceSpecification sourceSpecification)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        LegendSDLCException.validateNonNull(sourceSpecification, "sourceSpecification may not be null", 400);
        BackendCapability.checkSourceScope(this.backend, sourceSpecification);
        return new ProviderRevisionAccessContextWrapper(this.fileAccessProvider.getRevisionAccessContext(projectId, sourceSpecification));
    }

    @Override
    public RevisionAccessContext getPackageRevisionContext(String projectId, SourceSpecification sourceSpecification, String packagePath)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        LegendSDLCException.validateNonNull(sourceSpecification, "sourceSpecification may not be null", 400);
        LegendSDLCException.validateNonNull(packagePath, "packagePath may not be null", 400);
        if (!EntityPaths.isValidPackagePath(packagePath))
        {
            throw new LegendSDLCException("Invalid package path: " + packagePath, 400);
        }
        BackendCapability.checkSourceScope(this.backend, sourceSpecification);

        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(this.fileAccessProvider.getFileAccessContext(projectId, sourceSpecification, null));
        MutableList<String> directories = Iterate.collectWith(projectStructure.getEntitySourceDirectories(), EntitySourceDirectory::packagePathToFilePath, packagePath, Lists.mutable.empty());
        MutableList<String> canonicalDirectories = ProjectPaths.canonicalizeAndReduceDirectories(directories);
        return new ProviderRevisionAccessContextWrapper(this.fileAccessProvider.getRevisionAccessContext(projectId, sourceSpecification, canonicalDirectories), new PackageablePathExceptionProcessor(packagePath, canonicalDirectories));
    }

    @Override
    public RevisionAccessContext getEntityRevisionContext(String projectId, SourceSpecification sourceSpecification, String entityPath)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        LegendSDLCException.validateNonNull(sourceSpecification, "sourceSpecification may not be null", 400);
        LegendSDLCException.validateNonNull(entityPath, "entityPath may not be null", 400);
        if (!EntityPaths.isValidEntityPath(entityPath))
        {
            throw new LegendSDLCException("Invalid entity path: " + entityPath, 400);
        }
        BackendCapability.checkSourceScope(this.backend, sourceSpecification);

        ProjectFileAccessProvider.FileAccessContext fileAccessContext = this.fileAccessProvider.getFileAccessContext(projectId, sourceSpecification, null);
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(fileAccessContext);
        String filePath = projectStructure.findEntityFile(entityPath, fileAccessContext);
        if (filePath == null)
        {
            throw new LegendSDLCException("Cannot find entity \"" + entityPath + "\" in " + sourceSpecification + " of project " + projectId, 404);
        }
        String canonicalFilePath = ProjectPaths.canonicalizeFile(filePath);
        return new ProviderRevisionAccessContextWrapper(this.fileAccessProvider.getRevisionAccessContext(projectId, sourceSpecification, Collections.singleton(canonicalFilePath)), new PackageablePathExceptionProcessor(entityPath, canonicalFilePath));
    }

    @Override
    public RevisionStatus getRevisionStatus(String projectId, String revisionId)
    {
        throw new LegendSDLCException("Revision status is not supported by this backend", 501);
    }

    private static class ProviderRevisionAccessContextWrapper implements RevisionAccessContext
    {
        private final ProjectFileAccessProvider.RevisionAccessContext revisionAccessContext;
        private final Function<? super LegendSDLCException, ? extends LegendSDLCException> exceptionProcessor;

        private ProviderRevisionAccessContextWrapper(ProjectFileAccessProvider.RevisionAccessContext revisionAccessContext, Function<? super LegendSDLCException, ? extends LegendSDLCException> exceptionProcessor)
        {
            this.revisionAccessContext = revisionAccessContext;
            this.exceptionProcessor = exceptionProcessor;
        }

        private ProviderRevisionAccessContextWrapper(ProjectFileAccessProvider.RevisionAccessContext revisionAccessContext)
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
            catch (LegendSDLCException e)
            {
                throw process(e);
            }
        }

        @Override
        public Revision getBaseRevision()
        {
            try
            {
                return this.revisionAccessContext.getBaseRevision();
            }
            catch (LegendSDLCException e)
            {
                throw process(e);
            }
        }

        @Override
        public Revision getCurrentRevision()
        {
            try
            {
                return this.revisionAccessContext.getCurrentRevision();
            }
            catch (LegendSDLCException e)
            {
                throw process(e);
            }
        }

        @Override
        public List<Revision> getRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
        {
            try
            {
                return this.revisionAccessContext.getAllRevisions(predicate, since, until, limit).collect(Collectors.toList());
            }
            catch (LegendSDLCException e)
            {
                throw process(e);
            }
        }

        private LegendSDLCException process(LegendSDLCException e)
        {
            if (this.exceptionProcessor != null)
            {
                LegendSDLCException processedException = this.exceptionProcessor.apply(e);
                if (processedException != null)
                {
                    return processedException;
                }
            }
            return e;
        }
    }

    /**
     * Provider exceptions name file paths; when the context is scoped to a package or entity, rewrite those to
     * the packageable path the caller asked about.
     */
    private static class PackageablePathExceptionProcessor implements Function<LegendSDLCException, LegendSDLCException>
    {
        private final String packageablePath;
        private final ListIterable<String> filePaths;

        private PackageablePathExceptionProcessor(String packageablePath, ListIterable<String> filePaths)
        {
            this.packageablePath = packageablePath;
            this.filePaths = filePaths;
        }

        private PackageablePathExceptionProcessor(String packageablePath, String filePath)
        {
            this(packageablePath, Lists.immutable.with(filePath));
        }

        @Override
        public LegendSDLCException apply(LegendSDLCException e)
        {
            String message = e.getMessage();
            ListIterable<String> found = this.filePaths.select(message::contains);
            if (found.size() == 1)
            {
                String newMessage = message.replace(found.get(0), this.packageablePath);
                if (!newMessage.equals(message))
                {
                    return new LegendSDLCException(newMessage, e.getStatusCode(), e);
                }
            }
            else if (found.notEmpty())
            {
                String anyFoundPattern = LazyIterate.collect(found, Pattern::quote).makeString("((", ")|(", "))");
                String patternString = "\\{?+(" + anyFoundPattern + ",\\s*+)*+" + anyFoundPattern + "}?+";
                String newMessage = message.replaceAll(patternString, this.packageablePath);
                if (!newMessage.equals(message))
                {
                    return new LegendSDLCException(newMessage, e.getStatusCode(), e);
                }
            }
            return e;
        }
    }
}
