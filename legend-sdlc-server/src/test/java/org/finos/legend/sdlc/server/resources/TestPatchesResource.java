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

package org.finos.legend.sdlc.server.resources;

import org.apache.http.client.HttpResponseException;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

public class TestPatchesResource extends AbstractLegendSDLCServerResourceTest
{
    @Test
    public void testGetAllPatches() throws HttpResponseException
    {
        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("A").addVersionedClasses("2.0.0", "a1", "a2", "a3");
        this.backend.project("A").addVersionedClasses("3.0.0", "a1", "a2", "a3", "a4");
        this.backend.project("A").addPatch(VersionId.parseVersionId("1.0.1"));
        this.backend.project("A").addPatch(VersionId.parseVersionId("2.0.1"));
        this.backend.project("A").addPatch(VersionId.parseVersionId("3.0.1"));

        Response response = this.clientFor("/api/projects/A/patches/").request().get();

        if (response.getStatus() != 200)
        {
            throw new HttpResponseException(response.getStatus(), "Error during http call with status: " + response.getStatus() + " , entity: " + response.readEntity(String.class));
        }

        List<Patch> patches = response.readEntity(new GenericType<List<Patch>>()
        {
        });

        Assert.assertEquals(3, patches.size());
        Assert.assertEquals("1.0.1", findPatch(patches, VersionId.parseVersionId("1.0.1")).getPatchReleaseVersionId().toVersionIdString());
        Assert.assertEquals("2.0.1", findPatch(patches, VersionId.parseVersionId("2.0.1")).getPatchReleaseVersionId().toVersionIdString());
        Assert.assertEquals("3.0.1", findPatch(patches, VersionId.parseVersionId("3.0.1")).getPatchReleaseVersionId().toVersionIdString());
    }

    private Patch findPatch(List<Patch> patches, VersionId patchReleaseVersionId)
    {
        return ListIterate.detect(patches, p -> patchReleaseVersionId.equals(p.getPatchReleaseVersionId()));
    }
}
