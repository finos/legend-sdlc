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

package org.finos.legend.sdlc.server.backend;

import org.finos.legend.sdlc.backend.api.spi.BackendCapability;
import org.finos.legend.sdlc.backend.api.spi.UnsupportedCapabilityException;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.project.workspace.WorkspaceSource;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

/**
 * The undeclared-{@code REVIEWS} compatibility affordance: enumeration reports no reviews; everything else
 * fails with the original capability error (501, carrying the capability).
 */
public class TestNoReviewsReviewApi
{
    @Test
    public void testEnumerationReportsNoReviews()
    {
        NoReviewsReviewApi api = new NoReviewsReviewApi(new UnsupportedCapabilityException(BackendCapability.REVIEWS, "test"));
        Assert.assertEquals(Collections.emptyList(), api.getReviews("project", (ReviewState) null, null, null, (Set<WorkspaceSource>) null, null, null, null));
        Assert.assertEquals(Collections.emptyList(), api.getReviews(false, false, null, null, null, null, null, null));
    }

    @Test
    public void testEverythingElseRethrowsTheCapabilityError()
    {
        UnsupportedCapabilityException cause = new UnsupportedCapabilityException(BackendCapability.REVIEWS, "test");
        NoReviewsReviewApi api = new NoReviewsReviewApi(cause);

        UnsupportedCapabilityException e1 = Assert.assertThrows(UnsupportedCapabilityException.class, () -> api.getReview("project", "1"));
        Assert.assertSame(cause, e1);
        Assert.assertEquals(BackendCapability.REVIEWS, e1.getCapability());
        Assert.assertEquals(UnsupportedCapabilityException.STATUS_CODE, e1.getStatusCode());

        Assert.assertSame(cause, Assert.assertThrows(UnsupportedCapabilityException.class, () -> api.createReview("project", (WorkspaceSpecification) null, "title", "description", null)));
        Assert.assertSame(cause, Assert.assertThrows(UnsupportedCapabilityException.class, () -> api.commitReview("project", "1", "message")));
        Assert.assertSame(cause, Assert.assertThrows(UnsupportedCapabilityException.class, () -> api.getReviewApproval("project", "1")));
    }
}
