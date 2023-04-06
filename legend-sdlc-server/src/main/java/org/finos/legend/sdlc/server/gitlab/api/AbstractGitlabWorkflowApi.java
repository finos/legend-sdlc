// Copyright 2022 Goldman Sachs
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

package org.finos.legend.sdlc.server.gitlab.api;

import org.eclipse.collections.api.map.primitive.LongObjectMap;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.impl.factory.primitive.LongObjectMaps;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.PipelineApi;
import org.gitlab4j.api.models.Pipeline;

import java.util.stream.Stream;

abstract class AbstractGitlabWorkflowApi extends GitLabApiWithFileAccess
{
    protected AbstractGitlabWorkflowApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
    }

    protected Pipeline getMergeRequestPipeline(long gitLabProjectId, long mergeRequestId, long pipelineId) throws GitLabApiException
    {
        return PagerTools.stream(getMergeRequestPipelines(gitLabProjectId, mergeRequestId))
                .filter(p -> (p.getId() != null) && (p.getId() == pipelineId))
                .findAny()
                .orElse(null);
    }

    protected Pager<Pipeline> getMergeRequestPipelines(long gitLabProjectId, long mergeRequestId) throws GitLabApiException
    {
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();
        return withRetries(() -> mergeRequestApi.getMergeRequestPipelines(gitLabProjectId, mergeRequestId, ITEMS_PER_PAGE));
    }

    protected Pipeline getRefPipeline(long gitLabProjectId, String ref, long pipelineId) throws GitLabApiException
    {
        PipelineApi pipelineApi = getGitLabApi().getPipelineApi();
        Pipeline pipeline = withRetries(() -> pipelineApi.getPipeline(gitLabProjectId, pipelineId));
        return ((pipeline != null) && ref.equals(pipeline.getRef())) ? pipeline : null;
    }

    protected Pager<Pipeline> getRefPipelines(long gitLabProjectId, String ref) throws GitLabApiException
    {
        PipelineApi pipelineApi = getGitLabApi().getPipelineApi();
        return withRetries(() -> pipelineApi.getPipelines(gitLabProjectId, null, null, ref, false, null, null, null, null, ITEMS_PER_PAGE));
    }

    protected LongObjectMap<Pipeline> indexPipelinesById(Pager<Pipeline> pager, boolean ignoreNullIds, boolean ignoreIdConflicts)
    {
        return indexPipelinesById(PagerTools.stream(pager), ignoreNullIds, ignoreIdConflicts);
    }

    protected LongObjectMap<Pipeline> indexPipelinesById(Stream<Pipeline> pipelines, boolean ignoreNullIds, boolean ignoreIdConflicts)
    {
        MutableLongObjectMap<Pipeline> map = LongObjectMaps.mutable.empty();
        pipelines.forEach(p ->
        {
            Long id = p.getId();
            if (id == null)
            {
                if (ignoreNullIds)
                {
                    return;
                }
                throw new RuntimeException("Pipeline with null id: " + p);
            }
            if (ignoreIdConflicts)
            {
                map.getIfAbsentPut(id, p);
            }
            else
            {
                Pipeline old = map.put(id, p);
                if (old != null)
                {
                    throw new RuntimeException("Conflict for id " + id);
                }
            }
        });
        return map;
    }
}
