package org.finos.legend.sdlc.server.resources;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/projects/{projectId}/groupWorkspaces/{workspaceId}/revisions/{revisionId}/pureModelContextData")
@Api("Pure Model Context")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GroupWorkspaceRevisionPureModelContextDataResource extends PureModelContextDataResource
{
    private final EntityApi entityApi;

    @Inject
    protected GroupWorkspaceRevisionPureModelContextDataResource(EntityApi entityApi)
    {
        this.entityApi = entityApi;
    }

    @GET
    @ApiOperation("Get Pure model context data for a group workspace at a revision")
    public PureModelContextData getPureModelContextData(@PathParam("projectId") String projectId,
                                                        @PathParam("workspaceId") String workspaceId,
                                                        @PathParam("revisionId") @ApiParam("Including aliases: head, latest, current, base") String revisionId)
    {
        return executeWithLogging(
                "getting Pure model context data for group workspace " + workspaceId + " in project " + projectId + " at revision " + revisionId,
                () -> getPureModelContextData(projectId, revisionId, this.entityApi.getGroupWorkspaceRevisionEntityAccessContext(projectId, workspaceId, revisionId))
        );
    }
}
