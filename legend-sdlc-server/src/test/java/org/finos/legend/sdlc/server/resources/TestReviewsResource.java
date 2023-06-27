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

package org.finos.legend.sdlc.server.resources;

import org.apache.http.client.HttpResponseException;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

public class TestReviewsResource extends AbstractLegendSDLCServerResourceTest
{
    @Test
    public void testGetReviewForProject() throws HttpResponseException 
    {
        this.backend.project("A").addReview("1");  

        Response response = this.clientFor("/api/projects/A/reviews/1").request().get();

        if (response.getStatus() != 200)
        {
            throw new HttpResponseException(response.getStatus(), "Error during http call with status: " + response.getStatus() + " , entity: " + response.readEntity(String.class));
        } 
           
        Review review = response.readEntity(new GenericType<Review>()
            {
            });
     
   
        Assert.assertEquals("A", review.getProjectId());
        Assert.assertEquals("111", review.getWorkspaceId());
    }

    @Test
    public void testGetReviewsForProject() throws HttpResponseException 
    {
        this.backend.project("A").addReview("456");  

        Response response = this.clientFor("/api/projects/A/reviews?state=OPEN&limit=2").request().get();

        if (response.getStatus() != 200)
        {
            throw new HttpResponseException(response.getStatus(), "Error during http call with status: " + response.getStatus() + " , entity: " + response.readEntity(String.class));
        } 
        
        List<Review> reviews = response.readEntity(new GenericType<List<Review>>()
            {
            });
    
            
        Assert.assertNotNull(reviews);
        Assert.assertEquals(1, reviews.size());
    }

    @Test
    public void testGetReviewsForPatchReleaseVersion() throws HttpResponseException
    {
        this.backend.project("A").addReview("457", VersionId.parseVersionId("1.0.1"));

        Response response = this.clientFor("/api/projects/A/patches/1.0.1/reviews?limit=2").request().get();

        if (response.getStatus() != 200)
        {
            throw new HttpResponseException(response.getStatus(), "Error during http call with status: " + response.getStatus() + " , entity: " + response.readEntity(String.class));
        }

        List<Review> reviews = response.readEntity(new GenericType<List<Review>>()
        {
        });


        Assert.assertNotNull(reviews);
        Assert.assertEquals(1, reviews.size());
    }
}