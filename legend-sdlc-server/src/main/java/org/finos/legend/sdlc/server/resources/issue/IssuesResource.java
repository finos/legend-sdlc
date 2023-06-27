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

package org.finos.legend.sdlc.server.resources.issue;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.finos.legend.sdlc.domain.model.issue.Issue;
import org.finos.legend.sdlc.server.application.issue.CreateIssueCommand;
import org.finos.legend.sdlc.server.domain.api.issue.IssueApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.BaseResource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/projects/{projectId}/issues")
@Api("Issues")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class IssuesResource extends BaseResource
{
    private final IssueApi issueApi;

    @Inject
    public IssuesResource(IssueApi issueApi)
    {
        this.issueApi = issueApi;
    }

    @GET
    @ApiOperation("Get all issues for the project")
    public List<Issue> getIssues(@PathParam("projectId") String projectId)
    {
        return executeWithLogging(
                "getting issues for project " + projectId,
                this.issueApi::getIssues,
                projectId
        );
    }

    @GET
    @Path("{issueId}")
    @ApiOperation("Get an issue for the project")
    public Issue getIssue(@PathParam("projectId") String projectId, @PathParam("issueId") String issueId)
    {
        return executeWithLogging(
                "getting issue " + issueId + " for project " + projectId,
                this.issueApi::getIssue,
                projectId,
                issueId
        );
    }

    @POST
    @ApiOperation("Create a new issue for the project")
    public Issue createIssue(@PathParam("projectId") String projectId, CreateIssueCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to create issue");
        return executeWithLogging(
                "creating new issue \"" + command.getTitle() + "\" for project " + projectId,
                () -> this.issueApi.createIssue(projectId, command.getTitle(), command.getDescription())
        );
    }

    @DELETE
    @Path("{issueId}")
    @ApiOperation("Delete an issue for the project")
    public void deleteIssue(@PathParam("projectId") String projectId, @PathParam("issueId") String issueId)
    {
        executeWithLogging(
                "deleting issue " + issueId + " for project " + projectId,
                this.issueApi::deleteIssue,
                projectId,
                issueId
        );
    }
}
