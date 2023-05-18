// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.server.resources;

import org.apache.http.client.HttpResponseException;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.application.entity.UpdateEntitiesCommand;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryEntity;
import org.finos.legend.sdlc.tools.entity.EntityPaths;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class TestWorkspaceEntitiesResource extends AbstractLegendSDLCServerResourceTest
{
    @Test
    public void testGetUserWorkspaceEntities() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceOneId = "entityw1";
        String entityOneName = "testentityone";
        String entityTwoName = "testentitytwo";
        String entityPackageName = "testpkg";

        this.backend.project(projectId).addEntities(workspaceOneId, InMemoryEntity.newEntity(entityOneName, entityPackageName), InMemoryEntity.newEntity(entityTwoName, entityPackageName));

        Response responseOne = this.clientFor("/api/projects/A/workspaces/entityw1").request().get();

        if (responseOne.getStatus() != 200)
        {
            throw new HttpResponseException(responseOne.getStatus(), "Error during getting user workspace with status: " + responseOne.getStatus() + ", entity: " + responseOne.readEntity(String.class));
        }

        Workspace workspace = responseOne.readEntity(new GenericType<Workspace>()
        {
        });

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceOneId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        Response responseTwo = this.clientFor("/api/projects/A/workspaces/entityw1/entities").request().get();

        if (responseTwo.getStatus() != 200)
        {
            throw new HttpResponseException(responseTwo.getStatus(), "Error during getting entities in user workspace with status: " + responseTwo.getStatus() + ", entity: " + responseTwo.readEntity(String.class));
        }

        List<Entity> entities = responseTwo.readEntity(new GenericType<List<Entity>>()
        {
        });

        Assert.assertNotNull(entities);
        Assert.assertEquals(2, entities.size());
        Entity entityOne = findEntity(entities, entityOneName, entityPackageName);
        Entity entityTwo = findEntity(entities, entityTwoName, entityPackageName);
        Assert.assertEquals(entityPackageName + EntityPaths.PACKAGE_SEPARATOR + entityOneName, entityOne.getPath());
        Assert.assertEquals(entityPackageName + EntityPaths.PACKAGE_SEPARATOR + entityTwoName, entityTwo.getPath());
    }

    @Test
    public void testGetGroupWorkspaceEntities() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceOneId = "entityw2";
        String entityOneName = "testentityone";
        String entityTwoName = "testentitytwo";
        String entityPackageName = "testpkg";

        this.backend.project(projectId).addEntities(workspaceOneId, WorkspaceType.GROUP, InMemoryEntity.newEntity(entityOneName, entityPackageName), InMemoryEntity.newEntity(entityTwoName, entityPackageName));

        Response responseOne = this.clientFor("/api/projects/A/groupWorkspaces/entityw2").request().get();

        if (responseOne.getStatus() != 200)
        {
            throw new HttpResponseException(responseOne.getStatus(), "Error during getting group workspace with status: " + responseOne.getStatus() + ", entity: " + responseOne.readEntity(String.class));
        }

        Workspace workspace = responseOne.readEntity(new GenericType<Workspace>()
        {
        });

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceOneId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        Response responseTwo = this.clientFor("/api/projects/A/groupWorkspaces/entityw2/entities").request().get();

        if (responseTwo.getStatus() != 200)
        {
            throw new HttpResponseException(responseTwo.getStatus(), "Error during getting entities in group workspace with status: " + responseTwo.getStatus() + ", entity: " + responseTwo.readEntity(String.class));
        }

        List<Entity> entities = responseTwo.readEntity(new GenericType<List<Entity>>()
        {
        });

        Assert.assertNotNull(entities);
        Assert.assertEquals(2, entities.size());
        Entity entityOne = findEntity(entities, entityOneName, entityPackageName);
        Entity entityTwo = findEntity(entities, entityTwoName, entityPackageName);
        Assert.assertEquals(entityPackageName + EntityPaths.PACKAGE_SEPARATOR + entityOneName, entityOne.getPath());
        Assert.assertEquals(entityPackageName + EntityPaths.PACKAGE_SEPARATOR + entityTwoName, entityTwo.getPath());
    }

    @Test
    public void testGetAndUpdateUserWorkspaceEntity() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceOneId = "entityw3";
        String entityOneName = "testentityone";
        String entityPackageName = "testpkg";

        this.backend.project(projectId).addEntities(workspaceOneId, InMemoryEntity.newEntity(entityOneName, entityPackageName));

        Response responseOne = this.clientFor("/api/projects/A/workspaces/entityw3").request().get();

        if (responseOne.getStatus() != 200)
        {
            throw new HttpResponseException(responseOne.getStatus(), "Error during getting user workspace with status: " + responseOne.getStatus() + ", entity: " + responseOne.readEntity(String.class));
        }

        Workspace workspace = responseOne.readEntity(new GenericType<Workspace>()
        {
        });

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceOneId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        Response responseTwo = this.clientFor("/api/projects/A/workspaces/entityw3/entities/testpkg::testentityone").request().get();

        if (responseTwo.getStatus() != 200)
        {
            throw new HttpResponseException(responseTwo.getStatus(), "Error during getting entity in user workspace with status: " + responseTwo.getStatus() + ", entity: " + responseTwo.readEntity(String.class));
        }

        Entity entity = responseTwo.readEntity(new GenericType<Entity>()
        {
        });

        Assert.assertNotNull(entity);
        Assert.assertEquals(entityPackageName + EntityPaths.PACKAGE_SEPARATOR + entityOneName, entity.getPath());
        Assert.assertEquals(entityPackageName, entity.getContent().get("package"));
        Assert.assertEquals(entityOneName, entity.getContent().get("name"));

        UpdateEntitiesCommand updateEntitiesCommand = new UpdateEntitiesCommand();
        updateEntitiesCommand.setReplace(true);

        Response responseThree = this.clientFor("/api/projects/A/workspaces/entityw3/entities").request().post(javax.ws.rs.client.Entity.entity(updateEntitiesCommand, MediaType.APPLICATION_JSON));

        if (responseThree.getStatus() != 200)
        {
            throw new HttpResponseException(responseThree.getStatus(), "Error during updating entity in user workspace with status: " + responseThree.getStatus() + ", entity: " + responseThree.readEntity(String.class));
        }
        Revision revision = responseThree.readEntity(new GenericType<Revision>()
        {
        });

        Assert.assertNotNull(revision);
        Assert.assertNotNull(revision.getId());
    }

    @Test
    public void testGetAndUpdateGroupWorkspaceEntity() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceOneId = "entityw4";
        String entityOneName = "testentityone";
        String entityPackageName = "testpkg";

        this.backend.project(projectId).addEntities(workspaceOneId, WorkspaceType.GROUP, InMemoryEntity.newEntity(entityOneName, entityPackageName));

        Response responseOne = this.clientFor("/api/projects/A/groupWorkspaces/entityw4").request().get();

        if (responseOne.getStatus() != 200)
        {
            throw new HttpResponseException(responseOne.getStatus(), "Error during getting group workspace with status: " + responseOne.getStatus() + ", entity: " + responseOne.readEntity(String.class));
        }

        Workspace workspace = responseOne.readEntity(new GenericType<Workspace>()
        {
        });

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceOneId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        Response responseTwo = this.clientFor("/api/projects/A/groupWorkspaces/entityw4/entities/testpkg::testentityone").request().get();

        if (responseTwo.getStatus() != 200)
        {
            throw new HttpResponseException(responseTwo.getStatus(), "Error during getting entity in group workspace with status: " + responseTwo.getStatus() + ", entity: " + responseTwo.readEntity(String.class));
        }

        Entity entity = responseTwo.readEntity(new GenericType<Entity>()
        {
        });

        Assert.assertNotNull(entity);
        Assert.assertEquals(entityPackageName + EntityPaths.PACKAGE_SEPARATOR + entityOneName, entity.getPath());
        Assert.assertEquals(entityPackageName, entity.getContent().get("package"));
        Assert.assertEquals(entityOneName, entity.getContent().get("name"));

        UpdateEntitiesCommand updateEntitiesCommand = new UpdateEntitiesCommand();
        updateEntitiesCommand.setReplace(true);

        Response responseThree = this.clientFor("/api/projects/A/groupWorkspaces/entityw4/entities").request().post(javax.ws.rs.client.Entity.entity(updateEntitiesCommand, MediaType.APPLICATION_JSON));

        if (responseThree.getStatus() != 200)
        {
            throw new HttpResponseException(responseThree.getStatus(), "Error during updating entity in group workspace with status: " + responseThree.getStatus() + ", entity: " + responseThree.readEntity(String.class));
        }

        Revision revision = responseThree.readEntity(new GenericType<Revision>()
        {
        });

        Assert.assertNotNull(revision);
        Assert.assertNotNull(revision.getId());
    }

    @Test
    public void testGetUserWorkspaceEntitiesForPatchReleaseVersion() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceOneId = "entityw1";
        String entityOneName = "testentityone";
        String entityTwoName = "testentitytwo";
        String entityPackageName = "testpkg";
        VersionId patchReleaseVersionId = VersionId.parseVersionId("1.0.1");

        this.backend.project(projectId).addVersionedEntities("1.0.0", Arrays.asList(InMemoryEntity.newEntity(entityOneName, entityPackageName), InMemoryEntity.newEntity(entityTwoName, entityPackageName)));
        this.backend.project(projectId).addEntities(workspaceOneId, Arrays.asList(InMemoryEntity.newEntity(entityOneName, entityPackageName), InMemoryEntity.newEntity(entityTwoName, entityPackageName)), patchReleaseVersionId);

        Response responseOne = this.clientFor("/api/projects/A/patches/1.0.1/workspaces/entityw1").request().get();

        if (responseOne.getStatus() != 200)
        {
            throw new HttpResponseException(responseOne.getStatus(), "Error during getting user workspace with status: " + responseOne.getStatus() + ", entity: " + responseOne.readEntity(String.class));
        }

        Workspace workspace = responseOne.readEntity(new GenericType<Workspace>()
        {
        });

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceOneId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        Response responseTwo = this.clientFor("/api/projects/A/patches/1.0.1/workspaces/entityw1/entities").request().get();

        if (responseTwo.getStatus() != 200)
        {
            throw new HttpResponseException(responseTwo.getStatus(), "Error during getting entities in user workspace with status: " + responseTwo.getStatus() + ", entity: " + responseTwo.readEntity(String.class));
        }

        List<Entity> entities = responseTwo.readEntity(new GenericType<List<Entity>>()
        {
        });

        Assert.assertNotNull(entities);
        Assert.assertEquals(2, entities.size());
        Entity entityOne = findEntity(entities, entityOneName, entityPackageName);
        Entity entityTwo = findEntity(entities, entityTwoName, entityPackageName);
        Assert.assertEquals(entityPackageName + EntityPaths.PACKAGE_SEPARATOR + entityOneName, entityOne.getPath());
        Assert.assertEquals(entityPackageName + EntityPaths.PACKAGE_SEPARATOR + entityTwoName, entityTwo.getPath());
    }

    @Test
    public void testGetGroupWorkspaceEntitiesForPatchReleaseVersion() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceOneId = "entityw2";
        String entityOneName = "testentityone";
        String entityTwoName = "testentitytwo";
        String entityPackageName = "testpkg";
        VersionId patchReleaseVersionId = VersionId.parseVersionId("1.0.1");

        this.backend.project(projectId).addVersionedEntities("1.0.0", InMemoryEntity.newEntity(entityOneName, entityPackageName), InMemoryEntity.newEntity(entityTwoName, entityPackageName));
        this.backend.project(projectId).addEntities(workspaceOneId, WorkspaceType.GROUP, Arrays.asList(InMemoryEntity.newEntity(entityOneName, entityPackageName), InMemoryEntity.newEntity(entityTwoName, entityPackageName)), patchReleaseVersionId);

        Response responseOne = this.clientFor("/api/projects/A/patches/1.0.1/groupWorkspaces/entityw2").request().get();

        if (responseOne.getStatus() != 200)
        {
            throw new HttpResponseException(responseOne.getStatus(), "Error during getting group workspace with status: " + responseOne.getStatus() + ", entity: " + responseOne.readEntity(String.class));
        }

        Workspace workspace = responseOne.readEntity(new GenericType<Workspace>()
        {
        });

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceOneId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        Response responseTwo = this.clientFor("/api/projects/A/patches/1.0.1/groupWorkspaces/entityw2/entities").request().get();

        if (responseTwo.getStatus() != 200)
        {
            throw new HttpResponseException(responseTwo.getStatus(), "Error during getting entities in group workspace with status: " + responseTwo.getStatus() + ", entity: " + responseTwo.readEntity(String.class));
        }

        List<Entity> entities = responseTwo.readEntity(new GenericType<List<Entity>>()
        {
        });

        Assert.assertNotNull(entities);
        Assert.assertEquals(2, entities.size());
        Entity entityOne = findEntity(entities, entityOneName, entityPackageName);
        Entity entityTwo = findEntity(entities, entityTwoName, entityPackageName);
        Assert.assertEquals(entityPackageName + EntityPaths.PACKAGE_SEPARATOR + entityOneName, entityOne.getPath());
        Assert.assertEquals(entityPackageName + EntityPaths.PACKAGE_SEPARATOR + entityTwoName, entityTwo.getPath());
    }

    @Test
    public void testGetAndUpdateUserWorkspaceEntityForPatchReleaseVersion() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceOneId = "entityw3";
        String entityOneName = "testentityone";
        String entityPackageName = "testpkg";
        VersionId patchReleaseVersionId = VersionId.parseVersionId("1.0.1");

        this.backend.project(projectId).addVersionedEntities("1.0.0", InMemoryEntity.newEntity(entityOneName, entityPackageName));
        this.backend.project(projectId).addEntities(workspaceOneId, Arrays.asList(InMemoryEntity.newEntity(entityOneName, entityPackageName)), patchReleaseVersionId);

        Response responseOne = this.clientFor("/api/projects/A/patches/1.0.1/workspaces/entityw3").request().get();

        if (responseOne.getStatus() != 200)
        {
            throw new HttpResponseException(responseOne.getStatus(), "Error during getting user workspace with status: " + responseOne.getStatus() + ", entity: " + responseOne.readEntity(String.class));
        }

        Workspace workspace = responseOne.readEntity(new GenericType<Workspace>()
        {
        });

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceOneId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        Response responseTwo = this.clientFor("/api/projects/A/patches/1.0.1/workspaces/entityw3/entities/testpkg::testentityone").request().get();

        if (responseTwo.getStatus() != 200)
        {
            throw new HttpResponseException(responseTwo.getStatus(), "Error during getting entity in user workspace with status: " + responseTwo.getStatus() + ", entity: " + responseTwo.readEntity(String.class));
        }

        Entity entity = responseTwo.readEntity(new GenericType<Entity>()
        {
        });

        Assert.assertNotNull(entity);
        Assert.assertEquals(entityPackageName + EntityPaths.PACKAGE_SEPARATOR + entityOneName, entity.getPath());
        Assert.assertEquals(entityPackageName, entity.getContent().get("package"));
        Assert.assertEquals(entityOneName, entity.getContent().get("name"));

        UpdateEntitiesCommand updateEntitiesCommand = new UpdateEntitiesCommand();
        updateEntitiesCommand.setReplace(true);

        Response responseThree = this.clientFor("/api/projects/A/patches/1.0.1/workspaces/entityw3/entities").request().post(javax.ws.rs.client.Entity.entity(updateEntitiesCommand, MediaType.APPLICATION_JSON));

        if (responseThree.getStatus() != 200)
        {
            throw new HttpResponseException(responseThree.getStatus(), "Error during updating entity in user workspace with status: " + responseThree.getStatus() + ", entity: " + responseThree.readEntity(String.class));
        }
        Revision revision = responseThree.readEntity(new GenericType<Revision>()
        {
        });

        Assert.assertNotNull(revision);
        Assert.assertNotNull(revision.getId());
    }

    @Test
    public void testGetAndUpdateGroupWorkspaceEntityForPatchReleaseVersion() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceOneId = "entityw4";
        String entityOneName = "testentityone";
        String entityPackageName = "testpkg";
        VersionId patchReleaseVersionId = VersionId.parseVersionId("1.0.1");

        this.backend.project(projectId).addVersionedEntities("1.0.0", InMemoryEntity.newEntity(entityOneName, entityPackageName));
        this.backend.project(projectId).addEntities(workspaceOneId, WorkspaceType.GROUP, Arrays.asList(InMemoryEntity.newEntity(entityOneName, entityPackageName)), patchReleaseVersionId);

        Response responseOne = this.clientFor("/api/projects/A/patches/1.0.1/groupWorkspaces/entityw4").request().get();

        if (responseOne.getStatus() != 200)
        {
            throw new HttpResponseException(responseOne.getStatus(), "Error during getting group workspace with status: " + responseOne.getStatus() + ", entity: " + responseOne.readEntity(String.class));
        }

        Workspace workspace = responseOne.readEntity(new GenericType<Workspace>()
        {
        });

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceOneId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        Response responseTwo = this.clientFor("/api/projects/A/patches/1.0.1/groupWorkspaces/entityw4/entities/testpkg::testentityone").request().get();

        if (responseTwo.getStatus() != 200)
        {
            throw new HttpResponseException(responseTwo.getStatus(), "Error during getting entity in group workspace with status: " + responseTwo.getStatus() + ", entity: " + responseTwo.readEntity(String.class));
        }

        Entity entity = responseTwo.readEntity(new GenericType<Entity>()
        {
        });

        Assert.assertNotNull(entity);
        Assert.assertEquals(entityPackageName + EntityPaths.PACKAGE_SEPARATOR + entityOneName, entity.getPath());
        Assert.assertEquals(entityPackageName, entity.getContent().get("package"));
        Assert.assertEquals(entityOneName, entity.getContent().get("name"));

        UpdateEntitiesCommand updateEntitiesCommand = new UpdateEntitiesCommand();
        updateEntitiesCommand.setReplace(true);

        Response responseThree = this.clientFor("/api/projects/A/patches/1.0.1/groupWorkspaces/entityw4/entities").request().post(javax.ws.rs.client.Entity.entity(updateEntitiesCommand, MediaType.APPLICATION_JSON));

        if (responseThree.getStatus() != 200)
        {
            throw new HttpResponseException(responseThree.getStatus(), "Error during updating entity in group workspace with status: " + responseThree.getStatus() + ", entity: " + responseThree.readEntity(String.class));
        }

        Revision revision = responseThree.readEntity(new GenericType<Revision>()
        {
        });

        Assert.assertNotNull(revision);
        Assert.assertNotNull(revision.getId());
    }

    private Entity findEntity(List<Entity> entities, String entityName, String entityPackageName)
    {
        return entities.stream().filter(entity -> entity.getContent().get("name").equals(entityName) && entity.getContent().get("package").equals(entityPackageName)).findFirst().get();
    }
}
