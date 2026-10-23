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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.issue.Issue;
import org.finos.legend.sdlc.server.application.issue.CreateIssueCommand;
import org.finos.legend.sdlc.backend.api.issue.IssueApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.BaseResource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/projects/{projectId}/issues")
@Tag(name = "Issues")
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
    @Operation(summary = "Get all issues for the project")
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
    @Operation(summary = "Get an issue for the project")
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
    @Operation(summary = "Create a new issue for the project")
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
    @Operation(summary = "Delete an issue for the project")
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
